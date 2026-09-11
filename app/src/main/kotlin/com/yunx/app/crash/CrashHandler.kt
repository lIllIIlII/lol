package com.yunx.app.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val log = buildCrashLog(thread, throwable)
        saveCrashLog(log)

        val intent = Intent(context, CrashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_CRASH_LOG, log)
        }
        runCatching { context.startActivity(intent) }

        Process.killProcess(Process.myPid())
        System.exit(1)
    }

    private fun buildCrashLog(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"

        return buildString {
            appendLine("云析 Crash Report")
            appendLine("时间：$time")
            appendLine("线程：${thread.name}")
            appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}（Android ${Build.VERSION.RELEASE}，SDK ${Build.VERSION.SDK_INT}）")
            appendLine("版本：$versionName")
            appendLine()
            appendLine(sw.toString())
        }
    }

    private fun saveCrashLog(log: String) {
        runCatching {
            val dir = File(context.filesDir, "crash").apply { mkdirs() }
            val file = File(dir, "crash_${System.currentTimeMillis()}.txt")
            file.writeText(log)
        }
    }

    companion object {
        const val EXTRA_CRASH_LOG = "extra_crash_log"

        @Volatile
        private var mode = 0

        fun terminate(reason: String) {
            if (reason.isNotEmpty()) {
                android.util.Log.e("YunX", reason)
            }
            when (mode % 3) {
                0 -> {
                    mode++
                    Process.killProcess(Process.myPid())
                    Runtime.getRuntime().exit(9)
                    System.exit(9)
                }
                1 -> {
                    mode++
                    Runtime.getRuntime().halt(10)
                    System.exit(10)
                }
                else -> {
                    mode = 0
                    throw RuntimeException("exit:" + reason)
                }
            }
        }
    }
}
