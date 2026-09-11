package com.yunx.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object LogExporter {

    private const val EXPORT_DIR = "logs"

    private const val MAX_LINES = 30000

    fun export(context: Context): File? = runCatching {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val out = File(dir, "yunx_log_${timestamp()}.txt")
        FileOutputStream(out).use { exportTo(context, it) }
        out
    }.getOrNull()

    fun saveToDownloads(context: Context): Boolean = runCatching {
        val fileName = "yunx_log_${timestamp()}.txt"
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri: Uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                exportTo(context, out)
            } ?: false
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out -> exportTo(context, out) }
        }
        ok
    }.getOrDefault(false)

    private fun exportTo(context: Context, output: OutputStream): Boolean = runCatching {
        OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            writer.write("吸析At 日志导出\n")
            writer.write("导出时间：${now()}\n")
            writer.write("应用版本：${pkg.versionName}（${pkg.versionCode}）\n")
            writer.write("设备：${Build.MANUFACTURER} ${Build.MODEL}\n")
            writer.write("系统：Android ${Build.VERSION.RELEASE}（SDK ${Build.VERSION.SDK_INT}）\n")
            writer.write("\n")

            writer.write("========== 运行日志（logcat -d -v time --pid=${Process.myPid()}）==========\n")
            dumpLogcat(
                writer,
                listOf("logcat", "-d", "-v", "time", "--pid=${Process.myPid()}")
            )
        }
        true
    }.getOrDefault(false)

    fun clearLogcat(): Boolean = runCatching {
        ProcessBuilder("logcat", "-c").start().waitFor()
        true
    }.getOrDefault(false)

    private fun dumpLogcat(writer: OutputStreamWriter, command: List<String>) {
        var process: java.lang.Process? = null
        try {
            process = ProcessBuilder(command).redirectErrorStream(true).start()
            val reader =
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))

            val lines = ArrayDeque<String>()
            var line: String? = reader.readLine()
            while (line != null) {
                lines.addLast(line)
                if (lines.size > MAX_LINES) lines.removeFirst()
                line = reader.readLine()
            }
            process.waitFor()

            if (lines.isEmpty()) {
                writer.write("（无输出）\n")
            } else {
                lines.forEach { writer.write(LogRedactor.line(it)); writer.write("\n") }
            }
        } catch (e: Exception) {
            writer.write("（读取日志失败：${e.message}）\n")
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    fun share(context: Context, file: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "分享日志").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        true
    }.getOrElse {
        false
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
}
