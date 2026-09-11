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
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

class C139Api(
    private val clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    private val client get() = clientProvider()

    private val jsonMediaType = "application/json;charset=UTF-8".toMediaType()

    private val shareAesKey: SecretKeySpec =
        SecretKeySpec(C139Constants.SHARE_AES_KEY.toByteArray(Charsets.UTF_8), "AES")

    private fun md5(s: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun encodeURIComponent(s: String): String =
        URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%2A", "*")

    fun calSign(bodyJson: String, ts: String, rand: String): String {
        val encoded = encodeURIComponent(bodyJson)
        val sorted = encoded.toCharArray().sorted().joinToString("")
        val b64 = Base64.encodeToString(sorted.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val res = md5(b64) + md5("$ts:$rand")
        return md5(res).uppercase()
    }

    fun signHeader(bodyJson: String): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val rand = buildString {
            val pool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            repeat(16) { append(pool.random()) }
        }
        return "$ts,$rand,${calSign(bodyJson, ts, rand)}"
    }

    fun accountFromAuthorization(authorization: String): String? = runCatching {
        val b64 = authorization.removePrefix("Basic").trim()
        val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
        decoded.split(":").getOrNull(1)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun encryptBody(plaintext: String): String {
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, shareAesKey, IvParameterSpec(iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun decryptBody(b64: String): String {
        val raw = Base64.decode(b64, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 16)
        val ct = raw.copyOfRange(16, raw.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, shareAesKey, IvParameterSpec(iv))
        var d = cipher.doFinal(ct)
        if (d.size > 2 && d[0] == 0x1f.toByte() && d[1] == 0x8b.toByte()) {
            d = GZIPInputStream(ByteArrayInputStream(d)).use { it.readBytes() }
        }
        return String(d, Charsets.UTF_8)
    }

    suspend fun getOutLinkTitle(linkId: String): String? = withContext(Dispatchers.IO) {
        val req = JSONObject()
            .put("linkID", linkId)
            .put("isPasswd", 1)
            .put("account", "")
        val plain = JSONObject().put("getOutLinkGeneralReq", req).toString()
        val respJson = sharePostAnonymous(C139Constants.SHARE_GENERAL_URL, plain)
        val resultCode = respJson.optString("resultCode")
        if (resultCode.isNotBlank() && resultCode != "0") return@withContext null
        if (!respJson.optBoolean("success", true)) return@withContext null
        val data = respJson.optJSONObject("data")
            ?.optJSONObject("getOutLinkGeneralResp") ?: return@withContext null
        val array = data.optJSONArray("outLinkGeneral") ?: return@withContext null
        if (array.length() == 0) return@withContext null
        array.optJSONObject(0)?.optString("lkName")?.takeIf { it.isNotBlank() }
    }

    suspend fun getOutLinkPassword(linkId: String): String? = withContext(Dispatchers.IO) {
        val req = JSONObject()
            .put("linkID", linkId)
            .put("isPasswd", 1)
            .put("account", "")
        val plain = JSONObject().put("getOutLinkGeneralReq", req).toString()
        val respJson = sharePostAnonymous(C139Constants.SHARE_GENERAL_URL, plain)
        val resultCode = respJson.optString("resultCode")
        if (resultCode.isNotBlank() && resultCode != "0") return@withContext null
        if (!respJson.optBoolean("success", true)) return@withContext null
        val data = respJson.optJSONObject("data")
            ?.optJSONObject("getOutLinkGeneralResp") ?: return@withContext null
        val array = data.optJSONArray("outLinkGeneral") ?: return@withContext null
        if (array.length() == 0) return@withContext null
        array.optJSONObject(0)?.optString("passwd")?.takeIf { it.isNotBlank() }
    }

    suspend fun getShareFiles(
        linkId: String,
        pcaId: String,
        passwd: String,
        begin: Int = 1,
        end: Int = 200
    ): List<ShareFile> = withContext(Dispatchers.IO) {
        val req = JSONObject()
            .put("account", "")
            .put("linkID", linkId)
            .put("passwd", passwd)
            .put("caSrt", 1)
            .put("coSrt", 1)
            .put("srtDr", 0)
            .put("bNum", begin)
            .put("pCaID", pcaId)
            .put("eNum", end)
        val plain = JSONObject().put("getOutLinkInfoReq", req).toString()
        val respJson = sharePostAnonymous(C139Constants.SHARE_LIST_URL, plain)
        val resultCode = respJson.optString("resultCode")
        if (resultCode.isNotBlank() && resultCode != "0") {
            throw IllegalStateException(respJson.optString("desc").ifBlank { "获取文件列表失败（$resultCode）" })
        }
        if (!respJson.optBoolean("success", true)) {
            throw IllegalStateException(respJson.optString("desc").ifBlank { "获取文件列表失败" })
        }
        val data = respJson.optJSONObject("data") ?: return@withContext emptyList()
        val result = mutableListOf<ShareFile>()

        data.optJSONArray("caLst")?.let { ca ->
            for (i in 0 until ca.length()) {
                val item = ca.optJSONObject(i) ?: continue
                result.add(
                    ShareFile(
                        fid = item.optString("caID"),
                        fname = item.optString("caName"),
                        fsize = 0,
                        isdir = true,
                        pdirFid = pcaId,
                        fidToken = "",
                        modifyTime = item.optString("udTime").ifBlank { item.optString("ctTime") }
                    )
                )
            }
        }

        data.optJSONArray("coLst")?.let { co ->
            for (i in 0 until co.length()) {
                val item = co.optJSONObject(i) ?: continue
                result.add(
                    ShareFile(
                        fid = item.optString("coID"),
                        fname = item.optString("coName"),
                        fsize = item.optLong("coSize"),
                        isdir = item.optBoolean("isdir", item.optInt("coType", 1) == 2),
                        pdirFid = pcaId,
                        fidToken = "",
                        modifyTime = item.optString("udTime").ifBlank { item.optString("ctTime") }
                    )
                )
            }
        }
        result
    }

    suspend fun getShareDownloadLink(
        coId: String,
        linkId: String,
        account: String,
        authorization: String?
    ): DownloadLink? = withContext(Dispatchers.IO) {
        val reqV3 = JSONObject()
            .put("account", account)
            .put("linkID", linkId)
            .put("coIDLst", JSONObject().put("item", JSONArray().put(coId)))
            .put(
                "commonAccountInfo",
                JSONObject().put("account", account).put("accountType", 1)
            )
        val plain = JSONObject().put("dlFromOutLinkReqV3", reqV3).toString()
        val respJson = sharePostEncrypted(C139Constants.SHARE_LINK_URL, plain, authorization)
        val resultCode = respJson.optString("resultCode")
        if (resultCode.isNotBlank() && resultCode != "0") {
            throw IllegalStateException(respJson.optString("desc").ifBlank { "获取下载链接失败（$resultCode）" })
        }
        if (!respJson.optBoolean("success", true)) {
            throw IllegalStateException(respJson.optString("desc").ifBlank { "获取下载链接失败" })
        }
        val data = respJson.optJSONObject("data") ?: return@withContext null
        val url = data.optString("redrUrl")
        if (url.isBlank()) return@withContext null
        DownloadLink(
            fid = coId,
            filename = data.optString("fileName").ifEmpty { data.optString("coName").ifEmpty { coId } },
            downloadUrl = url,
            size = data.optLong("coSize", data.optLong("size"))
        )
    }

    data class C139TaskStatus(
        val status: String,
        val progress: Int,
        val results: List<Pair<String, String>>
    )

    data class C139TransferResult(
        val done: Boolean,
        val mapping: Map<String, String>
    )

    private suspend fun fetchCloudPage(
        parentFileId: String,
        cookie: String,
        pageCursor: String?
    ): Pair<List<ShareFile>, String?>? = withContext(Dispatchers.IO) {
        val authorization = C139Constants.extractAuthorization(cookie)
            ?: throw IllegalStateException("登录态缺少 authorization，请重新登录")
        val cursorValue: Any =
            pageCursor?.takeIf { it.isNotBlank() && it != "null" } ?: JSONObject.NULL
        val req = JSONObject()
            .put("pageInfo", JSONObject().put("pageSize", 100).put("pageCursor", cursorValue))
            .put("orderBy", "updated_at")
            .put("orderDirection", "DESC")
            .put("parentFileId", parentFileId)
            .put("imageThumbnailStyleList", JSONArray().put("Small").put("Large"))
        val resp = cloudPost(C139Constants.FILE_LIST_URL, req.toString(), authorization)
        checkCloud(resp, "获取文件列表失败")
        val data = resp.optJSONObject("data") ?: return@withContext null
        val files = buildList {
            data.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    add(
                        ShareFile(
                            fid = item.optString("fileId"),
                            fname = item.optString("name"),
                            fsize = item.optLong("size"),
                            isdir = item.optString("type") == "folder",
                            pdirFid = parentFileId,
                            fidToken = item.optString("fileId"),
                            modifyTime = item.optString("updatedAt")
                        )
                    )
                }
            }
        }
        val next = if (data.isNull("nextPageCursor")) {
            null
        } else {
            data.optString("nextPageCursor").takeIf { it.isNotBlank() && it != "null" }
        }
        Pair(files, next)
    }

    suspend fun listCloudFiles(
        parentFileId: String,
        cookie: String,
        pageCursor: String? = null
    ): List<ShareFile> {
        val all = mutableListOf<ShareFile>()
        val seen = HashSet<String>()
        var cursor = pageCursor
        repeat(200) {
            val (files, next) = fetchCloudPage(parentFileId, cookie, cursor) ?: return all
            for (f in files) {
                if (f.fid.isNotBlank() && seen.add(f.fid)) all.add(f)
            }
            if (next == null) return all
            if (next == cursor) return all
            cursor = next
        }
        return all
    }

    suspend fun listFolders(parentFileId: String, cookie: String): List<ShareFile> = withContext(Dispatchers.IO) {
        val authorization = C139Constants.extractAuthorization(cookie)
            ?: throw IllegalStateException("登录态缺少 authorization，请重新登录")
        val req = JSONObject()
            .put("pageInfo", JSONObject().put("pageSize", 100).put("pageCursor", JSONObject.NULL))
            .put("orderBy", "updated_at")
            .put("orderDirection", "DESC")
            .put("parentFileId", parentFileId)
            .put("imageThumbnailStyleList", JSONArray().put("Small").put("Large"))
            .put("type", "folder")
        val resp = cloudPost(C139Constants.FILE_LIST_URL, req.toString(), authorization)
        checkCloud(resp, "获取文件夹列表失败")
        val data = resp.optJSONObject("data") ?: return@withContext emptyList()
        buildList {
            data.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    add(
                        ShareFile(
                            fid = item.optString("fileId"),
                            fname = item.optString("name"),
                            fsize = 0,
                            isdir = true,
                            pdirFid = parentFileId,
                            fidToken = item.optString("fileId"),
                            modifyTime = item.optString("updatedAt")
                        )
                    )
                }
            }
        }
    }

    suspend fun renameFile(fileId: String, newName: String, cookie: String): Boolean = withContext(Dispatchers.IO) {
        val authorization = C139Constants.extractAuthorization(cookie)
            ?: throw IllegalStateException("登录态缺少 authorization，请重新登录")
        val req = JSONObject()
            .put("fileId", fileId)
            .put("name", newName)
            .put("description", "")
        val resp = cloudPost(C139Constants.FILE_UPDATE_URL, req.toString(), authorization)
        checkCloud(resp, "重命名失败")
        true
    }

    suspend fun moveFiles(fileIds: List<String>, toParentFileId: String, cookie: String): String? = withContext(Dispatchers.IO) {
        val authorization = C139Constants.extractAuthorization(cookie)
            ?: throw IllegalStateException("登录态缺少 authorization，请重新登录")
        val req = JSONObject()
            .put("fileIds", JSONArray().apply { fileIds.forEach { put(it) } })
            .put("toParentFileId", toParentFileId)
        val resp = cloudPost(C139Constants.BATCH_MOVE_URL, req.toString(), authorization)
        checkCloud(resp, "移动失败")
        resp.optJSONObject("data")?.optString("taskId")?.takeIf { it.isNotBlank() }
    }

    suspend fun deleteFiles(fileIds: List<String>, cookie: String): String? = withContext(Dispatchers.IO) {
        val authorization = C139Constants.extractAuthorization(cookie)
            ?: throw IllegalStateException("登录态缺少 authorization，请重新登录")
        val req = JSONObject().put("fileIds", JSONArray().apply { fileIds.forEach { put(it) } })
        val resp = cloudPost(C139Constants.BATCH_TRASH_URL, req.toString(), authorization)
        checkCloud(resp, "删除失败")
        resp.optJSONObject("data")?.optString("taskId")?.takeIf { it.isNotBlank() }
    }

    suspend fun getTask(taskId: String, cookie: String): C139TaskStatus = withContext(Dispatchers.IO) {
        val authorization = C139Constants.extractAuthorization(cookie)
            ?: throw IllegalStateException("登录态缺少 authorization，请重新登录")
        val req = JSONObject().put("taskId", taskId)
        val resp = cloudPost(C139Constants.TASK_GET_URL, req.toString(), authorization)
        checkCloud(resp, "查询任务失败")
        val data = resp.optJSONObject("data") ?: return@withContext C139TaskStatus("", 0, emptyList())
        val taskInfo = data.optJSONObject("taskInfo")
        val status = taskInfo?.optString("status") ?: ""
        val progress = taskInfo?.optInt("progress") ?: 0
        val results = buildList {
            data.optJSONArray("batchFileResults")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    add(item.optString("fileId") to item.optString("errCode"))
                }
            }
        }
        C139TaskStatus(status, progress, results)
    }

    suspend fun getDownloadUrl(fileId: String, cookie: String): DownloadLink? = withContext(Dispatchers.IO) {
        val authorization = C139Constants.extractAuthorization(cookie)
            ?: throw IllegalStateException("登录态缺少 authorization，请重新登录")
        val req = JSONObject().put("fileId", fileId)
        val resp = cloudPost(C139Constants.DOWNLOAD_URL, req.toString(), authorization)
        checkCloud(resp, "获取下载链接失败")
        val data = resp.optJSONObject("data") ?: return@withContext null
        val url = data.optString("url")
        if (url.isBlank()) return@withContext null
        DownloadLink(
            fid = fileId,
            filename = data.optString("name"),
            downloadUrl = url,
            size = data.optLong("size")
        )
    }

    suspend fun createShare(
        coIDLst: List<String>,
        caIDLst: List<String>,
        period: Int?,
        dedicatedName: String,
        cookie: String
    ): ShareInfo = withContext(Dispatchers.IO) {
        val authorization = C139Constants.extractAuthorization(cookie)
            ?: throw IllegalStateException("登录态缺少 authorization，请重新登录")
        val account = accountFromAuthorization(authorization)
            ?: throw IllegalStateException("无法从登录态解析账号，请重新登录")
        val getOutLinkReq = JSONObject()
            .put("subLinkType", 0)
            .put("encrypt", 1)
            .put("coIDLst", JSONArray().apply { coIDLst.forEach { put(it) } })
            .put("caIDLst", JSONArray().apply { caIDLst.forEach { put(it) } })
            .put("pubType", 1)
            .put("dedicatedName", dedicatedName)
            .put("periodUnit", 1)
            .apply { if (period != null) put("period", period) }
            .put("viewerLst", JSONArray())
            .put("extInfo", JSONObject().put("isWatermark", 0).put("shareChannel", "3001"))
            .put("commonAccountInfo", JSONObject().put("account", account).put("accountType", 1))
        val plain = JSONObject().put("getOutLinkReq", getOutLinkReq).toString()
        val resp = cloudPost(C139Constants.OUTLINK_CREATE_URL, plain, authorization, cookie, needSkey = true)
        if (!resp.optBoolean("success", true) || resp.optString("code") != "0") {
            throw IllegalStateException(resp.optString("message").ifBlank { "创建分享失败" })
        }
        val set = resp.optJSONObject("data")
            ?.optJSONObject("getOutLinkRes")
            ?.optJSONArray("getOutLinkResSet")
            ?.optJSONObject(0)
            ?: throw IllegalStateException("创建分享失败：未返回链接")
        val linkUrl = set.optString("linkUrl")
        if (linkUrl.isBlank()) throw IllegalStateException("创建分享失败：未返回链接")
        ShareInfo(
            shareUrl = linkUrl,
            passcode = set.optString("passwd"),
            pwdId = set.optString("linkID"),
            title = dedicatedName,
            expiredType = when (period) {
                1 -> 2
                7 -> 3
                30 -> 4
                else -> 1
            }
        )
    }

    suspend fun getQuota(cookie: String): QuotaInfo? = withContext(Dispatchers.IO) {
        val authorization = C139Constants.extractAuthorization(cookie)
            ?: return@withContext null
        val account = accountFromAuthorization(authorization) ?: return@withContext null
        runCatching {
            val req = JSONObject()
                .put("userDomainId", "")
                .put("commonAccountInfo", JSONObject().put("account", account).put("accountType", 1))
            val resp = cloudPost(
                "https://user-njs.yun.139.com/user/disk/quota/detail",
                req.toString(), authorization, cookie, needSkey = true
            )
            checkCloud(resp, "获取空间详情失败")
            val data = resp.optJSONObject("data") ?: return@runCatching null
            val total = data.optLong("diskSize") * 1024L * 1024L
            val used = data.optLong("freeDiskSize").let { free ->
                data.optJSONArray("quotaList")?.optJSONObject(0)?.optLong("usedSize")?.times(1024L * 1024L)
                    ?: (total - free * 1024L * 1024L)
            }
            QuotaInfo(used = used, total = total)
        }.getOrNull()
    }

    suspend fun createTransferTask(
        coIDLst: List<String>,
        catalogIDLst: List<String>,
        toFolderId: String,
        linkID: String,
        account: String,
        authorization: String?
    ): String? = withContext(Dispatchers.IO) {
        val taskInfo = JSONObject()
            .put("contentInfoList", JSONArray().apply { coIDLst.forEach { put("/$it") } })
            .put("catalogInfoList", JSONArray().apply { catalogIDLst.forEach { put(it) } })
            .put("newCatalogID", toFolderId)
            .put("linkID", linkID)
            .put("newCatalogName", "手机图片")
            .put("needPassword", true)
        val req = JSONObject()
            .put("createOuterLinkBatchOprTaskReq", JSONObject()
                .put("msisdn", account)
                .put("ownerAccount", "")
                .put("taskType", 1)
                .put("taskInfo", taskInfo)
                .put("linkID", linkID)
                .put("needPassword", true))
            .put("commonAccountInfo", JSONObject().put("account", account).put("accountType", 1))
        val resp = sharePostEncrypted(C139Constants.TRANSFER_CREATE_URL, req.toString(), authorization)
        val code = resp.optString("resultCode").ifBlank { resp.optString("code") }
        if (code.isNotBlank() && code != "0") {
            throw IllegalStateException(resp.optString("desc").ifBlank { "创建转存任务失败（$code）" })
        }
        resp.optJSONObject("data")?.optString("taskID")?.takeIf { it.isNotBlank() }
    }

    suspend fun queryTransferTask(taskID: String, account: String, authorization: String?): C139TransferResult =
        withContext(Dispatchers.IO) {
            val req = JSONObject().put(
                "queryBatchOprTaskDetailReq",
                JSONObject()
                    .put("taskID", taskID)
                    .put("msisdn", account)
                    .put("commonAccountInfo", JSONObject().put("account", account).put("accountType", 1))
            )
            val resp = sharePostEncrypted(C139Constants.TRANSFER_QUERY_URL, req.toString(), authorization)
            val code = resp.optString("resultCode").ifBlank { resp.optString("code") }
            if (code.isNotBlank() && code != "0") {
                throw IllegalStateException(resp.optString("desc").ifBlank { "查询转存结果失败（$code）" })
            }
            val data = resp.optJSONObject("data") ?: return@withContext C139TransferResult(false, emptyMap())
            val task = data.optJSONObject("batchOprTask")
            val done = (task?.optInt("progress") ?: 0) >= 100 && (task?.optInt("taskStatus") ?: 0) == 2
            val mapping = buildMap {
                data.optJSONObject("contentList")?.optJSONArray("idRspInfo")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        if (item.optString("reason") == "0000") {
                            put(item.optString("srcId"), item.optString("rstId"))
                        }
                    }
                }
            }
            C139TransferResult(done, mapping)
        }

    private fun cloudPost(
        url: String,
        plainBody: String,
        authorization: String,
        cookie: String? = null,
        needSkey: Boolean = false
    ): JSONObject {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", authorization)
            .header("mcloud-sign", signHeader(plainBody))
            .header("x-yun-channel-source", C139Constants.YUN_CHANNEL_SOURCE)
            .header("x-yun-app-channel", C139Constants.YUN_CHANNEL_SOURCE)
            .header("x-huawei-channelSrc", C139Constants.YUN_CHANNEL_SOURCE)
            .header("mcloud-version", C139Constants.MCLOUD_VERSION)
            .header("mcloud-client", C139Constants.MCLOUD_CLIENT)
            .header("mcloud-channel", C139Constants.MCLOUD_CHANNEL)
            .header("mcloud-route", "001")
            .header("x-yun-module-type", C139Constants.YUN_MODULE_TYPE)
            .header("x-yun-api-version", "v1")
            .header("x-yun-svc-type", "1")
            .header("x-SvcType", "1")
            .header("caller", "web")
            .header("x-inner-ntwk", "2")
            .header("CMS-DEVICE", "default")
            .header("x-m4c-src", C139Constants.M4C_SRC)
            .header("x-m4c-caller", C139Constants.M4C_CALLER)
            .header("X-Deviceinfo", C139Constants.X_DEVICEINFO)
            .header("x-yun-client-info", C139Constants.X_CLIENT_INFO)
            .header("INNER-HCY-ROUTER-HTTPS", "1")
            .header("Sec-Fetch-Site", "same-site")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Dest", "empty")
            .header("X-Requested-With", "mark.via")
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("User-Agent", C139Constants.PC_UA)
            .header("Origin", "https://yun.139.com")
            .header("Referer", "https://yun.139.com/")
            .header("Accept", "application/json, text/plain, */*")
        if (cookie != null) {
            builder.header("Cookie", cookie)
            if (needSkey) {
                val skey = cookie.split(";").map { it.trim() }
                    .firstOrNull { it.startsWith("skey=") }?.substringAfter('=')
                if (!skey.isNullOrBlank()) builder.header("mcloud-skey", skey)
            }
        }
        val request = builder.post(plainBody.toRequestBody(jsonMediaType)).build()
        val response = client.newCall(request).execute()
        val body = response.use { it.body?.string() ?: throw IllegalStateException("请求失败：响应为空") }
        return JSONObject(body)
    }

    private fun checkCloud(json: JSONObject, fallback: String) {
        val code = json.optString("code")
        if (json.optBoolean("success", true) && (code == "0000" || code == "0")) return
        val msg = json.optString("message").ifBlank { fallback }
        throw IllegalStateException("$msg（code=$code）")
    }

    private fun sharePostAnonymous(url: String, plainBody: String): JSONObject {
        val encrypted = encryptBody(plainBody)
        val request = Request.Builder()
            .url(url)
            .header("hcy-cool-flag", "1")
            .header("x-deviceinfo", C139Constants.SHARE_X_DEVICEINFO)
            .header("x-huawei-channelsrc", C139Constants.SHARE_X_HUAWEI_CHANNELSRC)
            .header("x-mm-source", C139Constants.SHARE_X_MM_SOURCE)
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("User-Agent", C139Constants.SHARE_MOBILE_UA)
            .header("Origin", "https://yun.139.com")
            .header("Referer", "https://yun.139.com/")
            .header("Accept", "application/json, text/plain, */*")
            .post(encrypted.toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        val body = response.use { it.body?.string() ?: throw IllegalStateException("请求失败：响应为空") }
        return runCatching { JSONObject(decryptBody(body)) }.getOrElse { JSONObject(body) }
    }

    private fun sharePostEncrypted(url: String, plainBody: String, authorization: String?): JSONObject {
        val encrypted = encryptBody(plainBody)
        val request = Request.Builder()
            .url(url)
            .apply { if (!authorization.isNullOrBlank()) header("Authorization", authorization) }
            .header("hcy-cool-flag", "1")
            .header("x-deviceinfo", C139Constants.SHARE_X_DEVICEINFO)
            .header("x-huawei-channelsrc", C139Constants.SHARE_X_HUAWEI_CHANNELSRC)
            .header("x-mm-source", C139Constants.SHARE_X_MM_SOURCE)
            .header("mcloud-sign", signHeader(plainBody))
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("User-Agent", C139Constants.SHARE_MOBILE_UA)
            .header("Origin", "https://yun.139.com")
            .header("Referer", "https://yun.139.com/")
            .header("Accept", "application/json, text/plain, */*")
            .post(encrypted.toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        val body = response.use { it.body?.string() ?: throw IllegalStateException("请求失败：响应为空") }
        return runCatching { JSONObject(decryptBody(body)) }.getOrElse { JSONObject(body) }
    }

}
