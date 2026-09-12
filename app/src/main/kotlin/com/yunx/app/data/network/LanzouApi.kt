package com.yunx.app.data.network

import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class LanzouEntry(
    val id: String,
    val name: String,
    val sizeText: String,
    val timeText: String,
    val isDir: Boolean
)

class LanzouNetException(message: String) : IllegalStateException(message)

class LanzouHostException(message: String) : IllegalStateException(message)

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

    private val FAMILY_DOMAINS = listOf(
        "wwi.lanzouw.com", "wwl.lanzoup.com", "wwm.lanzouv.com",
        "wwp.lanzoui.com", "wwk.lanzoue.com", "wwj.lanzouf.com",
        "wwh.lanzoug.com", "wwy.lanzouh.com", "wwt.lanzouq.com",
        "wws.lanzout.com", "wwr.lanzoux.com", "wwq.lanzouy.com",
        "wwn.lanzoul.com", "wwc.lanzouc.com", "wwd.lanzoum.com",
        "wwu.lanpw.com", "wwv.lanpv.com", "wwz.lanzn.com",
        "wwa.lanzoua.com", "wwb.lanzoub.com", "wwe.lanzoud.com",
        "wwf.lanzouf.com", "wwg.lanzoug.com", "wwi.lanzoui.com",
        "wwl.lanzouj.com", "wwm.lanzouk.com", "wwn.lanzoul.com",
        "wwp.lanzouo.com", "wwr.lanzour.com", "wws.lanzous.com",
        "wwt.lanzout.com", "wwu.lanzouw.com", "wwv.lanzoux.com",
        "wwx.lanzouy.com", "wwy.lanzouz.com", "wwz.lanzn.com",
        "wwa.lanzou.com", "wwb.lanzou.com", "wwc.lanzou.com",
        "wwd.lanzou.com", "wwe.lanzou.com", "wwf.lanzou.com",
        "wwg.lanzou.com", "wwh.lanzou.com", "wwj.lanzou.com",
        "wwk.lanzou.com", "wwl.lanzou.com", "wwm.lanzou.com",
        "wwn.lanzou.com", "wwo.lanzou.com", "wwp.lanzou.com",
        "wwq.lanzou.com", "wwr.lanzou.com", "wws.lanzou.com",
        "wwt.lanzou.com", "wwu.lanzou.com", "wwv.lanzou.com",
        "www.lanzou.com", "wwx.lanzou.com", "wwy.lanzou.com",
        "wwz.lanzou.com",
        "www.lanzoui.com", "www.lanzouj.com", "www.lanzouk.com",
        "www.lanzoul.com", "www.lanzoum.com", "www.lanzoun.com",
        "www.lanzouo.com", "www.lanzoup.com", "www.lanzouq.com",
        "www.lanzour.com", "www.lanzous.com", "www.lanzout.com",
        "www.lanzouu.com", "www.lanzouv.com", "www.lanzouw.com",
        "www.lanzoux.com", "www.lanzouy.com", "www.lanzouz.com",
        "www.lanzoua.com", "www.lanzoub.com", "www.lanzouc.com",
        "www.lanzoud.com", "www.lanzoue.com", "www.lanzouf.com",
        "www.lanzoug.com", "www.lanzouh.com",
        "www.lanpw.com", "www.lanpv.com", "www.lanzn.com",
        "ww.lanzoui.com", "ww.lanzouj.com", "ww.lanzouk.com",
        "ww.lanzoul.com", "ww.lanzoum.com", "ww.lanzoun.com",
        "ww.lanzouo.com", "ww.lanzoup.com", "ww.lanzouq.com",
        "ww.lanzour.com", "ww.lanzous.com", "ww.lanzout.com",
        "ww.lanzouv.com", "ww.lanzouw.com", "ww.lanzoux.com",
        "ww.lanzouy.com", "ww.lanzouz.com", "ww.lanzou.com",
        "lanzou.com", "lanzoui.com", "lanzouj.com", "lanzouk.com",
        "lanzoul.com", "lanzoum.com", "lanzoun.com", "lanzouo.com",
        "lanzoup.com", "lanzouq.com", "lanzour.com", "lanzous.com",
        "lanzout.com", "lanzouv.com", "lanzouw.com", "lanzoux.com",
        "lanzouy.com", "lanzouz.com", "lanzoua.com", "lanzoub.com",
        "lanzouc.com", "lanzoud.com", "lanzoue.com", "lanzouf.com",
        "lanzoug.com", "lanzouh.com", "lanpw.com", "lanpv.com",
        "lanzn.com"
    )

    private fun failoverHosts(original: String): List<String> {
        val cleaned = original.removePrefix("https://").removePrefix("http://").substringBefore('/')
        val matched = FAMILY_DOMAINS.firstOrNull { it.equals(cleaned, ignoreCase = true) }
        val ordered = if (matched != null) {
            listOf(matched) + FAMILY_DOMAINS.filter { !it.equals(matched, ignoreCase = true) }
        } else {
            listOf(cleaned) + FAMILY_DOMAINS
        }
        return ordered.distinctBy { it.lowercase() }.take(15)
    }

    private val hostCookies = ConcurrentHashMap<String, String>()

    private val cookieNameRegex = Regex("""^(acw_tc|cdn_sec_tc)=""")

    private fun captureHostCookies(host: String, resp: okhttp3.Response) {
        val frags = resp.headers("Set-Cookie")
            .mapNotNull { sc -> cookieNameRegex.find(sc)?.let { sc.substring(0, sc.indexOf(';').takeIf { i -> i > 0 } ?: sc.length) } }
        if (frags.isNotEmpty()) {
            hostCookies[host] = frags.joinToString("; ")
        }
    }

    private fun buildCookieHeader(host: String, accountCookie: String?, acw: String?): String {
        val parts = ArrayList<String>(3)
        accountCookie?.takeIf { it.isNotBlank() }?.let { parts.add(it.trim().trimEnd(';')) }
        hostCookies[host]?.let { parts.add(it) }
        acw?.let { parts.add("acw_sc__v2=$it") }
        return parts.joinToString("; ")
    }

    private const val ACW_XOR_KEY = "3000176000856006061501533003690027800375"
    private val ACW_BOX = intArrayOf(
        6, 28, 34, 31, 33, 18, 30, 23, 9, 8, 19, 38, 17, 24, 0, 5, 32, 21, 10, 22, 25,
        14, 15, 3, 16, 27, 13, 35, 2, 29, 11, 26, 4, 36, 1, 39, 37, 7, 20, 12
    )

    class WrongPwdException(message: String = "提取码不正确") : Exception(message)
    class NeedsPwdException(message: String = "该分享需要提取码") : Exception(message)

    private val acwRegex = Regex("""arg1='([0-9A-Z]+)'""")

    private val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SmartDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private val noRedirectClient: OkHttpClient by lazy {
        apiClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

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

    private fun baseHeaders(
        referer: String,
        acw: String? = null,
        accountCookie: String? = null,
        host: String? = null
    ): okhttp3.Headers.Builder {
        val b = okhttp3.Headers.Builder()
            .set("User-Agent", USER_AGENT)
            .set("Referer", referer)
            .set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        val cookie = when {
            host != null -> buildCookieHeader(host, accountCookie, acw)
            accountCookie?.isNotBlank() == true -> accountCookie.trim().trimEnd(';')
            else -> ""
        }
        if (cookie.isNotEmpty()) b.set("Cookie", cookie)
        return b
    }

    private suspend fun getHtml(url: String, referer: String, accountCookie: String?): String = withContext(Dispatchers.IO) {
        val host = runCatching { url.toHttpUrlOrNull()?.host }.getOrNull() ?: url
        var acw: String? = null
        var lastBody = ""
        repeat(ACW_MAX_ROUNDS) {
            val req = Request.Builder().url(url)
                .headers(baseHeaders(referer, acw, accountCookie, host).build()).get().build()
            val body = runCatching {
                apiClient.newCall(req).execute().use { resp ->
                    captureHostCookies(host, resp)
                    when {
                        resp.isSuccessful -> resp.body?.string() ?: ""
                        resp.code in BLOCK_HTTP_CODES -> throw LanzouNetException("HTTP ${resp.code}（节点拒绝）")
                        else -> return@withContext ""
                    }
                }
            }.getOrElse { e -> throw translateNetworkError(e, url) }
            lastBody = body
            val challenge = acwRegex.find(body)?.groupValues?.getOrNull(1) ?: return@withContext body
            acw = calcAcwScV2(challenge) ?: return@withContext body
        }
        if (acwRegex.containsMatchIn(lastBody)) throw LanzouHostException("反爬挑战未通过（${ACW_MAX_ROUNDS} 轮）")
        lastBody
    }

    private const val ACW_MAX_ROUNDS = 6

    private val BLOCK_HTTP_CODES = intArrayOf(403, 405, 406, 429, 500, 502, 503, 504)

    private fun translateNetworkError(e: Throwable, url: String): LanzouNetException {
        if (e is LanzouNetException) return e
        val host = runCatching { url.toHttpUrlOrNull()?.host ?: url }.getOrDefault(url)
        return LanzouNetException(
            when {
                e is java.net.UnknownHostException -> "域名解析失败（$host）"
                e is java.net.SocketTimeoutException || e is java.net.ConnectException -> "连接 $host 超时/被拒"
                e is javax.net.ssl.SSLException -> "与 $host 的安全连接被中断"
                else -> "网络异常（${e.javaClass.simpleName}）"
            }
        )
    }

    private suspend fun postForm(
        url: String,
        form: Map<String, String>,
        referer: String,
        accountCookie: String?,
        client: OkHttpClient = apiClient
    ): String = withContext(Dispatchers.IO) {
        val host = runCatching { url.toHttpUrlOrNull()?.host }.getOrNull() ?: url
        var acw: String? = null
        var lastBody = ""
        repeat(ACW_MAX_ROUNDS) {
            val fb = FormBody.Builder()
            form.forEach { (k, v) -> fb.add(k, v) }
            val req = Request.Builder()
                .url(url)
                .headers(
                    baseHeaders(referer, acw, accountCookie, host)
                        .set("Origin", Regex("^(https?://[^/]+)").find(url)?.value ?: referer)
                        .set("X-Requested-With", "XMLHttpRequest")
                        .set("Accept", "application/json, text/javascript, */*; q=0.01")
                        .build()
                )
                .post(fb.build())
                .build()
            val body = runCatching {
                client.newCall(req).execute().use { resp ->
                    captureHostCookies(host, resp)
                    when {
                        resp.isSuccessful -> resp.body?.string() ?: ""
                        resp.code in BLOCK_HTTP_CODES -> throw LanzouNetException("HTTP ${resp.code}（节点拒绝）")
                        else -> return@withContext ""
                    }
                }
            }.getOrElse { e -> throw translateNetworkError(e, url) }
            lastBody = body
            val challenge = acwRegex.find(body)?.groupValues?.getOrNull(1) ?: return@withContext body
            acw = calcAcwScV2(challenge) ?: return@withContext body
        }
        if (acwRegex.containsMatchIn(lastBody)) throw LanzouHostException("接口被反爬拦截（${ACW_MAX_ROUNDS} 轮）")
        lastBody
    }

    private fun parseAjaxBlock(html: String): Pair<String, Map<String, String>>? {
        val ajaxUrl = Regex("""url\s*:\s*['"](/(?:ajax|filemoreajax)[a-zA-Z]*\.php[^'"]*)['"]""").find(html)?.groupValues?.getOrNull(1)
            ?: return null
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

    private const val RACE_STAGGER_MS = 700L

    suspend fun fetchPage(baseUrl: String, shareId: String, pwd: String?, accountCookie: String?): LanzouPageResult {
        val originalHost = runCatching { baseUrl.toHttpUrlOrNull()?.host }.getOrNull() ?: baseUrl
        return raceHosts(failoverHosts(originalHost)) { host ->
            fetchPageOnHost("https://$host", shareId, pwd, accountCookie)
        }
    }

    private suspend fun fetchPageOnHost(
        baseUrl: String,
        shareId: String,
        pwd: String?,
        accountCookie: String?
    ): LanzouPageResult {
        val pageUrl = "$baseUrl/$shareId"
        val html = getHtml(pageUrl, "https://pc.woozooo.com", accountCookie)
        if (html.isBlank()) throw LanzouHostException("页面加载失败，请检查网络后重试")
        if (html.contains("arg1='")) throw LanzouHostException("反爬挑战未通过，请重试")
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

    private suspend fun listFolderEntries(
        baseUrl: String,
        shareId: String,
        pageHtml: String,
        pwd: String?,
        accountCookie: String?
    ): List<LanzouEntry> {
        val entries = ArrayList<LanzouEntry>()
        val seenIds = HashSet<String>()
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
        val ajax = parseAjaxBlock(pageHtml) ?: return entries
        if (!ajax.first.contains("filemoreajax")) {
        }
        val params = ajax.second.toMutableMap()
        params["pwd"] = pwd.orEmpty()
        val ajaxUrl = if (ajax.first.startsWith("http")) ajax.first else baseUrl + ajax.first
        var page = 1
        val maxPages = 40
        var lastPageIds = ""
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
                        if (it.optString("t") == "1") continue
                        pageIds.append(id).append(',')
                        if (!seenIds.add("f:$id")) continue
                        val name = unescapeHtml(it.optString("name_all"))
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
                    val currentIds = pageIds.toString()
                    if (currentIds.isNotEmpty() && currentIds == lastPageIds) break
                    lastPageIds = currentIds
                    if (added < 50) break
                    page++
                }
                "2" -> break
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

    suspend fun fetchDirectLink(baseUrl: String, shareId: String, pwd: String?, accountCookie: String?): String {
        val originalHost = runCatching { baseUrl.toHttpUrlOrNull()?.host }.getOrNull() ?: baseUrl
        return raceHosts(failoverHosts(originalHost)) { host ->
            fetchDirectLinkOnHost("https://$host", shareId, pwd, accountCookie)
        }
    }

    private suspend fun <T> raceHosts(hosts: List<String>, block: suspend (host: String) -> T): T {
        if (hosts.size <= 1) return block(hosts.first())
        val outcomes = Channel<Pair<String, Result<T>>>(Channel.UNLIMITED)
        val raceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            hosts.forEachIndexed { i, host ->
                raceScope.launch {
                    if (i > 0) delay(RACE_STAGGER_MS * i)
                    val r = runCatching { block(host) }
                    if (isActive) outcomes.trySend(host to r)
                }
            }
            val failures = ArrayList<String>(hosts.size)
            repeat(hosts.size) {
                val (host, r) = outcomes.receive()
                r.getOrNull()?.let { return it }
                val e = r.exceptionOrNull() ?: return@repeat
                if (e !is LanzouNetException && e !is LanzouHostException) throw e
                failures.add("$host=${shortNetTag(e)}")
            }
            throw LanzouNetException(buildAllFailedDiagnostic(failures))
        } finally {
            raceScope.cancel()
        }
    }

    private fun shortNetTag(e: Throwable): String = when {
        e is LanzouHostException -> (e.message ?: "节点异常").take(20)
        e is java.net.UnknownHostException -> "解析失败"
        e is java.net.SocketTimeoutException -> "连接超时"
        e is java.net.ConnectException -> "连接被拒"
        e is javax.net.ssl.SSLException -> "TLS中断"
        else -> (e.message ?: e.javaClass.simpleName).take(20)
    }

    private fun buildAllFailedDiagnostic(failures: List<String>): String {
        val shown = failures.take(4).joinToString("；")
        val more = if (failures.size > 4) "；等 ${failures.size} 个域名" else ""
        return "蓝奏云全线路连接失败（$shown$more）。" +
            "建议：① 若正在使用 VPN/代理请关闭后重试（部分代理出口会被蓝奏云 CDN 拒绝）；" +
            "② 切换 Wi-Fi/流量；③ 稍后再试"
    }

    private suspend fun fetchDirectLinkOnHost(
        baseUrl: String,
        shareId: String,
        pwd: String?,
        accountCookie: String?
    ): String {
        val pageUrl = "$baseUrl/$shareId"
        val html = getHtml(pageUrl, "https://pc.woozooo.com", accountCookie)
        if (html.isBlank()) throw LanzouHostException("页面加载失败，请检查网络后重试")
        if (html.contains("arg1='")) throw LanzouHostException("反爬挑战未通过，请重试")

        var ajax: Pair<String, Map<String, String>>? = null
        val needsPwd = html.contains("pwdload") || html.contains("passwddiv")
        if (needsPwd) {
            ajax = parseAjaxBlock(html)
        } else {
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
        val block = ajax ?: throw LanzouHostException("页面解析失败（未找到下载参数），请重试")

        val form = block.second.toMutableMap()
        form["p"] = pwd.orEmpty()
        if (!form.containsKey("kd")) form["kd"] = "1"
        val ajaxUrl = if (block.first.startsWith("http")) block.first else baseUrl + block.first

        val respBody = postForm(ajaxUrl, form, pageUrl, accountCookie)
        if (respBody.isBlank()) throw LanzouHostException("下载接口无响应，请重试")
        if (respBody.contains("arg1='")) throw LanzouHostException("下载接口被反爬拦截，请重试")
        val json = runCatching { JSONObject(respBody) }.getOrNull()
            ?: throw LanzouHostException("下载接口响应异常，请重试")
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
        if (dom.isBlank() || url.isBlank() || url == "0") throw LanzouHostException("直链返回无效，请稍后重试")
        val downloadPage = "$dom/file/$url"

        return withContext(Dispatchers.IO) {
            val dlHost = runCatching { downloadPage.toHttpUrlOrNull()?.host }.getOrNull() ?: downloadPage
            val req = Request.Builder()
                .url(downloadPage)
                .headers(baseHeaders(downloadPage, null, accountCookie, dlHost).build())
                .get()
                .build()
            val resp = runCatching { noRedirectClient.newCall(req).execute() }
                .getOrElse { e -> throw translateNetworkError(e, downloadPage) }
            val location = resp.use { it.header("Location") }
            if (location != null) return@withContext location
            val body = resp.body?.string() ?: ""
            if (body.isBlank()) throw LanzouHostException("直链获取失败，请重试")
            if (body.contains("arg1='")) throw LanzouHostException("触发安全校验，请稍后重试")
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
            delay(2000)
            val ajax2 = postForm("$dom/ajax.php", form2, downloadPage, accountCookie)
            val j2 = runCatching { JSONObject(ajax2) }.getOrNull()
            val u2 = j2?.optString("url").orEmpty()
            if (u2.isBlank()) throw LanzouHostException("直链获取失败（验证页），请稍后重试")
            u2
        }
    }

    fun sizeOf(text: String): Long = parseSize(text)
}
