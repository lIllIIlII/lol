/*
 * 吸析At - 蓝奏云分享解析 API。
 * 流程参考 alist-org/alist lanzou 驱动（AGPL-3.0）并针对新版页面适配：
 * - acw_sc__v2 反爬挑战：arg1 → Unbox 重排 → HexXor 固定 key → cookie 重试；
 * - 带密码单文件：页面 down_p() → POST /ajaxfile.php|/ajaxm.php {action,sign,kd,p} → {dom,url} → dom/file/url → 302 直链；
 * - 带密码文件夹：file() → POST /filemoreajax.php {lx,fid,uid,puid,pg,...,pwd} → text[] 条目 + 分页；
 * - 无密码文件：iframe(src 或 srcdoc) 页内取 ajax 参数 → 同上 POST。
 */

package com.yunx.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 蓝奏云解析结果条目（文件夹或文件） */
data class LanzouEntry(
    val id: String,
    val name: String,
    val sizeText: String,
    val timeText: String,
    val isDir: Boolean
)

/** 页面解析结果 */
data class LanzouPageResult(
    val baseUrl: String,
    val shareId: String,
    val isFolder: Boolean,
    val title: String,
    val entries: List<LanzouEntry>,
    val sizeText: String = "",
    val timeText: String = "",
    val needsPwd: Boolean = false
)

object LanzouApi {

    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private const val ACW_XOR_KEY = "3000176000856006061501533003690027800375"
    private val ACW_BOX = intArrayOf(
        6, 28, 34, 31, 33, 18, 30, 23, 9, 8, 19, 38, 17, 24, 0, 5, 32, 21, 10, 22, 25,
        14, 15, 3, 16, 27, 13, 35, 2, 29, 11, 26, 4, 36, 1, 39, 37, 7, 20, 12
    )

    /** 提取码不正确（需要用户在输入框填写） */
    class WrongPwdException(message: String = "提取码不正确") : Exception(message)
    class NeedsPwdException(message: String = "该分享需要提取码") : Exception(message)

    private val acwRegex = Regex("""arg1='([0-9A-Z]+)'""")

    // ---------- 客户端 ----------

