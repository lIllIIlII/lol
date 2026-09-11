package com.yunx.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class WsDiskConfig(
    val hosts: List<String>,
    val recommendPath: String,
    val folderListPath: String,
    val redirectPath: String,
    val aesKey: String,
    val origin: String,
    val label: String
)

data class WsDiskEntry(
    val id: String,
    val name: String,
    val size: Long,
    val isDir: Boolean,
    val entryUserId: String = ""
)

data class WsDiskShareInfo(
    val shareId: String,
    val userId: String,
    val title: String,
    val entries: List<WsDiskEntry>,
    val total: Int
)

class WsDiskApi(val config: WsDiskConfig) {

    val configLabel: String get() = config.label

    companion object {
        private const val CHARSET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_"
        private val random = SecureRandom()

        class WrongPwdException(message: String = "提取码不正确") : Exception(message)
        class NeedsPwdException(message: String = "该分享需要提取码") : Exception(message)

        val FEIJI = WsDiskApi(
            WsDiskConfig(
                hosts = listOf("api.feijipan.com"),
                recommendPath = "ws/recommend/list",
                folderListPath = "ws/share/list",
                redirectPath = "ws/file/redirect",
                aesKey = "dingHao-disk-app",
                origin = "https://www.feijix.com",
                label = "小飞机网盘"
            )
        )

        val ILANZOU = WsDiskApi(
            WsDiskConfig(
                hosts = listOf("apix.ilanzou.com", "api.ilanzou.com"),
                recommendPath = "unproved/recommend/list",
                folderListPath = "unproved/share/list",
                redirectPath = "unproved/file/redirect",
                aesKey = "lanZouY-disk-app",
                origin = "https://www.ilanzou.com",
                label = "蓝奏云优享版"
            )
        )
    }

    private fun randomUuid(): String {
        val sb = StringBuilder(21)
        repeat(21) { sb.append(CHARSET[random.nextInt(CHARSET.length)]) }
        return sb.toString()
    }

    private fun aesHex(plain: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(config.aesKey.toByteArray(Charsets.UTF_8), "AES"))
        return cipher.doFinal(plain.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SmartDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    @Volatile
    private var activeHostIdx = 0

    private fun httpUrl(host: String, path: String, params: Map<String, String>): okhttp3.HttpUrl {
        val url = okhttp3.HttpUrl.Builder().scheme("https").host(host)
            .addPathSegments(path).build()
        val b = url.newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private fun baseHeaders(): okhttp3.Headers.Builder {
        return okhttp3.Headers.Builder()
            .set("User-Agent", LanzouApi.USER_AGENT)
            .set("Accept", "application/json, text/plain, */*")
            .set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .set("Origin", config.origin)
            .set("Referer", "${config.origin}/")
    }

    private fun postWithFailover(path: String, params: Map<String, String>): String {
        var lastError: Exception = IllegalStateException("网络请求失败")
        for (attempt in config.hosts.indices) {
            val host = config.hosts[(activeHostIdx + attempt) % config.hosts.size]
            try {
                val req = Request.Builder().url(httpUrl(host, path, params))
                    .headers(baseHeaders().build())
                    .post(okhttp3.FormBody.Builder().build())
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        activeHostIdx = (activeHostIdx + attempt) % config.hosts.size
                        return resp.body?.string() ?: ""
                    }
                    lastError = IllegalStateException("网络请求失败（HTTP ${resp.code}）")
                }
            } catch (e: java.io.IOException) {
                lastError = e
            }
        }
        throw lastError
    }

    private fun parseEntry(
        f: JSONObject,
        shareFileIds: String,
        allowPwdProbe: Boolean
    ): WsDiskEntry? {
        val isDir = f.optInt("fileType", 1) == 2
        val id = if (isDir) f.optString("folderId") else f.optString("fileId")
        val name = when {
            isDir -> f.optString("folderName").ifBlank { f.optString("name") }
            else -> f.optString("fileName").ifBlank { f.optString("name") }
        }
        if (name.isBlank() && id.isBlank()) {
            if (allowPwdProbe) return null else throw NeedsPwdException()
        }
        if (isDir && id.isBlank()) return null
        val finalId = id.ifBlank { shareFileIds }
        if (finalId.isBlank()) return null
        return WsDiskEntry(
            id = finalId,
            name = name.ifBlank { "未知名称" },
            size = f.optLong("fileSize", 0L) * 1024L,
            isDir = isDir,
            entryUserId = f.optString("userId")
        )
    }

