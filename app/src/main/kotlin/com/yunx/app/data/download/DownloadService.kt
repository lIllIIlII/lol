package com.yunx.app.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.yunx.app.MainActivity
import com.yunx.app.R

class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "下载中…"
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, -1) ?: -1
                val speed = intent?.getStringExtra(EXTRA_SPEED) ?: ""
                val showSpeed = intent?.getBooleanExtra(EXTRA_SHOW_SPEED, true) ?: true
                startAsForeground(title, progress, speed, showSpeed)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    private fun startAsForeground(title: String, progress: Int, speed: String, showSpeed: Boolean) {
        ensureChannel()
        val notification = buildNotification(title, progress, speed, showSpeed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "下载任务", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(title: String, progress: Int, speed: String, showSpeed: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(title)
            .setContentText(
                if (showSpeed && speed.isNotBlank()) "下载速度 $speed"
                else "正在后台下载，完成前请勿关闭应用"
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (showSpeed && progress in 0..100) {
            builder.setProgress(100, progress, false)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "yunx_download"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.yunx.app.action.STOP_DOWNLOAD"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_SPEED = "speed"
        private const val EXTRA_SHOW_SPEED = "show_speed"

        fun start(context: Context, title: String, progress: Int = 0) {
            start(context, title, progress, "", true)
        }

        fun update(context: Context, title: String, progress: Int, speed: String, showSpeed: Boolean) {
            start(context, title, progress, speed, showSpeed)
        }

        private fun start(context: Context, title: String, progress: Int, speed: String, showSpeed: Boolean) {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_SPEED, speed)
                .putExtra(EXTRA_SHOW_SPEED, showSpeed)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}
