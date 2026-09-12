package com.yunx.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class Cloud189Entry(
    val id: String,
    val parentId: String,
    val name: String,
    val size: Long,
    val isDir: Boolean,
    val md5: String = ""
)

data class Cloud189ShareInfo(
    val shareId: String,
    val shareKey: String,
    val userId: String,
    val title: String,
    val entries: List<Cloud189Entry>,
    val needsPwd: Boolean
)

class Cloud189WrongPwdException(msg: String = "提取码不正确") : Exception(msg)
class Cloud189NeedsPwdException(msg: String = "该分享需要提取码") : Exception(msg)

object Cloud189Api {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private const val SHARE_BASE = "https://cloud.189.cn"
    private const val API_BASE = "https://api.cloud.189.cn"
    private const val PC_BASE = "https://pc.189.cn"

    private val random = SecureRandom()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SmartDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val noRedirectClient: OkHttpClient by lazy {
        client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    private fun randomHex(len: Int): String {
        val sb = StringBuilder(len)
        val chars = "0123456789ABCDEF"
        repeat(len) { sb.append(chars[random.nextInt(chars.length)]) }
        return sb.toString()
    }

    private fun headers(referer: String, cookie: String? = null): okhttp3.Headers.Builder {
        val b = okhttp3.Headers.Builder()
            .set("User-Agent", USER_AGENT)
            .set("Accept", "application/json, text/plain, */*")
            .set("Accept-Language", "zh-CN,zh;q=0.9")
            .set("Referer", referer)
        if (!cookie.isNullOrBlank()) b.set("Cookie", cookie)
        return b
    }

    suspend fun fetchShare(shareKey: String, pwd: String?, accountCookie: String?): Cloud189ShareInfo = withContext(Dispatchers.IO) {
        val pageUrl = "$SHARE_BASE/t/$shareKey"
        val initReq = Request.Builder().url(pageUrl)
            .headers(headers(SHARE_BASE, accountCookie).build())
            .get().build()
        val initBody = client.newCall(initReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("天翼云盘页面加载失败（HTTP ${resp.code}）")
            resp.body?.string() ?: ""
        }
        if (initBody.contains("分享已删除") || initBody.contains("分享不存在")) {
            throw IllegalStateException("分享已删除或不存在")
        }
        val shareId = Regex("""shareId\s*=\s*['"](\d+)['"]""").find(initBody)?.groupValues?.getOrNull(1)
            ?: Regex("""["']shareId["']\s*[:=]\s*['"]?(\d+)['"]?""").find(initBody)?.groupValues?.getOrNull(1)
            ?: throw IllegalStateException("无法获取分享 ID")
        val userId = Regex("""["']userId["']\s*[:=]\s*['"]?(\d+)['"]?""").find(initBody)?.groupValues?.getOrNull(1).orEmpty()

        val needsPwd = initBody.contains("sharePwd") || initBody.contains("inputPwd") || initBody.contains("passwordVerify")

        val listParams = linkedMapOf(
            "shareId" to shareId,
            "sharePwd" to (pwd.orEmpty()),
            "fileId" to "",
            "orderBy" to "lastOpTime",
            "order" to "desc",
            "page" to "1",
            "pageSize" to "200",
            "verifyCode" to ""
        )
        val listUrl = buildUrl("$API_BASE/personal/share/listShareDir.action", listParams)
        val listReq = Request.Builder().url(listUrl)
            .headers(headers(pageUrl, accountCookie).build())
            .get().build()
        val listBody = client.newCall(listReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("天翼云盘列表加载失败（HTTP ${resp.code}）")
            resp.body?.string() ?: ""
        }
        val json = runCatching { JSONObject(listBody) }.getOrNull()
            ?: throw IllegalStateException("天翼云盘列表响应异常")
        if (json.optInt("errorCode", -1) != 0 && json.optInt("res_code", -1) != 0) {
            val msg = json.optString("errorMsg").ifBlank { json.optString("errMsg") }
            if (msg.contains("密码") || msg.contains("提取")) {
                if (pwd.isNullOrBlank()) throw Cloud189NeedsPwdException(msg)
                throw Cloud189WrongPwdException(msg)
            }
            throw IllegalStateException(msg.ifBlank { "获取分享列表失败" })
        }
        val fileList = json.optJSONArray("fileListAO")?.optJSONObject(0)?.optJSONArray("fileList")
            ?: json.optJSONArray("fileList")
            ?: JSONObject().apply { put("fileList", JSONObject()) }.optJSONArray("fileList")
        val folderList = json.optJSONArray("fileListAO")?.optJSONObject(0)?.optJSONArray("folderList")
            ?: json.optJSONArray("folderList")
        val entries = ArrayList<Cloud189Entry>()
        folderList?.let { fl ->
            for (i in 0 until fl.length()) {
                val f = fl.optJSONObject(i) ?: continue
                val id = f.optString("id").ifBlank { f.optString("folderId") }
                val name = f.optString("name").ifBlank { f.optString("folderName") }
                if (id.isNotBlank() && name.isNotBlank()) {
                    entries.add(Cloud189Entry(id = id, parentId = "0", name = name, size = 0L, isDir = true))
                }
            }
        }
        fileList?.let { fl ->
            for (i in 0 until fl.length()) {
                val f = fl.optJSONObject(i) ?: continue
                val id = f.optString("id").ifBlank { f.optString("fileId") }
                val name = f.optString("name").ifBlank { f.optString("fileName") }
                val size = f.optLong("size", 0L)
                val md5 = f.optString("md5").orEmpty()
                if (id.isNotBlank() && name.isNotBlank()) {
                    entries.add(Cloud189Entry(id = id, parentId = "0", name = name, size = size, isDir = false, md5 = md5))
                }
            }
        }
        if (entries.isEmpty() && needsPwd && pwd.isNullOrBlank()) throw Cloud189NeedsPwdException()
        Cloud189ShareInfo(
            shareId = shareId,
            shareKey = shareKey,
            userId = userId,
            title = if (entries.size == 1) entries[0].name else "天翼云盘分享（${entries.size} 项）",
            entries = entries,
            needsPwd = needsPwd
        )
    }

    suspend fun fetchDirectLink(shareId: String, shareKey: String, fileId: String, pwd: String?, accountCookie: String?): String = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "shareId" to shareId,
            "sharePwd" to (pwd.orEmpty()),
            "fileId" to fileId,
            "type" to "1",
            "verifyCode" to ""
        )
        val url = buildUrl("$API_BASE/personal/share/getShareFileScripDownloadUrl.action", params)
        val req = Request.Builder().url(url)
            .headers(headers("$SHARE_BASE/t/$shareKey", accountCookie).build())
            .get().build()
        val resp = noRedirectClient.newCall(req).execute()
        val location = resp.use { it.header("Location") }
        if (location != null) return@withContext location
        val body = resp.body?.string() ?: ""
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw IllegalStateException("天翼云盘直链响应异常")
        val fileUrl = json.optString("fileDownloadUrl").ifBlank { json.optString("downloadUrl") }
        if (fileUrl.isBlank()) {
            val msg = json.optString("errorMsg").ifBlank { json.optString("errMsg") }
            if (msg.contains("密码")) throw Cloud189WrongPwdException(msg)
            throw IllegalStateException(msg.ifBlank { "获取直链失败" })
        }
        fileUrl
    }

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val urlBuilder = okhttp3.HttpUrl.Builder().scheme("https").host("api.cloud.189.cn")
        if (base.contains("cloud.189.cn")) urlBuilder.host("cloud.189.cn")
        if (base.contains("pc.189.cn")) urlBuilder.host("pc.189.cn")
        val path = base.substringAfter("://").substringAfter('/', "")
        if (path.isNotBlank()) {
            urlBuilder.addPathSegments(path.trimStart('/'))
        }
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        return urlBuilder.build().toString()
    }
}