    suspend fun fetchShare(shareId: String, code: String?): WsDiskShareInfo = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "devType" to "6",
            "devModel" to "Chrome",
            "uuid" to randomUuid(),
            "extra" to "2",
            "timestamp" to aesHex(System.currentTimeMillis().toString()),
            "shareId" to shareId,
            "type" to "0",
            "offset" to "1",
            "limit" to "100"
        )
        code?.takeIf { it.isNotBlank() }?.let { params["code"] = it }

        val body = postWithFailover(config.recommendPath, params)
        if (body.isBlank()) throw IllegalStateException("获取分享信息失败，请检查网络")
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw IllegalStateException("分享信息响应异常")

        when (json.optInt("code", -1)) {
            200 -> {   }
            else -> {
                val msg = json.optString("msg")
                if (msg.contains("密码") || msg.contains("提取") || msg.contains("code")) {
                    throw if (code.isNullOrBlank()) NeedsPwdException(msg) else WrongPwdException(msg)
                }
                throw IllegalStateException(msg.ifBlank { "分享链接无效或已失效" })
            }
        }
        when (json.opt("status").toString()) {
            "-1" -> throw IllegalStateException("分享已取消或已失效")
            "2" -> {
                if (code.isNullOrBlank()) throw NeedsPwdException() else throw WrongPwdException()
            }
        }

        val list: JSONArray = json.optJSONArray("list") ?: JSONArray()
        if (list.length() == 0) {
            throw if (code.isNullOrBlank())
                NeedsPwdException("分享无内容：可能需要提取码（在链接旁输入后重试）或已失效")
            else WrongPwdException("提取码不正确或分享已失效")
        }
        val info = list.optJSONObject(0) ?: throw IllegalStateException("分享数据异常")
        val shareFileIds = info.optString("fileIds")
        var userId = info.optString("userId")
        if (userId.isBlank()) userId = info.optJSONObject("map")?.optString("userId").orEmpty()
        if (userId.isBlank()) userId = info.optString("shareId").ifBlank { shareId }

        val fileList = info.optJSONArray("fileList") ?: JSONArray()
        if (fileList.length() == 1) {
            val only = fileList.optJSONObject(0) ?: JSONObject()
            val noName = only.optString("fileName").isBlank() && only.optString("folderName").isBlank()
            val noId = only.optString("fileId").isBlank() && only.optString("folderId").isBlank()
            if (noName && noId) {
                if (code.isNullOrBlank()) throw NeedsPwdException() else throw WrongPwdException()
            }
        }
        val entries = ArrayList<WsDiskEntry>(fileList.length())
        var firstFileName = ""
        for (i in 0 until fileList.length()) {
            val f = fileList.optJSONObject(i) ?: continue
            val e = parseEntry(f, shareFileIds, allowPwdProbe = true) ?: continue
            if (firstFileName.isBlank()) firstFileName = e.name
            entries.add(e)
        }
        if (entries.isEmpty()) throw IllegalStateException("分享内容为空或链接已失效")
        WsDiskShareInfo(
            shareId = shareId,
            userId = userId,
            title = if (entries.size == 1) firstFileName else "${config.label}分享（${entries.size} 项）",
            entries = entries,
            total = json.optInt("total", entries.size)
        )
    }

    suspend fun fetchFolderFiles(
        shareId: String,
        folderId: String?,
        code: String?,
        maxPages: Int = 10
    ): List<WsDiskEntry> = withContext(Dispatchers.IO) {
        val out = ArrayList<WsDiskEntry>()
        val seen = HashSet<String>()
        var offset = 1
        val limit = 100
        var total = -1
        repeat(maxPages) {
            val params = linkedMapOf(
                "devType" to "6",
                "devModel" to "Chrome",
                "uuid" to randomUuid(),
                "extra" to "2",
                "timestamp" to aesHex(System.currentTimeMillis().toString()),
                "shareId" to shareId,
                "offset" to offset.toString(),
                "limit" to limit.toString()
            )
            folderId?.takeIf { it.isNotBlank() }?.let { params["folderId"] = it }
            code?.takeIf { it.isNotBlank() }?.let { params["code"] = it }

            val body = postWithFailover(config.folderListPath, params)
            if (body.isBlank()) return@withContext out
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext out
            if (json.optInt("code", -1) != 200) return@withContext out
            val pageList = json.optJSONArray("list") ?: return@withContext out
            if (total < 0) total = json.optInt("total", pageList.length())
            var added = 0
            for (i in 0 until pageList.length()) {
                val f = pageList.optJSONObject(i) ?: continue
                val e = parseEntry(f, "", allowPwdProbe = true) ?: continue
                if (!seen.add("${if (e.isDir) 'd' else 'f'}:${e.id}")) continue
                out.add(e)
                added++
            }
            if (pageList.length() == 0 || out.size >= total || added == 0) return@withContext out
            offset++
        }
        out
    }

    suspend fun fetchDirectLink(shareId: String, fileId: String, userPart: String): String =
        withContext(Dispatchers.IO) {
            val uuid = randomUuid()
            val now = System.currentTimeMillis().toString()
            val params = linkedMapOf(
                "downloadId" to aesHex("$fileId|$userPart"),
                "enable" to "1",
                "devType" to "6",
                "uuid" to uuid,
                "timestamp" to aesHex(now),
                "auth" to aesHex("$fileId|$now"),
                "shareId" to shareId
            )
            var lastError: Exception = IllegalStateException("获取直链失败")
            for (attempt in config.hosts.indices) {
                val host = config.hosts[(activeHostIdx + attempt) % config.hosts.size]
                try {
                    val req = Request.Builder()
                        .url(httpUrl(host, config.redirectPath, params))
                        .headers(baseHeaders().build())
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        when {
                            resp.isRedirect || resp.code in 301..308 -> {
                                val loc = resp.header("Location").orEmpty()
                                if (loc.isNotBlank()) {
                                    activeHostIdx = (activeHostIdx + attempt) % config.hosts.size
                                    return@withContext loc
                                }
                                lastError = IllegalStateException("直链返回为空，请稍后重试")
                            }
                            else -> lastError = IllegalStateException(
                                "获取直链失败（HTTP ${resp.code}），可能需要会员或链接已失效"
                            )
                        }
                    }
                } catch (e: java.io.IOException) {
                    lastError = e
                }
            }
            throw lastError
        }
}
