/*
 * 吸析At - 小飞机网盘（feijipan）分享解析 API。
 * 参考 markcxx/feijipan-api（开源 MIT）：
 * - AES-128-ECB 密钥 "dingHao-disk-app"（PKCS5），timestamp/auth/downloadId 均为 hex(AES(明文))；
 * - POST api.feijipan.com/ws/recommend/list（shareId、code）→ {list:[{fileIds,userId,fileList:[{fileName,fileSize,fileType}]}]}
 * - GET api.feijipan.com/ws/file/redirect?downloadId=hex(fileId|userId)&auth=hex(fileId|ms)… → 302 Location 直链。
 */

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

data class FeijiEntry(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val isDir: Boolean
)

data class FeijiShareInfo(
    val shareId: String,
    val userId: String,
    val title: String,
    val entries: List<FeijiEntry>
)

object FeijiApi {

    private const val API_BASE = "https://api.feijipan.com/ws/"
    private const val AES_KEY = "dingHao-disk-app" // 16 字节
    private const val CHARSET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_"

    private val random = SecureRandom()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    class WrongPwdException(message: String = "提取码不正确") : Exception(message)

    // ---------- AES-ECB / 工具 ----------

    private fun aesHex(plain: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(AES_KEY.toByteArray(Charsets.UTF_8), "AES"))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return encrypted.joinToString("") { "%02x".format(it) }
    }

    private fun randomUuid(): String {
        val sb = StringBuilder(21)
        repeat(21) { sb.append(CHARSET[random.nextInt(CHARSET.length)]) }
        return sb.toString()
    }

    private fun httpUrlBuilder(path: String, params: Map<String, String>): okhttp3.HttpUrl {
        val url = okhttp3.HttpUrl.Builder().scheme("https").host("api.feijipan.com")
            .addPathSegments("ws/$path").build()
        val b = url.newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build()
    }

    private fun baseHeaders(): okhttp3.Headers.Builder {
        return okhttp3.Headers.Builder()
            .set("User-Agent", LanzouApi.USER_AGENT)
            .set("Accept", "application/json, text/plain, */*")
            .set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .set("Origin", "https://www.feijix.com")
            .set("Referer", "https://www.feijix.com/")
    }

    // ---------- 解析 ----------

    /** 拉取分享信息（文件列表） */
    suspend fun fetchShare(shareId: String, code: String?): FeijiShareInfo = withContext(Dispatchers.IO) {
        val uuid = randomUuid()
        val ts = aesHex(System.currentTimeMillis().toString())
        val params = linkedMapOf(
            "devType" to "6",
            "devModel" to "Chrome",
            "uuid" to uuid,
            "extra" to "2",
            "timestamp" to ts,
            "shareId" to shareId,
            "type" to "0",
            "offset" to "1",
            "limit" to "100"
        )
        code?.takeIf { it.isNotBlank() }?.let { params["code"] = it }

        val req = Request.Builder().url(httpUrlBuilder("recommend/list", params)).headers(baseHeaders().build())
            .post(okhttp3.FormBody.Builder().build())
            .build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("网络请求失败（HTTP ${resp.code}）")
            resp.body?.string() ?: ""
        }
        if (body.isBlank()) throw IllegalStateException("获取分享信息失败，请检查网络")
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw IllegalStateException("分享信息响应异常")
        if (json.optInt("code", -1) != 200) {
            val msg = json.optString("msg")
            throw if (msg.contains("密码") || msg.contains("提取") || msg.contains("code")) {
                WrongPwdException(msg.ifBlank { "提取码不正确" })
            } else IllegalStateException(msg.ifBlank { "分享链接无效或已失效" })
        }
        val list: JSONArray = json.optJSONArray("list") ?: JSONArray()
        if (list.length() == 0) throw IllegalStateException("分享内容为空或链接已失效")
        val info = list.optJSONObject(0) ?: throw IllegalStateException("分享数据异常")
        val fileIds = info.optString("fileIds")
        var userId = info.optString("userId")
        if (userId.isBlank()) {
            val map = info.optJSONObject("map")
            userId = map?.optString("userId").orEmpty()
        }
        if (userId.isBlank()) userId = info.optString("shareId").ifBlank { shareId }
        if (fileIds.isBlank()) throw IllegalStateException("未获取到文件 ID")

        val fileList = info.optJSONArray("fileList") ?: JSONArray()
        val entries = ArrayList<FeijiEntry>()
        var firstFileName = ""
        for (i in 0 until fileList.length()) {
            val f = fileList.optJSONObject(i) ?: continue
            val name = f.optString("fileName").ifBlank { "未知文件" }
            if (firstFileName.isBlank()) firstFileName = name
            entries.add(
                FeijiEntry(
                    fileId = fileIds,
                    fileName = name,
                    fileSize = f.optLong("fileSize", 0L),
                    isDir = f.optInt("fileType", 1) == 2
                )
            )
        }
        if (entries.isEmpty()) throw IllegalStateException("文件列表为空")
        FeijiShareInfo(
            shareId = shareId,
            userId = userId,
            title = if (entries.size == 1) firstFileName else "小飞机网盘分享（${entries.size} 个文件）",
            entries = entries
        )
    }

    /** 获取单文件直链（302 Location） */
    suspend fun fetchDirectLink(shareId: String, fileId: String, userId: String): String = withContext(Dispatchers.IO) {
        val uuid = randomUuid()
        val now = System.currentTimeMillis().toString()
        val params = linkedMapOf(
            "downloadId" to aesHex("$fileId|$userId"),
            "enable" to "1",
            "devType" to "6",
            "uuid" to uuid,
            "timestamp" to aesHex(now),
            "auth" to aesHex("$fileId|$now"),
            "shareId" to shareId
        )
        val req = Request.Builder().url(httpUrlBuilder("file/redirect", params)).headers(baseHeaders().build()).get().build()
        client.newCall(req).execute().use { resp ->
            when {
                resp.isRedirect || resp.code in 301..308 -> {
                    val loc = resp.header("Location").orEmpty()
                    if (loc.isBlank()) throw IllegalStateException("直链返回为空，请稍后重试")
                    loc
                }
                else -> throw IllegalStateException("获取直链失败（HTTP ${resp.code}），可能需要会员或链接已失效")
            }
        }
    }
}
