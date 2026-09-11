package com.yunx.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WssEntry(
    val fid: String,
    val fname: String,
    val size: Long,
    val isDir: Boolean
)

data class WssTaskInfo(
    val tid: String,
    val bid: String,
    val rootPid: String,
    val fileSize: Long,
    val fileCount: Int
)

object WenshushuApi {

    private const val BASE = "https://www.wenshushu.cn"

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:82.0) Gecko/20100101 Firefox/82.0"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    class NeedsPwdException(message: String = "该分享需要密码") : Exception(message)
    class WrongPwdException(message: String = "密码不正确") : Exception(message)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SmartDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cachedToken: String? = null
    private var tokenExpiresAt = 0L
    private val tokenMutex = Mutex()

    private suspend fun ensureToken(force: Boolean = false): String {
        if (!force) {
            cachedToken?.takeIf { System.currentTimeMillis() < tokenExpiresAt }?.let { return it }
        }
        val fresh = tokenMutex.withLock {
            if (!force) {
                val cached = cachedToken
                if (cached != null && System.currentTimeMillis() < tokenExpiresAt) {
                    return@withLock cached
                }
            }
            val body = postJson("$BASE/ap/login/anonymous", "{\"dev_info\":\"{}\"}")
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw IllegalStateException("文叔叔登录接口响应异常，请重试")
            if (json.optInt("code", -1) != 0) {
                throw IllegalStateException(json.optString("message").ifBlank { "文叔叔匿名登录失败" })
            }
            val token = json.optJSONObject("data")?.optString("token").orEmpty()
            if (token.isBlank()) throw IllegalStateException("文叔叔匿名登录失败（无 token）")
            cachedToken = token
            tokenExpiresAt = System.currentTimeMillis() + 30 * 60_000L
            token
        }
        return fresh
    }

    private suspend fun postJson(url: String, jsonBody: String, token: String? = null): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE/")
                .header("Origin", BASE)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8")
                .apply { token?.let { header("X-TOKEN", it) } }
                .post(jsonBody.toRequestBody(JSON_MEDIA))
                .build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("文叔叔接口请求失败（HTTP ${resp.code}）")
                    resp.body?.string() ?: ""
                }
            }.getOrElse { e ->
                throw when (e) {
                    is java.net.UnknownHostException -> IllegalStateException("域名解析失败（wenshushu.cn），请切换网络后重试")
                    is java.net.SocketTimeoutException, is java.net.ConnectException ->
                        IllegalStateException("连接文叔叔服务器超时，请切换 Wi-Fi/流量后重试")
                    is IllegalStateException -> e
                    else -> IllegalStateException("网络异常：${e.message ?: e.javaClass.simpleName}")
                }
            }
        }

    private suspend fun postJsonWithToken(url: String, jsonBody: String): JSONObject {
        var token = ensureToken()
        var json = runCatching { JSONObject(postJson(url, jsonBody, token)) }.getOrNull()
        val msg = json?.optString("message").orEmpty()
        if (json != null && json.optInt("code", -1) != 0 && (msg.contains("登录", ignoreCase = true) || msg.contains("token", ignoreCase = true))) {
            token = ensureToken(force = true)
            json = runCatching { JSONObject(postJson(url, jsonBody, token)) }.getOrNull()
        }
        return json ?: throw IllegalStateException("文叔叔接口响应异常，请重试")
    }

    suspend fun fetchTaskInfo(code: String, pwd: String?): WssTaskInfo {
        val tid = if (code.length == 16) {
            val body = postJsonWithToken("$BASE/ap/task/token", "{\"token\":\"$code\"}")
            if (body.optInt("code", -1) != 0) {
                throw mapTaskError(body, code)
            }
            body.optJSONObject("data")?.optString("tid").orEmpty()
        } else code
        if (tid.isBlank()) throw IllegalStateException("分享链接无效")

        val json = postJsonWithToken("$BASE/ap/task/mgrtask", "{\"tid\":\"$tid\",\"password\":\"${pwd.orEmpty()}\"}")
        if (json.optInt("code", -1) != 0) throw mapTaskError(json, tid)
        val d = json.optJSONObject("data") ?: throw IllegalStateException("分享数据异常")
        val bid = d.optString("boxid")
        val pid = d.optString("ufileid")
        if (bid.isBlank() || pid.isBlank()) throw IllegalStateException("分享数据异常（缺少目录信息）")
        return WssTaskInfo(
            tid = tid,
            bid = bid,
            rootPid = pid,
            fileSize = d.optLong("file_size", 0L),
            fileCount = d.optInt("file_count", 0)
        )
    }

    private fun mapTaskError(json: JSONObject, id: String): Exception {
        val code = json.optInt("code", -1)
        val msg = json.optString("message")
        return when {
            code == 1013 || msg.contains("TR_TP_ERR") ->
                IllegalStateException("分享已失效或已被删除")
            msg.contains("密码") || msg.contains("PASSWD", ignoreCase = true) ->
                if (msg.contains("不正确") || msg.contains("错误"))
                    WrongPwdException(msg) else NeedsPwdException(msg.ifBlank { "该分享需要密码" })
            else -> IllegalStateException(msg.ifBlank { "分享无效（$id）" })
        }
    }

    suspend fun listFiles(bid: String, pid: String): List<WssEntry> = withContext(Dispatchers.IO) {
        val out = ArrayList<WssEntry>()
        val seen = HashSet<String>()
        var start = 0
        val size = 50
        var rounds = 0
        while (rounds < 40) {
            val payload = "{\"start\":$start,\"sort\":{\"name\":\"asc\"}," +
                "\"bid\":\"$bid\",\"pid\":\"$pid\",\"type\":1," +
                "\"options\":{\"uploader\":\"true\"},\"size\":$size}"
            val json = postJsonWithToken("$BASE/ap/ufile/nlist", payload)
            if (json.optInt("code", -1) != 0) {
                if (out.isEmpty()) throw IllegalStateException(json.optString("message").ifBlank { "获取文件列表失败" })
                break
            }
            val fl = json.optJSONObject("data")?.optJSONArray("fileList") ?: break
            if (fl.length() == 0) break
            var added = 0
            for (i in 0 until fl.length()) {
                val f = fl.optJSONObject(i) ?: continue
                val fid = f.optString("fid")
                val name = f.optString("fname")
                if (fid.isBlank() || name.isBlank()) continue
                if (!seen.add("${if (f.optString("type") == "2") 'd' else 'f'}:$fid")) continue
                out.add(
                    WssEntry(
                        fid = fid,
                        fname = name,
                        size = f.optLong("size", 0L),
                        isDir = f.optString("type") == "2"
                    )
                )
                added++
            }
            if (added == 0) break
            start += fl.length()
            if (fl.length() < size) break
            rounds++
        }
        out
    }

    suspend fun fetchDirectLink(fid: String): String {
        val json = postJsonWithToken("$BASE/ap/dl/sign", "{\"consumeCode\":0,\"type\":1,\"ufileid\":\"$fid\"}")
        val d = json.optJSONObject("data")
        val url = d?.optString("url").orEmpty()
        if (url.isBlank()) {
            val ttNeed = d?.optLong("ttNeed", 0L) ?: 0L
            throw IllegalStateException(
                if (ttNeed != 0L) "对方分享流量不足，无法获取直链" else json.optString("message").ifBlank { "获取直链失败，请稍后重试" }
            )
        }
        return url
    }
}
