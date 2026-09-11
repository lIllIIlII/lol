package com.yunx.app.data.prefs

import android.content.Context
import com.yunx.app.data.download.DownloadPlatform

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)

    var downloadThreads: Int
        get() = downloadThreadsFor(DownloadPlatform.GENERIC)
        set(value) = setDownloadThreads(DownloadPlatform.GENERIC, value)

    fun downloadThreadsFor(platform: String): Int {
        if (platform == DownloadPlatform.XUNLEI) return XUNLEI_DOWNLOAD_THREADS
        return prefs.getInt(prefsKey(platform), DEFAULT_DOWNLOAD_THREADS)
            .coerceIn(1, MAX_DOWNLOAD_THREADS)
    }

    fun setDownloadThreads(platform: String, value: Int) {
        if (platform == DownloadPlatform.XUNLEI) return
        prefs.edit().putInt(prefsKey(platform), value.coerceIn(1, MAX_DOWNLOAD_THREADS)).apply()
    }

    private fun prefsKey(platform: String): String =
        if (platform.isBlank() || platform == DownloadPlatform.GENERIC) "download_threads"
        else "download_threads_$platform"

    var downloadDirUri: String?
        get() = prefs.getString("download_dir_uri", null)
        set(value) {
            prefs.edit().putString("download_dir_uri", value).apply()
        }

    var maxConcurrentDownloads: Int
        get() = prefs.getInt("max_concurrent_downloads", DEFAULT_MAX_CONCURRENT_DOWNLOADS)
        set(value) {
            prefs.edit().putInt("max_concurrent_downloads", value.coerceIn(1, 10)).apply()
        }

    var downloadSpeedLimit: Long
        get() = prefs.getLong("download_speed_limit", 0L)
        set(value) {
            prefs.edit().putLong("download_speed_limit", value.coerceAtLeast(0L)).apply()
        }

    var downloadRetryCount: Int
        get() = prefs.getInt("download_retry_count", DEFAULT_DOWNLOAD_RETRY_COUNT)
        set(value) {
            prefs.edit().putInt("download_retry_count", value.coerceIn(0, 10)).apply()
        }

    var keepDownloadWhenLocked: Boolean
        get() = prefs.getBoolean("keep_download_when_locked", true)
        set(value) {
            prefs.edit().putBoolean("keep_download_when_locked", value).apply()
        }

    var notificationShowSpeed: Boolean
        get() = prefs.getBoolean("notification_show_speed", true)
        set(value) {
            prefs.edit().putBoolean("notification_show_speed", value).apply()
        }

    var appIconVariant: Int
        get() = prefs.getInt("app_icon_variant", 0)
        set(value) {
            prefs.edit().putInt("app_icon_variant", value.coerceIn(0, 1)).apply()
        }

    var ignoreSslCert: Boolean
        get() = prefs.getBoolean("ignore_ssl_cert", false)
        set(value) {
            prefs.edit().putBoolean("ignore_ssl_cert", value).apply()
        }

    var baiduLimitHintDismissed: Boolean
        get() = prefs.getBoolean("baidu_limit_hint_dismissed", false)
        set(value) {
            prefs.edit().putBoolean("baidu_limit_hint_dismissed", value).apply()
        }

    var darkMode: Int
        get() = prefs.getInt("dark_mode", 0)
        set(value) {
            prefs.edit().putInt("dark_mode", value.coerceIn(0, 2)).apply()
        }

    var themeColorMode: Int
        get() = prefs.getInt("theme_color_mode", 0)
        set(value) {
            prefs.edit().putInt("theme_color_mode", value.coerceIn(0, 2)).apply()
        }

    var themeSeedColor: Long
        get() = prefs.getLong("theme_seed_color", DEFAULT_SEED_COLOR)
        set(value) {
            prefs.edit().putLong("theme_seed_color", value).apply()
        }

    var customWallpaper: String?
        get() = prefs.getString("custom_wallpaper", null)
        set(value) {
            prefs.edit().putString("custom_wallpaper", value).apply()
        }

    var askDownloadAfterSave: Boolean
        get() = prefs.getBoolean("ask_download_after_save", true)
        set(value) {
            prefs.edit().putBoolean("ask_download_after_save", value).apply()
        }

    var driveViewStyle: Int
        get() = prefs.getInt("drive_view_style", 0)
        set(value) {
            prefs.edit().putInt("drive_view_style", value.coerceIn(0, 1)).apply()
        }

    companion object {
        const val DEFAULT_DOWNLOAD_THREADS = 32
        const val MAX_DOWNLOAD_THREADS = 512
        const val XUNLEI_DOWNLOAD_THREADS = 8
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 1
        const val DEFAULT_DOWNLOAD_RETRY_COUNT = 3

        const val DEFAULT_SEED_COLOR = 0xFF415F91L
    }
}