    private val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 蓝奏云家族域名在部分运营商网络被 DNS 污染（系统解析返回假 IP → 连接超时 →
            // 应用报「网络错误」但其他网盘正常）：DoH 真实 IP 优先 + 系统 DNS 兜底
            .dns(SmartDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 不跟随重定向（直链 302 Location 探测专用） */
    private val noRedirectClient: OkHttpClient by lazy {
        apiClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    // ---------- 反爬挑战 ----------

    private fun calcAcwScV2(arg1: String): String? {
        return runCatching {
            val n = CharArray(arg1.length)
            for (i in ACW_BOX.indices) {
                val j = ACW_BOX[i]
                if (j < n.size) n[j] = arg1[i]
            }
            val unboxed = String(n)
            val out = StringBuilder()
            var i = 0
            while (i + 2 <= unboxed.length && i + 2 <= ACW_XOR_KEY.length) {
                val v1 = unboxed.substring(i, i + 2).toInt(16)
                val v2 = ACW_XOR_KEY.substring(i, i + 2).toInt(16)
                out.append("%02x".format(v1 xor v2))
                i += 2
            }
            out.toString()
        }.getOrNull()
    }

    // ---------- 基础请求 ----------

    private fun baseHeaders(referer: String, acw: String? = null, accountCookie: String? = null): okhttp3.Headers.Builder {
        val b = okhttp3.Headers.Builder()
            .set("User-Agent", USER_AGENT)
            .set("Referer", referer)
            .set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        val cookies = buildList {
            accountCookie?.takeIf { it.isNotBlank() }?.let { add(it.trim().trimEnd(';')) }
            acw?.let { add("acw_sc__v2=$it") }
        }
        if (cookies.isNotEmpty()) b.set("Cookie", cookies.joinToString("; "))
        return b
    }

    /** GET 页面：自动解算 acw_sc__v2 挑战并重试（最多 2 轮）；网络异常转译为可读提示 */
    private suspend fun getHtml(url: String, referer: String, accountCookie: String?): String = withContext(Dispatchers.IO) {
        var acw: String? = null
        repeat(3) {
            val req = Request.Builder().url(url).headers(baseHeaders(referer, acw, accountCookie).build()).get().build()
            val body = runCatching {
                apiClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext ""
                    resp.body?.string() ?: ""
                }
            }.getOrElse { e -> throw translateNetworkError(e, url) }
            val challenge = acwRegex.find(body)?.groupValues?.getOrNull(1)
            if (challenge == null || acw != null) return@withContext body
            acw = calcAcwScV2(challenge) ?: return@withContext body
        }
        ""
    }

    /** 网络异常 → 用户可读提示（区分 DNS 污染 / 连接阻断，指导切换网络） */
    private fun translateNetworkError(e: Throwable, url: String): IllegalStateException {
        val host = runCatching { url.toHttpUrlOrNull()?.host ?: url }.getOrDefault(url)
        return when {
            e is java.net.UnknownHostException ->
                IllegalStateException("域名解析失败（$host）。当前网络可能屏蔽蓝奏云域名，请切换 Wi-Fi/流量后重试")
            e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
                IllegalStateException("连接 $host 超时/被拒。当前网络可能屏蔽蓝奏云域名，请切换 Wi-Fi/流量后重试")
            e is javax.net.ssl.SSLException ->
                IllegalStateException("与 $host 的安全连接被中断，请切换网络后重试")
            else -> IllegalStateException("网络异常：${e.message ?: e.javaClass.simpleName}，请重试")
        }
    }

    /** POST 表单：自动处理响应中的 acw 挑战重试 */
    private suspend fun postForm(
        url: String,
        form: Map<String, String>,
        referer: String,
        accountCookie: String?,
        client: OkHttpClient = apiClient
    ): String = withContext(Dispatchers.IO) {
        var acw: String? = null
        repeat(3) {
            val fb = FormBody.Builder()
            form.forEach { (k, v) -> fb.add(k, v) }
            val req = Request.Builder()
                .url(url)
                .headers(
                    baseHeaders(referer, acw, accountCookie)
                        .set("Origin", Regex("^(https?://[^/]+)").find(url)?.value ?: referer)
                        .set("X-Requested-With", "XMLHttpRequest")
                        .set("Accept", "application/json, text/javascript, */*; q=0.01")
                        .build()
                )
                .post(fb.build())
                .build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext ""
                resp.body?.string() ?: ""
            }
            val challenge = acwRegex.find(body)?.groupValues?.getOrNull(1)
            if (challenge == null || acw != null) return@withContext body
            acw = calcAcwScV2(challenge) ?: return@withContext body
        }
        ""
    }

    // ---------- 页面解析辅助 ----------

    /** 找到页面/子页面中的 $.ajax POST 块：返回 (url, 参数表) */
    private fun parseAjaxBlock(html: String): Pair<String, Map<String, String>>? {
        val ajaxUrl = Regex("""url\s*:\s*['"](/ajax[a-zA-Z]+\.php[^'"]*)['"]""").find(html)?.groupValues?.getOrNull(1)
            ?: return null
        // data : { 'k':v, ... }（蓝奏云页面 data 块无嵌套大括号）
        val dataBlock = Regex("""data\s*:\s*\{([^{}]*)\}""").find(html)?.groupValues?.getOrNull(1)
            ?: return ajaxUrl to emptyMap()
        val kvRegex = Regex("""['"]?([0-9A-Za-z_]+)['"]?\s*:\s*(?:'([^']*)'|"([^"]*)"|([0-9A-Za-z_.]+))""")
        val params = linkedMapOf<String, String>()
        for (m in kvRegex.findAll(dataBlock)) {
            val key = m.groupValues[1]
            val strVal = m.groupValues[2].ifEmpty { m.groupValues[3] }
            val raw = m.groupValues[4]
            val value = when {
                strVal.isNotEmpty() -> strVal
                raw.isNotEmpty() -> resolveJsVar(html, raw) ?: raw
                else -> ""
            }
            if (key.isNotEmpty()) params[key] = value
        }
        return ajaxUrl to params
    }

