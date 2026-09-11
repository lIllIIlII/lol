package com.yunx.app.data.network

import android.util.Base64
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.CRC32

class Pan123Api(
    private val clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    private val client get() = clientProvider()

    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    private val loginuuid: String = Pan123Constants.newLoginUuid()

    private fun crc32Hex(s: String): String {
        val crc = CRC32()
        crc.update(s.toByteArray(Charsets.UTF_8))
        return java.lang.Long.toHexString(crc.value and 0xFFFFFFFFL)
    }

    fun makeSign(path: String, ts: Long = System.currentTimeMillis() / 1000): Pair<String, String> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = (ts + Pan123Constants.SIGN_OFFSET_SECONDS) * 1000L
        }
        val minute = String.format(
            "%04d%02d%02d%02d%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
        val substituted = minute.map { Pan123Constants.SIGN_TABLE[it - '0'] }.joinToString("")
        val authKey = crc32Hex(substituted)

        val random = ThreadLocalRandom.current().nextInt(0, 10_000_000)
        val data = "$ts|$random|$path|${Pan123Constants.SIGN_OS}|${Pan123Constants.SIGN_VER}|$authKey"
        val authValue = "$ts-$random-${crc32Hex(data)}"
        return authKey to authValue
    }

    suspend fun fetchNickname(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val json = getAuth(Pan123Constants.USER_INFO_URL, "/b/api/user/info", token)
            checkOk(json, "获取用户信息失败")
            json.optJSONObject("data")?.optString("Nickname")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun getQuota(token: String): QuotaInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val json = getAuth(Pan123Constants.USER_INFO_URL, "/b/api/user/info", token)
            checkOk(json, "获取空间详情失败")
            val data = json.optJSONObject("data") ?: return@runCatching null
            QuotaInfo(
                used = data.optLong("SpaceUsed"),
                total = data.optLong("SpacePermanent") + data.optLong("SpaceTemp")
            )
        }.getOrNull()
    }

    suspend fun getShareFiles(
        shareKey: String,
        sharePwd: String,
        parentFileId: String,
        next: String,
        page: Int
    ): Pair<List<ShareFile>, String?> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(Pan123Constants.SHARE_GET_URL)
            append("?limit=100")
            append("&next=").append(next)
            append("&orderBy=file_name")
            append("&orderDirection=asc")
            append("&shareKey=").append(URLEncoder.encode(shareKey, "UTF-8"))
            append("&ParentFileId=").append(parentFileId)
            append("&Page=").append(page)
            if (sharePwd.isNotBlank()) {
                append("&SharePwd=").append(URLEncoder.encode(sharePwd, "UTF-8"))
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Pan123Constants.DART_UA)
            .get()
            .build()
        val json = executeJson(request)
        checkOk(json, "获取文件列表失败")
        val data = json.optJSONObject("data") ?: return@withContext Pair(emptyList(), null)
        if (data.optBoolean("Expired", false)) {
            throw IllegalStateException("分享已失效")
        }
        val files = parseInfoList(data)
        val nextCursor = data.optString("Next").takeIf { it != "-1" }
        Pair(files, nextCursor)
    }

    suspend fun getShareDownloadLink(
        shareKey: String,
        file: ShareFile,
        token: String
    ): DownloadLink? = withContext(Dispatchers.IO) {
        val (s3KeyFlag, etag, _) = decodeToken(file.fidToken)
        val body = JSONObject()
            .put("ShareKey", shareKey)
            .put("FileID", file.fid)
            .put("S3KeyFlag", s3KeyFlag)
            .put("Size", file.fsize)
            .put("Etag", etag)
        val json = postAuth(
            Pan123Constants.SHARE_DOWNLOAD_INFO_URL,
            "/b/api/share/download/info",
            body.toString(),
            token,
            platform = Pan123Constants.PLATFORM_ANDROID,
            appVersion = Pan123Constants.APP_VERSION_ANDROID
        )
        checkOk(json, "获取下载链接失败")
        val data = json.optJSONObject("data") ?: return@withContext null
        val downloadUrl = data.optString("DownloadURL")
        if (downloadUrl.isBlank()) return@withContext null
        val decoded = decodeDownloadUrl(downloadUrl) ?: downloadUrl
        val realUrl = followRedirectUrl(decoded)
        DownloadLink(
            fid = file.fid,
            filename = file.fname,
            downloadUrl = realUrl,
            size = file.fsize
        )
    }

    private suspend fun fetchCloudPage(
        parentFileId: String,
        token: String,
        next: String
    ): Pair<List<ShareFile>, String?>? = withContext(Dispatchers.IO) {
        val url = buildString {
            append(Pan123Constants.FILE_LIST_URL)
            append("?driveId=0&limit=100&next=").append(next)
            append("&orderBy=update_time&orderDirection=desc")
            append("&parentFileId=").append(parentFileId)
            append("&trashed=false&SearchData=&Page=1&OnlyLookAbnormalFile=0")
            append("&event=homeListFile&operateType=1&inDirectSpace=false")
        }
        val json = getAuth(url, "/b/api/file/list/new", token)
        checkOk(json, "获取文件列表失败")
        val data = json.optJSONObject("data") ?: return@withContext null
        val files = parseInfoList(data)
        val nextCursor = data.optString("Next").takeIf { it != "-1" }
        Pair(files, nextCursor)
    }

    suspend fun listCloudFiles(parentFileId: String, token: String): List<ShareFile> {
        val all = mutableListOf<ShareFile>()
        var next = "0"
        repeat(200) {
            val (files, cursor) = fetchCloudPage(parentFileId, token, next) ?: return all
            all += files
            next = cursor ?: return all
        }
        return all
    }

    suspend fun getDownloadLink(file: ShareFile, token: String): DownloadLink? = withContext(Dispatchers.IO) {
        val (s3keyFlag, etag, _) = decodeToken(file.fidToken)
        val body = JSONObject()
            .put("driveId", 0)
            .put("etag", etag)
            .put("fileId", file.fid.toLongOrNull() ?: 0L)
            .put("s3keyFlag", s3keyFlag)
            .put("type", 0)
            .put("fileName", file.fname)
            .put("size", file.fsize)
        val json = postAuth(
            Pan123Constants.FILE_DOWNLOAD_INFO_URL,
            "/api/file/download_info",
            body.toString(),
            token
        )
        checkOk(json, "获取下载链接失败")
        val data = json.optJSONObject("data") ?: return@withContext null
        val raw = data.optString("DownloadUrl")
        if (raw.isBlank()) return@withContext null
        val decoded = decodeDownloadUrl(raw) ?: raw
        val url = followRedirectUrl(decoded)
        DownloadLink(
            fid = file.fid,
            filename = file.fname,
            downloadUrl = url,
            size = file.fsize
        )
    }

    suspend fun copySave(
        shareKey: String,
        sharePwd: String,
        file: ShareFile,
        toDirFid: String,
        token: String
    ): Pair<Long, String>? = withContext(Dispatchers.IO) {
        val shareId = shareIdOf(file)
        if (shareId.isBlank()) throw IllegalStateException("无法识别分享 ID（缺少 S3KeyFlag）")
        val (s3KeyFlag, etag, storageNode) = decodeToken(file.fidToken)
        val fileId = file.fid.toLongOrNull() ?: 0L
        val parentId = toDirFid.toLongOrNull() ?: 0L
        val body = JSONObject()
            .put(
                "fileList",
                JSONArray().put(
                    JSONObject()
                        .put("fileID", fileId)
                        .put("fileId", fileId)
                        .put("size", file.fsize)
                        .put("etag", etag)
                        .put("type", if (file.isdir) 1 else 0)
                        .put("parentFileID", parentId)
                        .put("parentFileId", parentId)
                        .put("fileName", file.fname)
                        .put("driveID", 0)
                        .put("driveId", 0)
                        .put("s3keyFlag", s3KeyFlag)
                        .put("S3KeyFlag", s3KeyFlag)
                        .put("StorageNode", storageNode)
                )
            )
            .put("shareKey", shareKey)
            .put("sharePwd", sharePwd.ifBlank { "" })
            .put("currentLevel", 1)
            .put("superAdmin", JSONObject.NULL)
        val request = Request.Builder()
            .url("https://$shareId.mshare.123pan.cn/b/api/restful/goapi/v1/file/copy/save")
            .header("Authorization", "Bearer $token")
            .header("LoginUuid", loginuuid)
            .header("platform", Pan123Constants.PLATFORM_WEB)
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("User-Agent", Pan123Constants.DART_UA)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        val json = executeJson(request)
        checkOk(json, "转存失败")
        val taskId = json.optJSONObject("data")?.optLong("taskID") ?: return@withContext null
        taskId to shareId
    }

    suspend fun pollCopySave(taskId: Long, shareId: String, token: String): String? = withContext(Dispatchers.IO) {
        repeat(15) {
            kotlinx.coroutines.delay(1000)
            val url =
                "https://$shareId.mshare.123pan.cn/b/api/restful/goapi/v1/file/copy/save/get?taskID=$taskId"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("LoginUuid", loginuuid)
                .header("platform", Pan123Constants.PLATFORM_WEB)
                .header("User-Agent", Pan123Constants.DART_UA)
                .get()
                .build()
            val json = executeJson(request)
            if (json.optInt("code", -1) != 0) {
                val msg = json.optString("message")
                if (msg.isNotBlank()) throw IllegalStateException("转存失败：$msg")
                return@repeat
            }
            val data = json.optJSONObject("data") ?: return@repeat
            val status = data.optInt("status", -1)
            val state = data.optString("state").lowercase()
            val done = data.optBoolean("finished", false) ||
                status == 2 || status == 3 ||
                state == "success" || state == "done" || state == "2" ||
                data.has("fileId") || data.has("FileId") || data.has("newFileId")
            if (done) {
                return@withContext data.optString("newFileId")
                    .ifBlank { data.optString("FileId") }
                    .ifBlank { data.optString("fileId") }
                    .ifBlank { taskId.toString() }
            }
        }
        null
    }

    private fun shareIdOf(file: ShareFile): String {
        val s3 = file.fidToken.substringBefore('|')
        return s3.substringBefore('-')
    }

    suspend fun deleteFiles(files: List<ShareFile>, token: String) = withContext(Dispatchers.IO) {
        val list = JSONArray()
        files.forEach { f ->
            val (s3, etag, _) = decodeToken(f.fidToken)
            list.put(
                JSONObject()
                    .put("FileId", f.fid.toLongOrNull() ?: 0L)
                    .put("FileName", f.fname)
                    .put("Type", if (f.isdir) 1 else 0)
                    .put("Size", f.fsize)
                    .put("S3KeyFlag", s3)
                    .put("Etag", etag)
            )
        }
        val body = JSONObject()
            .put("driveId", 0)
            .put("fileTrashInfoList", list)
            .put("operation", true)
            .put("event", "intoRecycle")
            .put("operatePlace", 1)
            .put("safeBox", false)
        val json = postAuth(Pan123Constants.FILE_TRASH_URL, "/b/api/file/trash", body.toString(), token)
        checkOk(json, "删除失败")
    }

    suspend fun renameFile(fileId: String, newName: String, token: String) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("driveId", 0)
            .put("fileId", fileId.toLongOrNull() ?: 0L)
            .put("fileName", newName)
            .put("duplicate", 1)
            .put("event", "fileRename")
            .put("operatePlace", "right")
            .put("RequestSource", JSONObject.NULL)
        val json = postAuth(Pan123Constants.FILE_RENAME_URL, "/b/api/file/rename", body.toString(), token)
        checkOk(json, "重命名失败")
    }

    suspend fun moveFiles(fileIds: List<String>, toParentFileId: String, token: String) = withContext(Dispatchers.IO) {
        val list = JSONArray()
        fileIds.forEach { list.put(JSONObject().put("FileId", it.toLongOrNull() ?: 0L)) }
        val body = JSONObject()
            .put("fileIdList", list)
            .put("parentFileId", toParentFileId.toLongOrNull() ?: 0L)
            .put("event", "fileMove")
            .put("operatePlace", 1)
            .put("RequestSource", JSONObject.NULL)
        val json = postAuth(Pan123Constants.FILE_MOD_PID_URL, "/b/api/file/mod_pid", body.toString(), token)
        checkOk(json, "移动失败")
    }

    suspend fun createShare(
        fileIds: List<String>,
        shareName: String,
        expiration: String,
        sharePwd: String?,
        token: String
    ): ShareInfo = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("driveId", 0)
            .put("expiration", expiration)
            .apply {
                if (fileIds.size == 1) {
                    put("fileIdList", fileIds[0].toLongOrNull() ?: 0L)
                } else {
                    put("fileIdList", JSONArray().apply { fileIds.forEach { put(it.toLongOrNull() ?: 0L) } })
                }
            }
            .put("shareName", shareName)
            .put("event", "shareCreate")
            .put("fileNum", fileIds.size)
            .put("shareModality", 4)
            .put("trafficLimitSwitch", 1)
            .put("trafficLimit", 0)
            .put("trafficSwitch", 1)
            .put("fillPwdSwitch", 0)
            .apply { if (!sharePwd.isNullOrBlank()) put("sharePwd", sharePwd) }
        val json = postAuth(Pan123Constants.SHARE_CREATE_URL, "/b/api/share/create", body.toString(), token)
        checkOk(json, "创建分享失败")
        val data = json.optJSONObject("data")
            ?: throw IllegalStateException("创建分享失败：未返回数据")
        val shareKey = data.optString("ShareKey")
        if (shareKey.isBlank()) throw IllegalStateException("创建分享失败：未返回 ShareKey")
        val linkList = data.optJSONObject("shareLinkList")
        val shareUrl = linkList?.optJSONArray("list")?.optString(0)
            ?.takeIf { it.isNotBlank() }
            ?: linkList?.optString("standBy")
                ?.takeIf { it.isNotBlank() }
                ?: "https://www.123pan.com/s/$shareKey"
        ShareInfo(
            shareUrl = shareUrl,
            passcode = sharePwd.orEmpty(),
            pwdId = shareKey,
            title = shareName,
            expiredType = if (expiration == Pan123Constants.EXPIRATION_FOREVER) 1 else 4
        )
    }

    private fun parseInfoList(data: JSONObject): List<ShareFile> {
        val arr = data.optJSONArray("InfoList") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val type = item.optInt("Type", 0)
                add(
                    ShareFile(
                        fid = item.optString("FileId"),
                        fname = item.optString("FileName"),
                        fsize = item.optLong("Size"),
                        isdir = type == 1,
                        pdirFid = item.optString("ParentFileId"),
                        fidToken = "${item.optString("S3KeyFlag")}|${item.optString("Etag")}|${item.optString("StorageNode")}",
                        modifyTime = item.optString("UpdateAt")
                    )
                )
            }
        }
    }

    private fun decodeToken(fidToken: String): Triple<String, String, String> {
        val parts = fidToken.split('|')
        return Triple(
            parts.getOrNull(0) ?: "",
            parts.getOrNull(1) ?: "",
            parts.getOrNull(2) ?: ""
        )
    }

    private fun decodeDownloadUrl(downloadUrl: String): String? {
        val trimmed = downloadUrl.trim()
        if (!trimmed.contains("://")) {
            return runCatching {
                String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8)
                    .takeIf { it.startsWith("http", ignoreCase = true) }
            }.getOrNull()
        }
        val idx = trimmed.indexOf("params=")
        if (idx < 0) return null
        val params = trimmed.substring(idx + "params=".length).substringBefore("&")
        return runCatching {
            val normalized = params.replace('-', '+').replace('_', '/')
            String(Base64.decode(normalized, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun followRedirectUrl(initialUrl: String): String {
        var url = initialUrl
        repeat(5) {
            val next = probeJsonRedirect(url) ?: return url
            url = next
        }
        return url
    }

    private fun probeJsonRedirect(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Referer", Pan123Constants.DOWNLOAD_REFERER)
            .header("User-Agent", Pan123Constants.DART_UA)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
            if (len >= 0 && len <= 8192) {
                val body = response.body?.string() ?: return@use null
                if (body.trimStart().startsWith("{")) {
                    runCatching {
                        JSONObject(body).optJSONObject("data")
                            ?.optString("redirect_url")
                            ?.takeIf { it.isNotBlank() }
                    }.getOrNull()
                } else null
            } else null
        }
    }.getOrNull()

    private fun checkOk(json: JSONObject, fallback: String) {
        val code = json.optInt("code", -1)
        if (code == 0) return
        val msg = json.optString("message").ifBlank { fallback }
        throw IllegalStateException("$msg（code=$code）")
    }

    private fun getAuth(url: String, path: String, token: String): JSONObject {
        val (ak, av) = makeSign(path)
        val request = Request.Builder()
            .url(url)
            .header("platform", Pan123Constants.PLATFORM_WEB)
            .header("app-version", Pan123Constants.APP_VERSION_WEB)
            .header("authorization", "Bearer $token")
            .header("loginuuid", loginuuid)
            .header("auth-key", ak)
            .header("auth-value", av)
            .header("User-Agent", Pan123Constants.WEB_UA)
            .header("Accept", "application/json, text/plain, */*")
            .get()
            .build()
        return executeJson(request)
    }

    private fun postAuth(
        url: String,
        path: String,
        body: String,
        token: String,
        platform: String = Pan123Constants.PLATFORM_WEB,
        appVersion: String = Pan123Constants.APP_VERSION_WEB
    ): JSONObject {
        val (ak, av) = makeSign(path)
        val request = Request.Builder()
            .url(url)
            .header("platform", platform)
            .header("app-version", appVersion)
            .header("authorization", "Bearer $token")
            .header("loginuuid", loginuuid)
            .header("auth-key", ak)
            .header("auth-value", av)
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("User-Agent", Pan123Constants.WEB_UA)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IllegalStateException("请求失败：响应为空（${response.code}）")
            if (!response.isSuccessful && body.isBlank()) {
                throw IllegalStateException("请求失败（HTTP ${response.code}）")
            }
            return JSONObject(body)
        }
    }
}
