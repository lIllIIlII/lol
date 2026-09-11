package com.yunx.app.data.network

import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.random.Random

data class XunleiShareResult(
    val title: String,
    val files: List<ShareFile>,
    val passCodeToken: String,
    val shareId: String,
    val nextPageToken: String = ""
)

data class XunleiFilePage(val files: List<ShareFile>, val nextPageToken: String)

data class XunleiLoginStep(
    val needSms: Boolean = false,
    val smsCreditKey: String = "",
    val smsToken: String = "",
    val sessionKey: String = "",
    val sessionId: String = "",
    val nickname: String = "",
    val userID: String = "",
    val reviewUrl: String = "",
    val message: String = ""
)

class XunleiApi(
    private val clientProvider: () -> OkHttpClient = { HttpClients.apiClient() }
) {
    private val client get() = clientProvider()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val formMediaType = "application/x-www-form-urlencoded".toMediaType()

    @Volatile
    private var refreshedCaptcha: String? = null

    @Volatile
    private var currentAccessToken: String = ""

    var refreshTokenProvider: suspend (deviceId: String) -> Pair<String, String>? = { null }

    @Volatile
    private var currentUserId: String = ""

    suspend fun initCaptcha(
        deviceId: String,
        username: String,
        action: String = "POST:/auth/signin/token"
    ): String? = withContext(Dispatchers.IO) {
        val ts = System.currentTimeMillis().toString()
        val sign = buildCaptchaSign(deviceId, ts)
        val body = JSONObject()
            .put("action", action)
            .put("captcha_token", "")
            .put("client_id", XunleiConstants.APP_CLIENT_ID)
            .put("device_id", deviceId)
            .put("meta", JSONObject()
                .put("username", username)
                .put("client_version", XunleiConstants.APP_CLIENT_VERSION)
                .put("package_name", XunleiConstants.APP_PACKAGE_NAME)
                .put("timestamp", ts)
                .put("captcha_sign", sign)
                .put("user_id", currentUserId))
            .put("redirect_uri", "xlaccsdk01://xunlei.com/callback?state=harbor")
            .toString()
        val request = Request.Builder()
            .url(XunleiConstants.CAPTCHA_INIT_URL)
            .header("User-Agent", XunleiConstants.APP_UA)
            .header("Accept", "application/json;charset=UTF-8")
            .header("Content-Type", "application/json")
            .header("X-Client-Id", XunleiConstants.APP_CLIENT_ID)
            .header("X-Device-Id", deviceId)
            .header("X-Client-Version", "8.31.0.9726")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: "{}")
                json.optString("captcha_token").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    suspend fun loginWithPassword(
        username: String,
        password: String,
        deviceId: String,
        checkCode: String = ""
    ): XunleiLoginStep = withContext(Dispatchers.IO) {
        val body = baseLoginBody(deviceId, "25.0.5.25", "513006")
            .put("userName", username)
            .put("passWord", password)
            .put("verifyKey", "")
            .put("verifyCode", checkCode)
            .put("isMd5Pwd", "0")
            .toString()
        val request = Request.Builder()
            .url(XunleiConstants.LOGIN_URL)
            .header("User-Agent", "android-ok-http-client/xl-acc-sdk/version-5.1.3.513006")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { resp ->
            val json = JSONObject(resp.body?.string() ?: "{}")
            parseLoginResponse(json)
        }
    }

    suspend fun sendSms(mobile: String, deviceId: String): XunleiLoginStep = withContext(Dispatchers.IO) {
        val body = baseLoginBody(deviceId, "8.31.0.9726", "231500")
            .put("mobile", mobile)
            .put("register", "0")
            .toString()
        val request = Request.Builder()
            .url(XunleiConstants.SEND_SMS_URL)
            .header("User-Agent", "android-ok-http-client/xl-acc-sdk/version-5.0.12.512000")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { resp ->
            val json = JSONObject(resp.body?.string() ?: "{}")
            XunleiLoginStep(
                needSms = true,
                smsCreditKey = json.optString("creditkey"),
                smsToken = json.optString("token"),
                message = json.optString("errorDesc").ifBlank { "短信已发送" }
            )
        }
    }

    suspend fun smsLogin(
        mobile: String,
        smsCode: String,
        creditKey: String,
        smsToken: String,
        deviceId: String
    ): XunleiLoginStep = withContext(Dispatchers.IO) {
        val body = baseLoginBody(deviceId, "8.31.0.9726", "231500", creditKey)
            .put("mobile", mobile)
            .put("smsCode", smsCode)
            .put("token", smsToken)
            .put("register", "0")
            .toString()
        val request = Request.Builder()
            .url(XunleiConstants.SMS_LOGIN_URL)
            .header("User-Agent", "android-ok-http-client/xl-acc-sdk/version-5.0.12.512000")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { resp ->
            val json = JSONObject(resp.body?.string() ?: "{}")
            parseLoginResponse(json)
        }
    }

    suspend fun loginWithTokenPassword(
        username: String,
        password: String,
        deviceId: String,
        captchaToken: String,
        checkCode: String = ""
    ): Pair<String, String>? = null

    suspend fun exchangeToken(sessionId: String, deviceId: String, captchaToken: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("client_id", XunleiConstants.APP_CLIENT_ID)
            .put("client_secret", XunleiConstants.APP_CLIENT_SECRET)
            .put("provider", "access_end_point_token")
            .put("signin_token", sessionId)
            .toString()
        val builder = Request.Builder()
            .url(XunleiConstants.TOKEN_URL)
            .header("User-Agent", XunleiConstants.APP_UA)
            .header("Accept", "application/json;charset=UTF-8")
            .header("Content-Type", "application/json")
            .header("X-Client-Id", XunleiConstants.APP_CLIENT_ID)
            .header("X-Device-Id", deviceId)
            .header("X-Client-Version", "8.31.0.9726")
        if (captchaToken.isNotBlank()) builder.header("X-Captcha-Token", captchaToken)
        val request = builder.post(body.toRequestBody(jsonMediaType)).build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                val json = JSONObject(resp.body?.string() ?: "{}")
                val at = json.optString("access_token").ifBlank { json.optString("accessToken") }
                val rt = json.optString("refresh_token").ifBlank { json.optString("refreshToken") }
                if (at.isBlank()) null else {
                    jwtSub(at).takeIf { it.isNotBlank() }?.let { currentUserId = it }
                    currentAccessToken = at
                    at to rt
                }
            }
        }.getOrNull()
    }

    fun cacheUserId(accessToken: String) {
        if (currentUserId.isBlank()) currentUserId = jwtSub(accessToken)
    }

    suspend fun refreshToken(refreshToken: String, deviceId: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val body = "grant_type=refresh_token" +
                "&client_id=${XunleiConstants.APP_CLIENT_ID}" +
                "&client_secret=${XunleiConstants.APP_CLIENT_SECRET}" +
                "&refresh_token=${java.net.URLEncoder.encode(refreshToken, "UTF-8")}"
            val request = Request.Builder()
                .url(XunleiConstants.REFRESH_URL)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Device-Id", deviceId)
                .post(body.toRequestBody(formMediaType))
                .build()
            runCatching {
                client.newCall(request).execute().use { resp ->
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val at = json.optString("access_token").ifBlank { json.optString("accessToken") }
                    val rt = json.optString("refresh_token").ifBlank { json.optString("refreshToken") }
                    if (at.isBlank()) null else {
                        jwtSub(at).takeIf { it.isNotBlank() }?.let { currentUserId = it }
                        currentAccessToken = at
                        at to rt
                    }
                }
            }.getOrNull()
        }

    fun jwtExp(token: String): Long = runCatching {
        val payload = token.split(".").getOrNull(1) ?: return@runCatching 0L
        val json = String(
            android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
        )
        JSONObject(json).optLong("exp")
    }.getOrDefault(0L)

    private fun jwtSub(token: String): String = runCatching {
        val payload = token.split(".").getOrNull(1) ?: return@runCatching ""
        val json = String(
            android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
        )
        JSONObject(json).optString("sub")
    }.getOrDefault("")

    private fun parseLoginResponse(json: JSONObject): XunleiLoginStep {
        val errorCode = json.optString("errorCode")
        if (errorCode == "0" || json.optString("error") == "success") {
            return XunleiLoginStep(
                needSms = false,
                sessionKey = json.optString("loginKey"),
                sessionId = json.optString("sessionID"),
                nickname = json.optString("nickName"),
                userID = json.optString("userID"),
                message = "登录成功"
            )
        }
        val error = json.optString("error")
        val needSms = error == "review_panel" || errorCode == "1007" ||
            json.optString("verifyType") == "MEA" || json.optString("verifyType").isNotBlank()
        return XunleiLoginStep(
            needSms = needSms,
            reviewUrl = json.optString("reviewurl"),
            message = json.optString("errorDesc").ifBlank { json.optString("error_description") }
        )
    }

    private fun baseLoginBody(
        deviceId: String,
        clientVersion: String,
        sdkVersion: String,
        creditKey: String = ""
    ): JSONObject = JSONObject()
        .put("protocolVersion", "301")
        .put("sequenceNo", Random.nextLong(10000000, 99999999).toString())
        .put("platformVersion", "10")
        .put("isCompressed", "0")
        .put("appid", "40")
        .put("clientVersion", clientVersion)
        .put("peerID", XunleiDeviceFingerprint.peerId())
        .put("appName", "ANDROID-com.xunlei.downloadprovider")
        .put("sdkVersion", sdkVersion)
        .put("devicesign", XunleiDeviceFingerprint.deviceSign())
        .put("netWorkType", "WIFI")
        .put("providerName", "NONE")
        .put("deviceModel", "M2004J7AC")
        .put("deviceName", "Xiaomi_M2004j7ac")
        .put("OSVersion", "12")
        .put("creditkey", creditKey)
        .put("hl", "zh-CN")

    private suspend fun getFilesPage(
        parentId: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String,
        pageToken: String
    ): Pair<List<ShareFile>, String> = withContext(Dispatchers.IO) {
        val filters = java.net.URLEncoder.encode("""{"trashed":{"eq":false}}""", "UTF-8")
        val url = buildString {
            append(XunleiConstants.FILES_URL)
            append("?parent_id=").append(parentId)
            append("&page_token=").append(java.net.URLEncoder.encode(pageToken, "UTF-8"))
            append("&limit=100&with_audit=true&filters=").append(filters)
        }
        panCall(captchaToken, deviceId, "GET:/drive/v1/files", { t ->
            panRequest(url, accessToken, deviceId, t)
        }) { data ->
            val files = data.optJSONArray("files")?.let(::parseFileArray) ?: emptyList()
            val next = data.optString("next_page_token").takeIf { it.isNotBlank() } ?: ""
            files to next
        }
    }

    suspend fun getFiles(
        parentId: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String
    ): List<ShareFile>? {
        val all = mutableListOf<ShareFile>()
        var token = ""
        repeat(100) {
            val (files, next) = getFilesPage(parentId, accessToken, deviceId, captchaToken, token)
            all += files
            if (next.isBlank()) return all.ifEmpty { null }
            token = next
        }
        return all.ifEmpty { null }
    }

    suspend fun createFolder(
        name: String,
        parentId: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String
    ): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("kind", "drive#folder")
            .put("name", name)
            .put("parent_id", parentId)
            .put("space", "")
            .toString()
        panCall(captchaToken, deviceId, "POST:/drive/v1/files", { t ->
            panRequest(XunleiConstants.FILES_URL, accessToken, deviceId, t, body)
        }) { data -> data.optString("id").takeIf { it.isNotBlank() } }
    }

    suspend fun getFileDetail(
        fileId: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String
    ): DownloadLink? = withContext(Dispatchers.IO) {
        val url = "${XunleiConstants.FILES_URL}/$fileId?_magic=2021&usage=PLAY&thumbnail_size=SIZE_LARGE" +
            "&with=hdr10&with=subtitle_files&with=task&with=public_share_tag"
        panCall(captchaToken, deviceId, "GET:/drive/v1/files/$fileId", { t ->
            panRequest(url, accessToken, deviceId, t)
        }) { data ->
            val links = data.optJSONObject("links")
            val urlStr = links?.optJSONObject("application/octet-stream")?.optString("url")
                ?: data.optString("web_content_link")
                ?: ""
            DownloadLink(
                fid = data.optString("id"),
                filename = data.optString("name"),
                downloadUrl = urlStr,
                size = data.optLong("size")
            )
        }
    }

    suspend fun getShare(
        shareId: String,
        passCode: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String,
        pageToken: String = ""
    ): XunleiShareResult? = withContext(Dispatchers.IO) {
        val url = buildString {
            append(XunleiConstants.SHARE_URL)
            append("?share_id=").append(shareId)
            append("&pass_code=").append(java.net.URLEncoder.encode(passCode, "UTF-8"))
            append("&limit=100&page_token=")
                .append(java.net.URLEncoder.encode(pageToken, "UTF-8"))
                .append("&thumbnail_size=SIZE_SMALL")
        }
        panCall(captchaToken, deviceId, "GET:/drive/v1/share", { t ->
            panRequest(url, accessToken, deviceId, t)
        }) { data ->
            when (data.optString("share_status")) {
                "PASS_CODE_EMPTY" -> throw QuarkApiException("请输入提取码")
                "PASS_CODE_ERROR" -> throw QuarkApiException("提取码错误")
                "PASS_CODE_NEED" -> throw QuarkApiException("该分享需要提取码")
            }
            val files = data.optJSONArray("files")?.let(::parseFileArray) ?: emptyList()
            XunleiShareResult(
                title = data.optString("title"),
                files = files,
                passCodeToken = data.optString("pass_code_token"),
                shareId = shareId,
                nextPageToken = data.optString("next_page_token")
            )
        }
    }

    suspend fun getShareDetail(
        shareId: String,
        parentId: String,
        passCodeToken: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String,
        pageToken: String = ""
    ): XunleiFilePage? = withContext(Dispatchers.IO) {
        val url = buildString {
            append(XunleiConstants.SHARE_DETAIL_URL)
            append("?share_id=").append(shareId)
            append("&parent_id=").append(parentId)
            append("&pass_code_token=").append(java.net.URLEncoder.encode(passCodeToken, "UTF-8"))
            append("&limit=100&page_token=")
                .append(java.net.URLEncoder.encode(pageToken, "UTF-8"))
                .append("&thumbnail_size=SIZE_SMALL")
        }
        panCall(captchaToken, deviceId, "GET:/drive/v1/share/detail", { t ->
            panRequest(url, accessToken, deviceId, t)
        }) { data ->
            XunleiFilePage(
                files = data.optJSONArray("files")?.let(::parseFileArray) ?: emptyList(),
                nextPageToken = data.optString("next_page_token")
            )
        }
    }

    suspend fun restore(
        shareId: String,
        passCodeToken: String,
        parentFolderId: String,
        fileIds: List<String>,
        accessToken: String,
        deviceId: String,
        captchaToken: String
    ): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("share_id", shareId)
            .put("pass_code_token", passCodeToken)
            .put("parent_id", parentFolderId)
            .put("ancestor_ids", JSONArray())
            .put("file_ids", JSONArray().apply { fileIds.forEach { put(it) } })
            .put("specify_parent_id", true)
            .toString()
        panCall(captchaToken, deviceId, "POST:/drive/v1/share/restore", { t ->
            panRequest(XunleiConstants.RESTORE_URL, accessToken, deviceId, t, body)
        }) { data ->
            val trace = data.optJSONObject("params")?.optString("trace_file_ids").orEmpty()
            runCatching {
                val map = JSONObject(trace)
                fileIds.firstOrNull { map.has(it) }
                    ?.let { map.optString(it) }
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
                ?: data.optString("file_id").takeIf { it.isNotBlank() }
        }
    }

    suspend fun batchDelete(
        ids: List<String>,
        accessToken: String,
        deviceId: String,
        captchaToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("ids", JSONArray().apply { ids.forEach { put(it) } })
            .put("space", "")
            .toString()
        panCall(captchaToken, deviceId, "POST:/drive/v1/files:batchDelete", { t ->
            panRequest("${XunleiConstants.FILES_URL}:batchDelete", accessToken, deviceId, t, body)
        }) { true }
    }

    suspend fun pollTask(taskId: String, accessToken: String, deviceId: String, captchaToken: String): Boolean =
        withContext(Dispatchers.IO) {
            val url = "${XunleiConstants.TASKS_URL}/$taskId?type=share"
            for (i in 0 until 15) {
                val done = runCatching {
                    panCall(captchaToken, deviceId, "GET:/drive/v1/tasks/$taskId", { t ->
                        panRequest(url, accessToken, deviceId, t)
                    }) { data ->
                        val status = data.optString("status").ifBlank { data.optString("phase") }
                        status == "PHASE_TYPE_COMPLETE" || data.optInt("error_code") == 0
                    }
                }.getOrDefault(false)
                if (done) return@withContext true
                delay(1000)
            }
            false
        }

    suspend fun ensureTempDir(
        accessToken: String,
        deviceId: String,
        captchaToken: String
    ): String? = withContext(Dispatchers.IO) {
        val root = getFiles("", accessToken, deviceId, captchaToken) ?: emptyList()
        root.firstOrNull { it.isdir && it.fname == XunleiConstants.TEMP_DIR_NAME }?.fid
            ?: createFolder(XunleiConstants.TEMP_DIR_NAME, "", accessToken, deviceId, captchaToken)
    }

    suspend fun getQuota(
        accessToken: String,
        deviceId: String,
        captchaToken: String
    ): QuotaInfo? = withContext(Dispatchers.IO) {
        runCatching {
            panCall(captchaToken, deviceId, "GET:/drive/v1/about", { t ->
                panRequest("${XunleiConstants.PAN_BASE}/drive/v1/about", accessToken, deviceId, t)
            }) { data ->
                val quota = data.optJSONObject("quota")
                QuotaInfo(
                    used = quota?.optLong("usage") ?: 0L,
                    total = quota?.optLong("limit") ?: 0L,
                    usedInTrash = quota?.optLong("usage_in_trash") ?: 0L
                )
            }
        }.getOrNull()
    }

    suspend fun renameFile(
        fileId: String,
        name: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        panCall(captchaToken, deviceId, "PATCH:/drive/v1/files/$fileId", { t ->
            panRequestM(
                "${XunleiConstants.FILES_URL}/$fileId",
                accessToken, deviceId, t, "PATCH",
                JSONObject().put("name", name).toString()
            )
        }) { data -> data.optString("id").isNotBlank() }
    }

    suspend fun moveFile(
        fileIds: List<String>,
        toParentId: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String
    ): String? = withContext(Dispatchers.IO) {
        panCall(captchaToken, deviceId, "POST:/drive/v1/files:batchMove", { t ->
            panRequestM(
                XunleiConstants.MOVE_URL,
                accessToken, deviceId, t, "POST",
                JSONObject()
                    .put("ids", JSONArray().apply { fileIds.forEach { put(it) } })
                    .put("to", JSONObject().put("parent_id", toParentId).put("space", ""))
                    .put("space", "")
                    .toString()
            )
        }) { data -> data.optString("task_id").takeIf { it.isNotBlank() } }
    }

    suspend fun createShare(
        fileIds: List<String>,
        title: String,
        expirationDays: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String,
        passCode: String = ""
    ): ShareInfo? = withContext(Dispatchers.IO) {
        panCall(captchaToken, deviceId, "POST:/drive/v1/share", { t ->
            panRequestM(
                XunleiConstants.SHARE_CREATE_URL,
                accessToken, deviceId, t, "POST",
                JSONObject()
                    .put("file_ids", JSONArray().apply { fileIds.forEach { put(it) } })
                    .put("share_to", "copy")
                    .put("params", JSONObject()
                        .put("subscribe_push", "false")
                        .put("WithPassCodeInLink", "true")
                        .put("with_pass_code_in_link", "true")
                        .apply { if (passCode.isNotBlank()) put("pass_code", passCode) })
                    .put("title", title.ifBlank { "分享文件" })
                    .put("restore_limit", "-1")
                    .put("expiration_days", expirationDays)
                    .toString()
            )
        }) { data ->
            ShareInfo(
                shareUrl = data.optString("share_url"),
                passcode = data.optString("pass_code"),
                pwdId = data.optString("share_id"),
                title = data.optString("title").ifBlank { title },
                expiredType = 1
            )
        }
    }

    suspend fun deleteFiles(
        fileIds: List<String>,
        accessToken: String,
        deviceId: String,
        captchaToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        panCall(captchaToken, deviceId, "POST:/drive/v1/files:batchTrash", { t ->
            panRequestM(
                XunleiConstants.TRASH_URL,
                accessToken, deviceId, t, "POST",
                JSONObject()
                    .put("ids", JSONArray().apply { fileIds.forEach { put(it) } })
                    .put("space", "")
                    .toString()
            )
        }) { true }
    }

    private fun parseFileArray(array: JSONArray): List<ShareFile> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            add(
                ShareFile(
                    fid = item.optString("id"),
                    fname = item.optString("name"),
                    fsize = item.optLong("size"),
                    isdir = item.optString("kind") == "drive#folder",
                    pdirFid = item.optString("parent_id"),
                    fidToken = "",
                    modifyTime = item.optString("modified_time")
                )
            )
        }
    }

    private fun panRequest(
        url: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String,
        body: String? = null
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", XunleiConstants.WEB_UA)
            .header("Authorization", "Bearer ${currentAccessToken.ifBlank { accessToken }}")
            .header("X-Device-Id", deviceId)
            .header("X-Client-Version", "8.31.0.9726")
            .header("Content-Type", "application/json")
            .header("Origin", "https://pan.xunlei.com")
            .header("Referer", "https://pan.xunlei.com/")
            if (captchaToken.isNotBlank()) builder.header("X-Captcha-Token", captchaToken)
        return if (body != null) builder.post(body.toRequestBody(jsonMediaType)).build()
        else builder.get().build()
    }

    private fun panRequestM(
        url: String,
        accessToken: String,
        deviceId: String,
        captchaToken: String,
        method: String,
        body: String? = null
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", XunleiConstants.WEB_UA)
            .header("Authorization", "Bearer ${currentAccessToken.ifBlank { accessToken }}")
            .header("X-Device-Id", deviceId)
            .header("X-Client-Version", "8.31.0.9726")
            .header("Content-Type", "application/json")
            .header("Origin", "https://pan.xunlei.com")
            .header("Referer", "https://pan.xunlei.com/")
            if (captchaToken.isNotBlank()) builder.header("X-Captcha-Token", captchaToken)
        val rb = body?.toRequestBody(jsonMediaType) ?: "{}".toRequestBody(jsonMediaType)
        return when (method) {
            "PATCH" -> builder.patch(rb).build()
            "GET" -> builder.get().build()
            else -> builder.post(rb).build()
        }
    }

    private suspend fun <T> panCall(
        captchaToken: String,
        deviceId: String,
        action: String,
        build: (String) -> Request,
        parse: (JSONObject) -> T
    ): T {
        var token = refreshedCaptcha ?: captchaToken
        repeat(2) { attempt ->
            val response = client.newCall(build(token)).execute()
            val body = response.use { it.body?.string() ?: throw QuarkApiException("请求失败：响应为空") }
            val json = runCatching { JSONObject(body) }.getOrElse {
                throw QuarkApiException("响应解析失败")
            }
            if (!response.isSuccessful || json.has("error")) {
                val err = json.optString("error")
                if ((response.code == 401 || err == "unauthenticated") && attempt == 0) {
                    val refreshed = refreshTokenProvider(deviceId)
                    if (refreshed != null) {
                        currentAccessToken = refreshed.first
                        val newCaptcha = initPanCaptcha(deviceId, action, token)
                        if (!newCaptcha.isNullOrBlank()) {
                            refreshedCaptcha = newCaptcha
                            token = newCaptcha
                        }
                        return@repeat
                    }
                }
                if (err == "captcha_invalid" && attempt == 0) {
                    val newToken = initPanCaptcha(deviceId, action, token)
                    if (!newToken.isNullOrBlank()) {
                        refreshedCaptcha = newToken
                        token = newToken
                        return@repeat
                    }
                }
                val msg = json.optString("error_description").ifBlank { json.optString("message") }
                    .ifBlank { err }.ifBlank { "请求失败" }
                throw QuarkApiException(msg)
            }
            return parse(json.optJSONObject("data") ?: json)
        }
        throw QuarkApiException("验证码刷新后仍失败")
    }

    private suspend fun initPanCaptcha(deviceId: String, action: String, oldToken: String): String? =
        withContext(Dispatchers.IO) {
            val ts = System.currentTimeMillis().toString()
            val sign = buildCaptchaSign(deviceId, ts)
            val body = JSONObject()
                .put("client_id", XunleiConstants.APP_CLIENT_ID)
                .put("action", action)
                .put("device_id", deviceId)
                .put("redirect_uri", "xlaccsdk01://xunlei.com/callback?state=harbor")
                .put(
                    "meta",
                    JSONObject()
                        .put("client_version", XunleiConstants.APP_CLIENT_VERSION)
                        .put("package_name", XunleiConstants.APP_PACKAGE_NAME)
                        .put("timestamp", ts)
                        .put("captcha_sign", sign)
                        .put("user_id", currentUserId)
                )
                .put("captcha_token", oldToken)
                .toString()
            val request = Request.Builder()
                .url(XunleiConstants.CAPTCHA_INIT_URL)
                .header("User-Agent", XunleiConstants.APP_UA)
                .header("Accept", "application/json;charset=UTF-8")
                .header("Content-Type", "application/json")
                .header("X-Client-Id", XunleiConstants.APP_CLIENT_ID)
                .header("X-Device-Id", deviceId)
                .header("X-Client-Version", "8.31.0.9726")
                .post(body.toRequestBody(jsonMediaType))
                .build()
            runCatching {
                client.newCall(request).execute().use { resp ->
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    json.optString("captcha_token").takeIf { it.isNotBlank() }
                }
            }.getOrNull()
        }

    private fun buildCaptchaSign(deviceId: String, tsMs: String): String {
        var h = XunleiConstants.APP_CLIENT_ID + XunleiConstants.APP_CLIENT_VERSION +
            XunleiConstants.APP_PACKAGE_NAME + deviceId + tsMs
        for (salt in XunleiConstants.CAPTCHA_SALTS) {
            h = md5Hex(h + salt)
        }
        return "1.$h"
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun newDeviceId(): String = XunleiDeviceFingerprint.deviceId()

        fun parseReviewUrl(reviewUrl: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            runCatching {
                val q = reviewUrl.substringAfter('?', "")
                q.split('&').forEach { pair ->
                    val parts = pair.split('=', limit = 2)
                    if (parts.size == 2) {
                        map[parts[0]] = java.net.URLDecoder.decode(parts[1], "UTF-8")
                    } else if (parts.size == 1 && parts[0].isNotBlank()) {
                        map[parts[0]] = ""
                    }
                }
            }
            return map
        }
    }
}