    /** 按名称查 JS 变量值：取最后一个非空声明（蓝奏云存在 isngis 先空后值的两个声明） */
    private fun resolveJsVar(html: String, name: String): String? {
        val re = Regex("""var\s+${Regex.escape(name)}\s*=\s*(?:'([^']*)'|"([^"]*)"|([^;\s]+))\s*;""")
        var result: String? = null
        for (m in re.findAll(html)) {
            val v = m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }
            if (v.isNotBlank()) result = v
        }
        return result
    }

    private fun unescapeHtml(s: String): String {
        return runCatching { android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString() }
            .getOrDefault(s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\""))
            .trim()
    }

    private fun parseSize(text: String): Long {
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

    private fun pageTitle(html: String): String {
        val m = Regex("""<title>([^<]+)</title>""").find(html)?.groupValues?.getOrNull(1)
        m?.let { return it.trim().removeSuffix("- 蓝奏云").trim() }
        return "蓝奏云分享"
    }

    // ---------- 公开解析入口 ----------

    /**
     * 解析分享页（文件或文件夹）。
     * @param baseUrl 如 https://wwbll.lanzoul.com
     * @param shareId 路径段（b0188jmrwj 等）
     * @throws WrongPwdException 提取码错误
     * @throws NeedsPwdException 需要提取码但未提供
     */
    suspend fun fetchPage(baseUrl: String, shareId: String, pwd: String?, accountCookie: String?): LanzouPageResult {
        val pageUrl = "$baseUrl/$shareId"
        var html = getHtml(pageUrl, "https://pc.woozooo.com", accountCookie)
        if (html.isBlank()) throw IllegalStateException("页面加载失败，请检查网络后重试")
        if (html.contains("取消分享")) throw IllegalStateException("分享已被取消")
        if (html.contains("文件不存在") || html.contains("文件已删除")) throw IllegalStateException("文件不存在或已删除")

        val needsPwd = html.contains("pwdload") || html.contains("passwddiv")
        val isFolder = html.contains("filemoreajax")

        return if (isFolder) {
            val entries = listFolderEntries(baseUrl, shareId, html, pwd, accountCookie)
            LanzouPageResult(
                baseUrl = baseUrl, shareId = shareId, isFolder = true,
                title = pageTitle(html), entries = entries, needsPwd = needsPwd
            )
        } else {
            // 单文件：meta + needsPwd 标记（直链在下载时再取，避免过早失效）
            val sizeM = Regex("""(?i)大小[^\d]{0,6}([0-9.]+\s*[BKM]+)""").find(html)?.groupValues?.getOrNull(1) ?: ""
            val timeM = Regex("""\d{4}-\d{2}-\d{2}""").find(html)?.value ?: ""
            var title = Regex("""var filename\s*=\s*'([^']*)'""").find(html)?.groupValues?.getOrNull(1)
                ?: Regex("""id="filenajax">([^<]+)<""").find(html)?.groupValues?.getOrNull(1)
                ?: pageTitle(html)
            title = unescapeHtml(title)
            LanzouPageResult(
                baseUrl = baseUrl, shareId = shareId, isFolder = false,
                title = title.ifBlank { shareId },
                entries = emptyList(),
                sizeText = sizeM, timeText = timeM, needsPwd = needsPwd
            )
        }
    }

    /** 文件夹分享条目（含密码分页）；无密码文件夹从页面 HTML 与 filemoreajax 双路提取。
     *  防护：分页去重 + 页数上限 + 同页重复检测（服务端忽略 pg 参数时旧版会无限追加重复条目
     *  → 列表出现无数重复「未知文件夹」，此处为根治点）。 */
    private suspend fun listFolderEntries(
        baseUrl: String,
        shareId: String,
        pageHtml: String,
        pwd: String?,
        accountCookie: String?
    ): List<LanzouEntry> {
        val entries = ArrayList<LanzouEntry>()
        val seenIds = HashSet<String>()
        // 无密码文件夹：子文件夹直接渲染在页面里（folderlink/mbxfolder）
        if (!pageHtml.contains("pwdload") && !pageHtml.contains("passwddiv")) {
            val subFolderRegex = Regex("""(?i)(?:folderlink|mbxfolder).+?href="/([^"]+)"(?:.+?filename")?>([^<]+)<""")
            for (m in subFolderRegex.findAll(pageHtml)) {
                val id = m.groupValues[1]
                val name = unescapeHtml(m.groupValues[2])
                if (id.isNotBlank() && name.isNotBlank() && seenIds.add("d:$id")) {
                    entries.add(LanzouEntry(id = id, name = name, sizeText = "", timeText = "", isDir = true))
                }
            }
        }
        // 文件（以及带密码文件夹的全部条目）：POST filemoreajax
        val ajax = parseAjaxBlock(pageHtml) ?: return entries
        if (!ajax.first.contains("filemoreajax")) {
            // data 块缺参数时兜底：/filemoreajax.php?file=<fid>
        }
        val params = ajax.second.toMutableMap()
        params["pwd"] = pwd.orEmpty()
        val ajaxUrl = if (ajax.first.startsWith("http")) ajax.first else baseUrl + ajax.first
        var page = 1
        val maxPages = 40            // 防护：最多 40 页（约 2000 条）
        var lastPageIds = ""         // 防护：与上页完全重复 → 服务端忽略 pg 参数，立即停止
        while (page <= maxPages) {
            params["pg"] = page.toString()
            val respBody = postForm(ajaxUrl, params, "$baseUrl/$shareId", accountCookie)
            if (respBody.isBlank()) break
            val json = runCatching { JSONObject(respBody) }.getOrNull() ?: break
            when (json.opt("zt").toString()) {
                "1" -> {
                    val text = json.optJSONArray("text") ?: JSONArray()
                    var added = 0
                    val pageIds = StringBuilder()
                    for (i in 0 until text.length()) {
                        val it = text.optJSONObject(i) ?: continue
                        val id = it.optString("id")
                        if (id.isBlank() || id == "-1") continue
                        if (it.optString("t") == "1") continue // 推广条目
                        pageIds.append(id).append(',')
                        // 去重：同 id 不重复入列（服务端翻页异常时不再无限叠加）
                        if (!seenIds.add("f:$id")) continue
                        val name = unescapeHtml(it.optString("name_all"))
                        // 无名条目不入列（旧版曾以空名生成「未知文件夹」）
                        if (name.isBlank()) continue
                        val sizeText = it.optString("size")
                        val icon = it.optString("icon").lowercase()
                        entries.add(
                            LanzouEntry(
                                id = id,
                                name = name,
                                sizeText = sizeText,
                                timeText = it.optString("time"),
                                isDir = sizeText.isBlank() || icon.contains("folder")
                            )
                        )
                        added++
                    }
                    // 与上页完全相同 → 服务端未真正翻页，停止（否则无限循环）
                    val currentIds = pageIds.toString()
                    if (currentIds.isNotEmpty() && currentIds == lastPageIds) break
                    lastPageIds = currentIds
                    if (added < 50) break // 少于 50 条即最后一页（页面 JS 同款逻辑）
                    page++
                }
                "2" -> break // 没有文件
                "3" -> {
                    if (pwd.isNullOrBlank()) throw NeedsPwdException()
                    throw WrongPwdException(json.optString("info").ifBlank { "提取码不正确" })
                }
                "6" -> throw IllegalStateException(json.optString("info").ifBlank { "分享异常" })
                else -> throw IllegalStateException(json.optString("info").ifBlank { "获取文件列表失败" })
            }
        }
        return entries
    }

    /**
     * 获取单文件直链：
     * 页面(或 iframe 子页) → POST ajaxfile/ajaxm → dom+url → 直链页 302 Location。
     * @return 直链（CDN 地址）
     */
    suspend fun fetchDirectLink(baseUrl: String, shareId: String, pwd: String?, accountCookie: String?): String {
        val pageUrl = "$baseUrl/$shareId"
        var html = getHtml(pageUrl, "https://pc.woozooo.com", accountCookie)
        if (html.isBlank()) throw IllegalStateException("页面加载失败，请检查网络后重试")

        var ajax: Pair<String, Map<String, String>>? = null
        val needsPwd = html.contains("pwdload") || html.contains("passwddiv")
        if (needsPwd) {
            ajax = parseAjaxBlock(html)
        } else {
            // 无密码文件：先看 iframe src；再看 srcdoc（新页面把 ajax 代码内联在 iframe srcdoc 中）
            val iframeSrc = Regex("""<iframe[^>]*\ssrc="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
            if (iframeSrc != null) {
                val subUrl = if (iframeSrc.startsWith("http")) iframeSrc else baseUrl + iframeSrc
                val subHtml = getHtml(subUrl, pageUrl, accountCookie)
                ajax = parseAjaxBlock(subHtml)
            }
            if (ajax == null) {
                val srcDoc = Regex("""<iframe[^>]*\ssrcdoc="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)
                if (srcDoc != null) {
                    ajax = parseAjaxBlock(unescapeHtml(srcDoc))
                }
            }
            if (ajax == null) ajax = parseAjaxBlock(html)
        }
        val block = ajax ?: throw IllegalStateException("页面解析失败（未找到下载参数），请重试")

        val form = block.second.toMutableMap()
        form["p"] = pwd.orEmpty()
        if (!form.containsKey("kd")) form["kd"] = "1"
        val ajaxUrl = if (block.first.startsWith("http")) block.first else baseUrl + block.first

        val respBody = postForm(ajaxUrl, form, pageUrl, accountCookie)
        if (respBody.isBlank()) throw IllegalStateException("下载接口无响应，请重试")
        val json = runCatching { JSONObject(respBody) }.getOrNull()
            ?: throw IllegalStateException("下载接口响应异常，请重试")
        if (json.opt("zt").toString() != "1") {
            val inf = json.optString("inf")
            if (inf.contains("密码")) {
                if (pwd.isNullOrBlank()) throw NeedsPwdException()
                throw WrongPwdException(inf)
            }
            throw IllegalStateException(inf.ifBlank { "获取下载地址失败" })
        }
        val dom = json.optString("dom")
        val url = json.optString("url")
        if (dom.isBlank() || url.isBlank() || url == "0") throw IllegalStateException("直链返回无效，请稍后重试")
        val downloadPage = "$dom/file/$url"

        // 直链页：302 Location 即 CDN 直链；200 则为滑动验证页 → ajax.php 二次获取
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(downloadPage)
                .headers(baseHeaders(downloadPage, null, accountCookie).build())
                .get()
                .build()
            val resp = noRedirectClient.newCall(req).execute()
            val location = resp.use { it.header("Location") }
            if (location != null) return@withContext location
            val body = resp.body?.string() ?: ""
            if (body.isBlank()) throw IllegalStateException("直链获取失败，请重试")
            if (body.contains("arg1='")) throw IllegalStateException("触发安全校验，请稍后重试")
            // 验证页：data 参数 + el=2 → POST dom/ajax.php
            val dataBlock = Regex("""data\s*:\s*\{([^{}]*)\}""").find(body)?.groupValues?.getOrNull(1)
            val kvRegex = Regex("""['"]?([0-9A-Za-z_]+)['"]?\s*:\s*(?:'([^']*)'|"([^"]*)"|([0-9A-Za-z_.]+))""")
            val form2 = linkedMapOf<String, String>()
            if (dataBlock != null) {
                for (m in kvRegex.findAll(dataBlock)) {
                    val key = m.groupValues[1]
                    val strVal = m.groupValues[2].ifEmpty { m.groupValues[3] }
                    val raw = m.groupValues[4]
                    val value = when {
                        strVal.isNotEmpty() -> strVal
                        raw.isNotEmpty() -> resolveJsVar(body, raw) ?: raw
                        else -> ""
                    }
                    if (key.isNotEmpty()) form2[key] = value
                }
            }
            form2["el"] = "2"
            Thread.sleep(2000)
            val ajax2 = postForm("$dom/ajax.php", form2, downloadPage, accountCookie)
            val j2 = runCatching { JSONObject(ajax2) }.getOrNull()
            val u2 = j2?.optString("url").orEmpty()
            if (u2.isBlank()) throw IllegalStateException("直链获取失败（验证页），请稍后重试")
            u2
        }
    }

    /** 解析"大小"文本为字节（列表展示用） */
    fun sizeOf(text: String): Long = parseSize(text)
}
