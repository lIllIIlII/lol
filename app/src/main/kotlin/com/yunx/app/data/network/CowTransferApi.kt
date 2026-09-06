/*
 * 吸析At - 奶牛快传（CowTransfer）分享解析 API。
 * 参考 Mikubill/cowtransfer-uploader 的公开接口：
 * 1. GET /api/transfer/transferdetail?url=<fid>&treceive=undefined&passcode=<pwd> → {guid, downloadName, deleted, uploaded}
 * 2. GET /api/transfer/files?page=<n>&guid=<guid> → {transferFileDtos:[{guid,fileName,size}], totalPages}
 * 3. POST /api/transfer/download?guid=<文件guid> → {link} 直链
 * 需携带 Referer https://cowtransfer.com/s/<fid> 与 cf-cs-k-20181214 cookie。
 */

package com.yunx.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CowEntry(
    val guid: String,
    val fileName: String,
    val sizeText: String
)

data class CowShareInfo(
    val guid: String,
    val title: String,
    val entries: List<CowEntry>
)

object CowTransferApi {

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private const val BASE = "https://cowtransfer.com"

    class WrongPwdException(message: String = "提取码不正确") : Exception(message)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun headers(referer: String): okhttp3.Headers.Builder {
        return okhttp3.Headers.Builder()
            .set("User-Agent", USER_AGENT)
            .set("Referer", referer)
            .set("Origin", BASE)
            .set("Accept", "application/json, text/plain, */*")
            .set("Accept-Language", "zh-CN,zh;q=0.9")
            .set("Cookie", "cf-cs-k-20181214=${System.currentTimeMillis() * 1000}")
    }

    private suspend fun httpGet(url: String, referer: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).headers(headers(referer).build()).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("网络请求失败（HTTP ${resp.code}）")
            resp.body?.string() ?: ""
        }
    }

    private suspend fun httpPost(url: String, referer: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .headers(headers(referer).build())
            .post(FormBody.Builder().build())
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("网络请求失败（HTTP ${resp.code}）")
            resp.body?.string() ?: ""
        }
    }

    fun sizeOf(text: String): Long {
        val m = Regex("""([0-9.]+)\s*([BKMG])""", RegexOption.IGNORE_CASE).find(text) ?: return 0L
        val num = m.groupValues[1].toDoubleOrNull() ?: return 0L
        return when (m.groupValues[2].uppercase()) {
            "B" -> num.toLong()
            "K" -> (num * 1024).toLong()
            "M" -> (num * 1024 * 1024).toLong()
            "G" -> (num * 1024 * 1024 * 1024).toLong()
            else -> 0L
        }
    }

    /** 解析分享：guid + 文件列表（自动分页） */
    suspend fun fetchShare(fileId: String, pwd: String?): CowShareInfo {
        val referer = "$BASE/s/$fileId"
        // 1. transferdetail
        val detailBody = httpGet(
            "$BASE/api/transfer/transferdetail?url=$fileId&treceive=undefined&passcode=${pwd.orEmpty()}",
            referer
        )
        if (detailBody.isBlank()) throw IllegalStateException("获取分享信息失败，请检查网络")
        val detail = runCatching { JSONObject(detailBody) }
            .getOrNull() ?: throw IllegalStateException("分享信息响应异常")
        val guid = detail.optString("guid")
        if (guid.isBlank()) {
            val msg = detail.optString("message")
            throw if (msg.contains("密码") || msg.contains("passcode")) {
                WrongPwdException(msg)
            } else IllegalStateException(msg.ifBlank { "分享链接无效或已失效" })
        }
        if (detail.optBoolean("deleted")) throw IllegalStateException("分享已被删除")
        if (!detail.optBoolean("uploaded")) throw IllegalStateException("分享尚未完成上传")
        val title = detail.optString("downloadName").ifBlank { "奶牛快传分享" }

        // 2. files 分页
        val entries = ArrayList<CowEntry>()
        var page = 0
        var totalPages = 1
        while (page < totalPages && page < 50) {
            val filesBody = httpGet("$BASE/api/transfer/files?page=$page&guid=$guid", referer)
            val files = runCatching { JSONObject(filesBody) }.getOrNull()
                ?: throw IllegalStateException("文件列表响应异常")
            totalPages = files.optInt("totalPages", 1).coerceAtLeast(1)
            val arr: JSONArray = files.optJSONArray("transferFileDtos") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val fguid = f.optString("guid")
                if (fguid.isBlank()) continue
                entries.add(CowEntry(guid = fguid, fileName = f.optString("fileName"), sizeText = f.optString("size")))
            }
            page++
            if (arr.length() == 0) break
        }
        return CowShareInfo(guid = guid, title = title, entries = entries)
    }

    /** 单文件直链 */
    suspend fun fetchDirectLink(fileId: String, fileGuid: String): String {
        val referer = "$BASE/s/$fileId"
        val body = httpPost("$BASE/api/transfer/download?guid=$fileGuid", referer)
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: throw IllegalStateException("直链响应异常")
        val link = json.optString("link")
        if (link.isBlank()) throw IllegalStateException(json.optString("message").ifBlank { "获取直链失败" })
        return link
    }
}
