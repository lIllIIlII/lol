package com.yunx.app.data.network

import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo
import com.yunx.app.data.network.model.ShareToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object QuarkCookieUtil {
    private val TRACKED = setOf("__puus", "__pus")

    fun mergeFromSetCookies(original: String, setCookies: List<String>): String {
        var cookie = original
        for (sc in setCookies) {
            val kv = sc.substringBefore(';').trim()
            val eq = kv.indexOf('=')
            if (eq <= 0) continue
            val name = kv.substring(0, eq)
            if (name in TRACKED) cookie = setOrReplace(cookie, name, kv.substring(eq + 1))
        }
        return cookie
    }

    fun withoutPuus(cookie: String): String =
        cookie.split(";").map { it.trim() }
            .filter { !it.startsWith("__puus=") }
            .joinToString("; ")

    private fun setOrReplace(cookie: String, name: String, value: String): String {
        val parts = cookie.split(";").map { it.trim() }.toMutableList()
        val idx = parts.indexOfFirst { it.startsWith("$name=") }
        val kv = "$name=$value"
        if (idx >= 0) parts[idx] = kv else parts.add(kv)
        return parts.joinToString("; ")
    }
}

class QuarkApi(
    private val clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    private val client get() = clientProvider()

    var cookieSink: ((String) -> Unit)? = null

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchNickname(cookie: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(QuarkConstants.ACCOUNT_INFO_URL)
            .header("Cookie", cookie)
            .header("User-Agent", QuarkConstants.API_USER_AGENT)
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                if (json.optBoolean("success", false)) {
                    json.optJSONObject("data")
                        ?.optString("nickname")
                        ?.takeIf { it.isNotBlank() }
                } else null
            }
        }.getOrNull()
    }

    suspend fun getShareToken(shareId: String, pwd: String?, cookie: String): ShareToken? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("pwd_id", shareId)
            .put("passcode", pwd ?: "")
            .put("support_visit_limit_private_share", true)
            .toString()
        val request = postJson(QuarkConstants.SHARE_TOKEN_URL, cookie, body)
        parseData(request) { data ->
            ShareToken(
                stoken = data.optString("stoken"),
                title = data.optString("title"),
                firstFid = data.optString("first_fid")
            )
        }
    }

    suspend fun verifySharePassword(shareId: String, passcode: String, cookie: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("share_id", shareId)
                .put("passcode", passcode)
                .toString()
            val request = postJson(QuarkConstants.SHARE_PASSWORD_URL, cookie, body)
            runCatching {
                client.newCall(request).execute().use { response ->
                    val json = JSONObject(response.body?.string() ?: "{}")
                    json.optInt("status") == 200
                }
            }.getOrDefault(false)
        }

    suspend fun getShareFiles(
        shareId: String,
        stoken: String,
        pdirFid: String,
        cookie: String,
        page: Int = 1,
        size: Int = 100
    ): List<ShareFile>? = withContext(Dispatchers.IO) {
        val url = buildString {
            append(QuarkConstants.SHARE_DETAIL_URL)
            append("&pwd_id=").append(shareId)
            append("&stoken=").append(URLEncoder.encode(stoken, "UTF-8"))
            append("&pdir_fid=").append(pdirFid)
            append("&ver=2")
            append("&force=0")
            append("&_page=").append(page)
            append("&_size=").append(size)
            append("&_fetch_banner=0")
            append("&_fetch_share=0")
            append("&fetch_relate_conversation=0")
            append("&_fetch_total=1")
            append("&_sort=file_type:asc,file_name:asc")
        }
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", QuarkConstants.API_USER_AGENT)
            .header("Origin", "https://pan.quark.cn")
            .header("Referer", "https://pan.quark.cn/")
            .get()
            .build()
        parseData(request) { data ->
            val array = data.optJSONArray("list") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        ShareFile(
                            fid = item.optString("fid"),
                            fname = item.optString("file_name"),
                            fsize = item.optLong("size"),
                            isdir = item.optBoolean("dir", false),
                            pdirFid = item.optString("pdir_fid"),
                            fidToken = item.optString("share_fid_token"),
                            modifyTime = item.optString("updated_at")
                        )
                    )
                }
            }
        }
    }

    suspend fun getFileList(
        pdirFid: String,
        cookie: String,
        page: Int = 1,
        size: Int = 100
    ): List<ShareFile>? = withContext(Dispatchers.IO) {
    val url = "${QuarkConstants.FILE_URL}&pdir_fid=$pdirFid&page=$page&size=$size"
    val request = get(url, cookie)
    parseData(request) { data ->
        val array = data.optJSONArray("list") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    ShareFile(
                        fid = item.optString("fid"),
                        fname = item.optString("file_name").ifEmpty { item.optString("fname") },
                        fsize = if (item.has("size")) item.optLong("size") else item.optLong("fsize"),
                        isdir = item.optBoolean("dir", false) || item.optInt("isdir") == 1,
                        pdirFid = item.optString("pdir_fid"),
                        fidToken = item.optString("fid_token"),
                        modifyTime = item.optString("modify_time")
                    )
                )
            }
        }
    }
}

    suspend fun listCloudFiles(
        pdirFid: String,
        cookie: String,
        page: Int = 1,
        size: Int = 100
    ): List<ShareFile>? = withContext(Dispatchers.IO) {
        val all = mutableListOf<ShareFile>()
        var p = page
        while (true) {
            val url = buildString {
                append(QuarkConstants.CLOUD_FILE_SORT_URL)
                append("&uc_param_str=")
                append("&pdir_fid=").append(pdirFid)
                append("&_page=").append(p)
                append("&_size=").append(size)
                append("&_fetch_total=1")
                append("&_fetch_sub_dirs=0")
                append("&_sort=file_type:asc,updated_at:desc")
                append("&fetch_all_file=1")
                append("&fetch_risk_file_name=1")
            }
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", QuarkConstants.API_USER_AGENT)
                .header("Origin", "https://pan.quark.cn")
                .header("Referer", "https://pan.quark.cn/")
                .get()
                .build()
            val files = parseData(request) { data ->
                val array = data.optJSONArray("list") ?: JSONArray()
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        add(
                            ShareFile(
                                fid = item.optString("fid"),
                                fname = item.optString("file_name").ifEmpty { item.optString("fname") },
                                fsize = item.optLong("size"),
                                isdir = item.optBoolean("dir", false),
                                pdirFid = item.optString("pdir_fid"),
                                fidToken = "",
                                modifyTime = item.optString("updated_at")
                            )
                        )
                    }
                }
            }
            all += files
            if (files.size < size || p >= 100) break
            p++
        }
        all.ifEmpty { null }
    }

    suspend fun createFolder(name: String, parentFid: String, cookie: String): String? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("pdir_fid", parentFid)
                .put("file_name", name)
                .put("dir_path", "")
                .put("dir_init_lock", false)
                .toString()
            val request = postJson(QuarkConstants.FILE_URL, cookie, body)
            parseData(request) { data -> data.optString("fid") }
        }

    suspend fun saveShareFile(
        shareId: String,
        stoken: String,
        pdirFid: String,
        fid: String,
        fidToken: String,
        toPdirFid: String,
        cookie: String
    ): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("pwd_id", shareId)
            .put("stoken", stoken)
            .put("pdir_fid", pdirFid)
            .put("to_pdir_fid", toPdirFid)
            .put("fid_list", JSONArray().put(fid))
            .put("fid_token_list", JSONArray().put(fidToken))
            .put("scene", "link")
            .toString()
        val request = postJson(QuarkConstants.SAVE_URL, cookie, body)
        parseData(request) { data -> data.optString("task_id").takeIf { it.isNotBlank() } }
    }

    suspend fun pollTask(taskId: String, cookie: String): String? = withContext(Dispatchers.IO) {
        val url = "${QuarkConstants.TASK_URL}&task_id=${URLEncoder.encode(taskId, "UTF-8")}&retry_index=0"
        for (i in 0 until 10) {
            val savedFid = runCatching {
                client.newCall(get(url, cookie)).execute().use { response ->
                    val json = JSONObject(response.body?.string() ?: "{}")
                    if (json.optInt("status") != 200) return@use null
                    val data = json.optJSONObject("data") ?: return@use null
                    val finished = data.optLong("finished_at") > 0 ||
                        data.optInt("status") == 2 ||
                        data.optInt("task_status") == 2
                    if (!finished) return@use null
                    data.optJSONObject("save_as")
                        ?.optJSONArray("save_as_top_fids")
                        ?.optString(0)
                        ?.takeIf { it.isNotBlank() }
                }
            }.getOrNull()
            if (savedFid != null) return@withContext savedFid
            delay(1000)
        }
        null
    }

    suspend fun getQuota(cookie: String): QuotaInfo? = withContext(Dispatchers.IO) {
        val url = "https://drive-pc.quark.cn/1/clouddrive/member?pr=ucpro&fr=pc&fetch_subscribe=true&_ch=home"
        runCatching {
            val response = client.newCall(get(url, cookie)).execute()
            val body = response.use { it.body?.string() ?: return@runCatching null }
            val data = JSONObject(body).optJSONObject("data") ?: return@runCatching null
            QuotaInfo(
                used = data.optLong("use_capacity"),
                total = data.optLong("total_capacity")
            )
        }.getOrNull()
    }

    suspend fun refreshSession(cookie: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(QuarkConstants.CONFIG_URL)
            .header("Cookie", QuarkCookieUtil.withoutPuus(cookie))
            .header("User-Agent", QuarkConstants.API_USER_AGENT)
            .header("Referer", QuarkConstants.DOWNLOAD_REFERER)
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                val merged = QuarkCookieUtil.mergeFromSetCookies(cookie, resp.headers("Set-Cookie"))
                if (merged != cookie) cookieSink?.invoke(merged)
                merged
            }
        }.getOrNull()
    }

    suspend fun getDownloadLink(fid: String, cookie: String): DownloadLink? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("fids", JSONArray().put(fid)).toString()
        val request = postJson(QuarkConstants.DOWNLOAD_URL, cookie, body)
        val response = client.newCall(request).execute()
        val bodyStr = response.use {
            mergeCookieFromResponse(request, it)
            it.body?.string() ?: throw QuarkApiException("获取下载链接失败：响应为空")
        }
        val json = runCatching { JSONObject(bodyStr) }.getOrElse {
            throw QuarkApiException("响应解析失败")
        }
        if (json.optInt("status") != 200 && json.optInt("code") != 0) {
            throw QuarkApiException(
                json.optString("message").ifBlank { "获取下载链接失败" },
                json.optInt("code")
            )
        }
        val array = json.optJSONArray("data") ?: throw QuarkApiException("响应缺少 data")
        if (array.length() == 0) throw QuarkApiException("未返回下载链接")
        val item = array.optJSONObject(0) ?: throw QuarkApiException("未返回下载链接")
        DownloadLink(
            fid = item.optString("fid"),
            filename = item.optString("file_name").ifEmpty { item.optString("filename") },
            downloadUrl = item.optString("download_url"),
            size = item.optLong("size")
        )
    }

    suspend fun deleteFile(fid: String, cookie: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("action_type", 2)
            .put("filelist", JSONArray().put(fid))
            .put("exclude_fids", JSONArray())
            .toString()
        val request = postJson(QuarkConstants.DELETE_URL, cookie, body)
        parseData(request) { data -> data.optString("task_id").takeIf { it.isNotBlank() } }
    }

    suspend fun renameFile(fid: String, newName: String, cookie: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("fid", fid)
                .put("file_name", newName)
                .toString()
            val request = postJson(QuarkConstants.RENAME_URL, cookie, body)
            runCatching {
                client.newCall(request).execute().use { response ->
                    val json = JSONObject(response.body?.string() ?: "{}")
                    json.optInt("status") == 200
                }
            }.getOrDefault(false)
        }

    suspend fun moveFile(fid: String, toPdirFid: String, cookie: String): String? =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("action_type", 1)
                .put("to_pdir_fid", toPdirFid)
                .put("filelist", JSONArray().put(fid))
                .put("exclude_fids", JSONArray())
                .toString()
            val request = postJson(QuarkConstants.MOVE_URL, cookie, body)
            parseData(request) { data -> data.optString("task_id").takeIf { it.isNotBlank() } }
        }

    suspend fun createShare(
        fidList: List<String>,
        title: String,
        urlType: Int,
        passcode: String,
        expiredType: Int,
        cookie: String
    ): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("fid_list", JSONArray().apply { fidList.forEach { put(it) } })
            .put("title", title.ifBlank { "分享文件" })
            .put("url_type", urlType)
            .apply { if (passcode.isNotBlank()) put("passcode", passcode) }
            .put("expired_type", expiredType)
            .put("support_error_code", JSONArray().put("41060"))
            .toString()
        val request = postJson(QuarkConstants.SHARE_CREATE_URL, cookie, body)
        val taskId = parseData(request) { data ->
            data.optString("task_id").takeIf { it.isNotBlank() }
        } ?: return@withContext null
        pollShareTask(taskId, cookie)
    }

    private suspend fun pollShareTask(taskId: String, cookie: String): String? =
        withContext(Dispatchers.IO) {
            val url = "${QuarkConstants.TASK_URL}&task_id=${URLEncoder.encode(taskId, "UTF-8")}&retry_index=0"
            for (i in 0 until 15) {
                val shareId = runCatching {
                    client.newCall(get(url, cookie)).execute().use { resp ->
                        val json = JSONObject(resp.body?.string() ?: "{}")
                        if (json.optInt("status") != 200) return@use null
                        val data = json.optJSONObject("data") ?: return@use null
                        val finished = data.optLong("finished_at") > 0 || data.optInt("status") == 2
                        if (!finished) return@use null
                        data.optString("share_id").takeIf { it.isNotBlank() }
                    }
                }.getOrNull()
                if (shareId != null) return@withContext shareId
                delay(1000)
            }
            null
        }

    suspend fun getShareInfo(shareId: String, cookie: String): ShareInfo? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("share_id", shareId).toString()
        val request = postJson(QuarkConstants.SHARE_INFO_URL, cookie, body)
        parseData(request) { data ->
            ShareInfo(
                shareUrl = data.optString("share_url"),
                passcode = data.optString("passcode"),
                pwdId = data.optString("pwd_id"),
                title = data.optString("title"),
                expiredType = data.optInt("expired_type")
            )
        }
    }

    private fun get(url: String, cookie: String): Request =
        Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", QuarkConstants.API_USER_AGENT)
            .get()
            .build()

    private fun postJson(url: String, cookie: String, body: String): Request =
        Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", QuarkConstants.API_USER_AGENT)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

    private fun <T> parseData(request: Request, parser: (JSONObject) -> T): T {
        val response = client.newCall(request).execute()
        val body = response.use {
            mergeCookieFromResponse(request, it)
            it.body?.string() ?: throw QuarkApiException("请求失败：响应为空")
        }
        val json = runCatching { JSONObject(body) }.getOrElse {
            throw QuarkApiException("响应解析失败")
        }
        if (json.optInt("status") != 200) {
            throw QuarkApiException(json.optString("message").ifBlank { "请求失败" })
        }
        return parser(json.optJSONObject("data") ?: throw QuarkApiException("响应缺少 data"))
    }

    private fun mergeCookieFromResponse(request: Request, response: okhttp3.Response) {
        val setCookies = response.headers("Set-Cookie")
        if (setCookies.isEmpty()) return
        val original = request.header("Cookie").orEmpty()
        if (original.isBlank()) return
        val merged = QuarkCookieUtil.mergeFromSetCookies(original, setCookies)
        if (merged != original) cookieSink?.invoke(merged)
    }
}
