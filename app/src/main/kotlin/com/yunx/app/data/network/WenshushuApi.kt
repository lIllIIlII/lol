/*
 * 吸析At - 文叔叔（wenshushu.cn）分享解析 API。
 *
 * 协议（由 fundrive/wssf 等开源实现 + 官网前端 app.js 交叉还原，沙箱实测验证）：
 * 1) POST /ap/login/anonymous {"dev_info":"{}"} → {code:0, data:{token}} → 全局 X-TOKEN 头
 * 2) 分享尾段 16 位 → POST /ap/task/token {"token":<code>} → tid；其他长度（11/12 位）即 tid
 * 3) POST /ap/task/mgrtask {"tid":<tid>,"password":<pwd>}
 *    → {code:0, data:{boxid(bid), ufileid(pid 根), file_size, file_count, ...}}
 *    - code 1013/TR_TP_ERR30：分享已失效；密码错误为其他 code/message
 * 4) POST /ap/ufile/nlist {"start":0,"sort":{"name":"asc"},"bid":<bid>,"pid":<pid>,
 *    "type":1,"options":{"uploader":"true"},"size":50}
 *    → {data:{fileList:[{fid, fname, type, size}]}}（type 2=文件夹 1=文件；start 步进翻页）
 * 5) POST /ap/dl/sign {"consumeCode":0,"type":1,"ufileid":<fid>} → {data:{url}} 直链
 *    - url 空且 ttNeed!=0 → 分享流量不足
 *
 * 注：/box/ 资源包链接匿名接口不可达（TR_E_NOFOLDER），仅支持 /f/ 文件传输分享。
 */

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

/** 文叔叔条目（文件或文件夹） */
data class WssEntry(
    val fid: String,
    val fname: String,
    val size: Long,
    val isDir: Boolean
)

/** 分享任务信息 */
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

    // ---------- 匿名登录（token 缓存 + 并发互斥） ----------

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
            tokenExpiresAt = System.currentTimeMillis() + 30 * 60_000L // 30 分钟有效，到期自动重登
            token
        }
        return fresh
    }

    // ---------- HTTP ----------

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

    /** 带 token 的 POST：401/token 失效时自动重登一次 */
    private suspend fun postJsonWithToken(url: String, jsonBody: String): JSONObject {
        var token = ensureToken()
        var json = runCatching { JSONObject(postJson(url, jsonBody, token)) }.getOrNull()
        // token 失效（非 0 且提示重新登录）→ 强制重登一次
        val msg = json?.optString("message").orEmpty()
        if (json != null && json.optInt("code", -1) != 0 && (msg.contains("登录", ignoreCase = true) || msg.contains("token", ignoreCase = true))) {
            token = ensureToken(force = true)
            json = runCatching { JSONObject(postJson(url, jsonBody, token)) }.getOrNull()
        }
        return json ?: throw IllegalStateException("文叔叔接口响应异常，请重试")
    }

    // ---------- 解析流程 ----------

    /**
     * 解析分享任务（16 位 token 自动换 tid）。
     * @param code 链接尾段（11/12 位 tid 或 16 位 token）
     */
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

    /**
     * 列出目录内容（自动翻页，防大目录截断；同页 fid 去重防服务端翻页异常）。
     * @param pid 目录 fid（空/根 = 任务的 ufileid）
     */
    suspend fun listFiles(bid: String, pid: String): List<WssEntry> = withContext(Dispatchers.IO) {
        val out = ArrayList<WssEntry>()
        val seen = HashSet<String>()
        var start = 0
        val size = 50
        var rounds = 0
        while (rounds < 40) { // 最多 2000 条
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
            if (added == 0) break // 翻页异常防护：全是重复条目即停止
            start += fl.length()
            if (fl.length() < size) break
            rounds++
        }
        out
    }

    /** 获取单文件直链（dl/sign） */
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
