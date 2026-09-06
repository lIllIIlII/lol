/*
 * 吸析At - 日志汇报（邮件上报）。
 *
 * 通道设计（保证真实可用）：
 * 1. QQ 邮箱 SMTP 直发（smtp.qq.com:465 SSL，AUTH LOGIN 授权码）——真正的 QQ 邮箱发送 API；
 *    发件账号与授权码支持「远程配置」（GitHub 仓库 report.json），改配置无需重打包；
 * 2. SMTP 未配置/失败 → 自动回退 mailto:（系统邮件/QQ邮箱客户端，已自动填好收件人与正文）。
 *
 * 汇报目标邮箱：3395858053@qq.com（开发者）。
 */

package com.yunx.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.yunx.app.data.network.HttpClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

object LogReportManager {

    /** 汇报目标邮箱（开发者 QQ 邮箱） */
    const val TO_EMAIL = "3395858053@qq.com"

    private const val PREFS = "yunx_settings"
    private const val PREF_QQ = "report_qq"
    private const val PREF_CFG_JSON = "report_smtp_cfg"
    private const val PREF_CFG_TS = "report_smtp_cfg_ts"
    private const val CFG_TTL_MS = 60L * 60 * 1000 // 远程配置缓存 1 小时

    /** SMTP 远程配置源（GitHub 仓库，多镜像） */
    private val CONFIG_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/lIllIIlII/lol@main/report.json",
        "https://gh-proxy.com/https://raw.githubusercontent.com/lIllIIlII/lol/main/report.json",
        "https://raw.githubusercontent.com/lIllIIlII/lol/main/report.json"
    )

    data class SmtpConfig(
        val user: String,
        val authCode: String,
        val to: String,
        val host: String,
        val port: Int
    ) {
        val available: Boolean get() = user.isNotBlank() && authCode.isNotBlank()
    }

    /** 上次填写的 QQ 号（便利回填） */
    fun lastQq(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_QQ, "").orEmpty()

    fun saveQq(context: Context, qq: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREF_QQ, qq).apply()
    }

    // ---------- 汇报正文 ----------

    /** 组装汇报正文：QQ + 设备信息 + 应用版本 + 崩溃/运行日志（脱敏，限最近行数） */
    fun buildReport(context: Context, qq: String, maxLines: Int = 1200): String {
        val sb = StringBuilder()
        val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        sb.append("【吸析At 日志汇报】\n")
        sb.append("汇报人QQ：${qq.ifBlank { "未填写" }}\n")
        sb.append("汇报时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("应用版本：${pkg?.versionName ?: "?"}（${pkg?.versionCode ?: "?"}）\n")
        sb.append("设备：${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("系统：Android ${Build.VERSION.RELEASE}（SDK ${Build.VERSION.SDK_INT}）\n")
        sb.append("\n========== 运行日志（最近 $maxLines 行，已脱敏） ==========\n")
        sb.append(dumpLogcatTail(maxLines))
        return sb.toString()
    }

    /** logcat -d 当前进程最近 N 行（脱敏；读取失败给出提示行） */
    private fun dumpLogcatTail(maxLines: Int): String {
        return runCatching {
            val process = ProcessBuilder(
                "logcat", "-d", "-v", "time", "--pid=${android.os.Process.myPid()}"
            ).redirectErrorStream(true).start()
            val lines = ArrayDeque<String>()
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    lines.addLast(LogRedactor.line(line))
                    if (lines.size > maxLines) lines.removeFirst()
                    line = reader.readLine()
                }
            }
            runCatching { process.waitFor() }
            if (lines.isEmpty()) "（无日志输出）" else lines.joinToString("\n")
        }.getOrElse { "（读取日志失败：${it.message}）" }
    }

    // ---------- 远程 SMTP 配置 ----------

    /** 读取 SMTP 配置：远程拉取（带 1h 缓存）→ 失败用本地缓存/内置默认 */
    suspend fun loadConfig(context: Context): SmtpConfig = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(PREF_CFG_JSON, null)
        val cachedTs = prefs.getLong(PREF_CFG_TS, 0L)
        if (cached != null && System.currentTimeMillis() - cachedTs < CFG_TTL_MS) {
            return@withContext parseConfig(cached)
        }
        for (url in CONFIG_URLS) {
            val body = runCatching {
                HttpClients.apiClient().newCall(Request.Builder().url(url).get().build())
                    .execute().use { resp ->
                        if (!resp.isSuccessful) return@runCatching null
                        resp.body?.string()
                    }
            }.getOrNull() ?: continue
            if (body.isNullOrBlank()) continue
            val start = body.indexOf('{')
            val end = body.lastIndexOf('}')
            if (start < 0 || end <= start) continue
            val cfg = parseConfig(body.substring(start, end + 1))
            if (cfg.available) {
                prefs.edit()
                    .putString(PREF_CFG_JSON, body.substring(start, end + 1))
                    .putLong(PREF_CFG_TS, System.currentTimeMillis())
                    .apply()
                return@withContext cfg
            }
        }
        cached?.let { parseConfig(it) } ?: defaultConfig()
    }

    private fun parseConfig(json: String): SmtpConfig = runCatching {
        val j = JSONObject(json)
        SmtpConfig(
            user = j.optString("smtp_user").trim(),
            authCode = j.optString("smtp_auth_code").trim(),
            to = j.optString("to").ifBlank { TO_EMAIL },
            host = j.optString("smtp_host").ifBlank { "smtp.qq.com" },
            port = j.optInt("smtp_port", 465)
        )
    }.getOrDefault(defaultConfig())

    /** 内置默认（授权码留空 → 视为未配置，走 mailto 兜底） */
    private fun defaultConfig() = SmtpConfig(
        user = "",
        authCode = "",
        to = TO_EMAIL,
        host = "smtp.qq.com",
        port = 465
    )

    // ---------- SMTP 直发（QQ 邮箱发送 API） ----------

    /**
     * 通过 QQ SMTP 发送汇报邮件（SSL + AUTH LOGIN）。
     * @param qq 汇报人 QQ 号（作为 Reply-To，方便开发者直接回复）
     */
    suspend fun sendViaSmtp(
        context: Context,
        qq: String,
        config: SmtpConfig? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = config ?: loadConfig(context)
            check(cfg.available) { "SMTP 未配置（report.json 缺少授权码），无法自动发送" }
            val subject = "吸析At 日志汇报 - QQ:${qq.ifBlank { "未填写" }}"
            val body = buildReport(context, qq)
            smtpSend(cfg, subject, body, replyTo = qq.takeIf { it.isNotBlank() }?.let { "$it@qq.com" })
        }
    }

    /** 最小 SMTPS 客户端（无第三方依赖） */
    private fun smtpSend(cfg: SmtpConfig, subject: String, body: String, replyTo: String?) {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val socket = factory.createSocket() as SSLSocket
        socket.connect(InetSocketAddress(cfg.host, cfg.port), 15000)
        socket.soTimeout = 30000
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, StandardCharsets.ISO_8859_1))
            val writer = OutputStreamWriter(socket.outputStream, StandardCharsets.ISO_8859_1)

            fun read(): String {
                val sb = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    sb.append(line)
                    // 多行响应：以空格结尾的行是续行（如 250-... / 250 ...）
                    if (line.length < 4 || line[3] == ' ') break
                }
                return sb.toString()
            }

            fun send(cmd: String): String {
                writer.write(cmd)
                writer.flush()
                return read()
            }

            require(read().startsWith("220")) { "SMTP 服务器无响应" }
            require(send("EHLO xixiat\r\n").startsWith("250")) { "EHLO 失败" }
            require(send("AUTH LOGIN\r\n").startsWith("334")) { "服务器不支持 AUTH LOGIN" }
            require(send(b64(cfg.user) + "\r\n").startsWith("334")) { "账号被拒绝" }
            val authResp = send(b64(cfg.authCode) + "\r\n")
            require(authResp.startsWith("235")) {
                "授权码校验失败（授权码错误或未开启 SMTP），请检查 report.json 配置"
            }
            require(send("MAIL FROM:<${cfg.user}>\r\n").startsWith("250")) { "MAIL FROM 被拒绝" }
            require(send("RCPT TO:<${cfg.to}>\r\n").startsWith("250")) { "RCPT TO 被拒绝" }
            require(send("DATA\r\n").startsWith("354")) { "DATA 被拒绝" }

            // 邮件头（主题 MIME B 编码支持中文；正文 base64 传输，杜绝乱码）
            val headers = buildString {
                append("From: XiXiAt Log <${cfg.user}>\r\n")
                append("To: <${cfg.to}>\r\n")
                replyTo?.let { append("Reply-To: <$it>\r\n") }
                append("Subject: ${mimeB(subject)}\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/plain; charset=UTF-8\r\n")
                append("Content-Transfer-Encoding: base64\r\n")
                append("Date: ${SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date())}\r\n")
                append("\r\n")
            }
            val encoded = wrap76(b64(body.toByteArray(StandardCharsets.UTF_8)))
            val message = headers + encoded + "\r\n.\r\n"
            val dataResp = send(message)
            require(dataResp.startsWith("250")) { "邮件投递失败：$dataResp" }
            runCatching { send("QUIT\r\n") }
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun b64(s: String): String =
        Base64.encodeToString(s.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    /** base64 文本按 76 字符折行（RFC 2045） */
    private fun wrap76(s: String): String {
        if (s.length <= 76) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val end = minOf(i + 76, s.length)
            sb.append(s.substring(i, end)).append("\r\n")
            i = end
        }
        return sb.toString()
    }

    /** 中文主题 → =?UTF-8?B?...?= */
    private fun mimeB(s: String): String = "=?UTF-8?B?${b64(s)}?="

    // ---------- mailto 兜底 ----------

    /**
     * 打开系统邮件客户端（QQ 邮箱等），自动填好收件人/主题/正文（截断至安全长度）。
     * 返回是否成功调起。
     */
    fun openMailto(context: Context, qq: String): Boolean {
        val subject = "吸析At 日志汇报 - QQ:${qq.ifBlank { "未填写" }}"
        val body = buildReport(context, qq, maxLines = 400).let {
            if (it.length > 20000) it.substring(0, 20000) + "\n…（过长截断，完整日志请通过自动通道发送）" else it
        }
        return runCatching {
            val uri = Uri.parse("mailto:${TO_EMAIL}").buildUpon()
                .appendQueryParameter("subject", subject)
                .appendQueryParameter("body", body)
                .build()
            context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
            true
        }.getOrElse { false }
    }
}
