package com.yunx.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CtfileFileInfo(
    val fileName: String,
    val fileSize: Long,
    val isVip: Boolean,
    val userid: String,
    val fileId: String,
    val fileChk: String,
    val startTime: Long,
    val waitSeconds: Long,
    val verifycode: String,
    val vipUrls: Map<String, String> = emptyMap()
)

object CtfileApi {

    private const val WEBAPI = "https://webapi.ctfile.com"

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    class NeedsPwdException(message: String = "该分享需要访问密码") : Exception(message)
    class WrongPwdException(message: String = "访问密码不正确") : Exception(message)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SmartDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun rand(): String = Math.random().toString().removePrefix("0.")

    private fun headers(ref: String): okhttp3.Headers {
        val origin = Regex("^https?://[^/]+").find(ref)?.value ?: "https://www.ctfile.com"
        return okhttp3.Headers.Builder()
            .set("User-Agent", USER_AGENT)
            .set("Referer", "$origin/")
            .set("Origin", origin)
            .set("Accept", "application/json, text/plain, */*")
            .set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
    }

    private suspend fun httpGet(url: String, ref: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).headers(headers(ref)).get().build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("城通接口请求失败（HTTP ${resp.code}）")
                resp.body?.string() ?: ""
            }
        }.getOrElse { e ->
            throw when (e) {
                is java.net.UnknownHostException -> IllegalStateException("域名解析失败（webapi.ctfile.com），请切换网络后重试")
                is java.net.SocketTimeoutException, is java.net.ConnectException ->
                    IllegalStateException("连接城通服务器超时，请切换 Wi-Fi/流量后重试")
                is IllegalStateException -> e
                else -> IllegalStateException("网络异常：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    suspend fun fetchFileInfo(fileid: String, pwd: String?, refHost: String): CtfileFileInfo {
        val pathKind = if (fileid.count { it == '-' } >= 2) "f" else "file"
        val ref = "https://$refHost"
        val url = "$WEBAPI/getfile.php?path=$pathKind&f=$fileid" +
            "&passcode=${pwd.orEmpty()}&token=&r=${rand()}&ref=$ref&url=$ref"
        val body = httpGet(url, ref)
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw IllegalStateException("城通接口响应异常，请重试")
        val f = json.optJSONObject("file") ?: JSONObject()
        when (json.optInt("code", -1)) {
            200 -> {   }
            423 -> {
                val msg = f.optString("message")
                throw if (pwd.isNullOrBlank()) NeedsPwdException(msg.ifBlank { "该分享需要访问密码" })
                else WrongPwdException(msg.ifBlank { "访问密码不正确" })
            }
            404 -> throw IllegalStateException(f.optString("message").ifBlank { "分享不存在或已过期" })
            else -> throw IllegalStateException(f.optString("message").ifBlank { "分享链接无效或已失效" })
        }
        val isVip = f.optInt("is_vip") == 1
        val fileId = f.optString("file_id")
        val vipUrls = buildMap {
            listOf("vip_dx_url", "vip_yd_url", "vip_lt_url", "us_downurl_a").forEach { k ->
                f.optString(k).takeIf { it.isNotBlank() }?.let { put(k, it) }
            }
        }
        if (!isVip && fileId.isBlank()) throw IllegalStateException("分享数据异常，请稍后重试")
        return CtfileFileInfo(
            fileName = f.optString("file_name").ifBlank { fileid },
            fileSize = parseSizeText(f.optString("file_size")),
            isVip = isVip,
            userid = f.optString("userid"),
            fileId = fileId,
            fileChk = f.optString("file_chk"),
            startTime = f.optLong("start_time", 0L),
            waitSeconds = f.optLong("wait_seconds", 0L),
            verifycode = f.optString("verifycode"),
            vipUrls = vipUrls
        )
    }

    suspend fun fetchDirectLink(info: CtfileFileInfo): String {
        if (info.isVip) {
            val url = info.vipUrls.values.firstOrNull { it.isNotBlank() }
            if (!url.isNullOrBlank()) return url
            throw IllegalStateException("该文件需要城通会员，当前无可用直链，请在浏览器登录后下载")
        }
        val url = "$WEBAPI/get_file_url.php?uid=${info.userid}&fid=${info.fileId}" +
            "&file_chk=${info.fileChk}&start_time=${info.startTime}&wait_seconds=${info.waitSeconds}" +
            "&app=0&acheck=2&verifycode=${info.verifycode}&rd=${rand()}"
        val body = httpGet(url, "https://www.ctfile.com")
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw IllegalStateException("城通下载接口响应异常，请重试")
        val code = json.optInt("code", -1)
        if (code == 200 || code == 302) {
            val du = json.optString("downurl").ifBlank { json.optString("url") }
            if (du.isBlank() || du == "0") {
                throw IllegalStateException(json.optString("message").ifBlank { "直链获取失败（可能需登录或等待冷却），请稍后重试" })
            }
            return du
        }
        throw IllegalStateException(json.optString("message").ifBlank { "获取下载地址失败（HTTP code=$code）" })
    }

    fun parseSizeText(text: String): Long {
        val m = Regex("""([0-9.]+)\s*(B|KB|MB|GB|TB)""", RegexOption.IGNORE_CASE).find(text) ?: return 0L
        val num = m.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (m.groupValues[2].uppercase()) {
            "B" -> num.toLong()
            "KB" -> (num * 1024).toLong()
            "MB" -> (num * 1024 * 1024).toLong()
            "GB" -> (num * 1024 * 1024 * 1024).toLong()
            "TB" -> (num * 1024L * 1024 * 1024 * 1024).toLong()
            else -> 0L
        }
    }
}
