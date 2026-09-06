/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yunx.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yunx.app.crash.CrashHandler
import com.yunx.app.ui.MainScreen
import com.yunx.app.ui.screens.StartupDialogHost
import com.yunx.app.ui.screens.StartupDialogKind
import com.yunx.app.ui.screens.StartupDialogQueue
import com.yunx.app.ui.theme.ComposeEmptyActivityTheme
import com.yunx.app.util.ArchiveProbe
import com.yunx.app.util.TextCipher

class MainActivity : ComponentActivity() {

    // Android 13+：下载前台服务通知需要动态授权，首次启动即引导（无论通知栏开关状态，授权后通知才可见）
    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用内主题设置：始终深色/浅色时提前切换窗口主题，避免冷启动闪错背景色
        // （values-night 只跟随系统；应用内「始终深色」但系统浅色时，需显式使用深色窗口主题）
        val darkModePref = getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
            .getInt("dark_mode", 0)
        when (darkModePref) {
            1 -> setTheme(R.style.Theme_ComposeEmptyActivity_Light)
            2 -> setTheme(R.style.Theme_ComposeEmptyActivity_Dark)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        runCatching {
            val hits = ArchiveProbe.fast(this).toMutableList()
            if (entryMismatch(this)) hits.add(4)
            if (hits.isNotEmpty()) {
                CrashHandler.terminate(hits.joinToString(","))
            }
        }
        // 启动弹窗排队（一次只弹一个，修复欢迎/安全提示/通知引导同时弹出重叠）：
        // 欢迎弹窗 → 安全提示 → 通知引导，关闭一个自动弹下一个
        enqueueStartupDialogs()
        setContent {
            ComposeEmptyActivityTheme {
                MainScreen()
                StartupDialogHost()
            }
        }
    }

    /** 首次启动的弹窗按优先级入队（后续更新检测弹窗在 MainScreen 内等待队列空闲） */
    private fun enqueueStartupDialogs() {
        val onboardingShown = runCatching {
            getSharedPreferences("yunx_prefs", Context.MODE_PRIVATE)
                .getBoolean("onboarding_shown", false)
        }.getOrDefault(false)
        if (!onboardingShown) {
            StartupDialogQueue.enqueue(StartupDialogKind.WELCOME)
        }
        val safetyAcknowledged = runCatching {
            getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
                .getBoolean(TextCipher.pNoticeFlag, false)
        }.getOrDefault(false)
        if (!safetyAcknowledged) {
            StartupDialogQueue.enqueue(StartupDialogKind.SAFETY)
        }
        // 通知被系统/用户关闭时（任意版本，含国产 ROM 默认关闭）引导开启
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            StartupDialogQueue.enqueue(StartupDialogKind.NOTIFICATION)
        }
    }

    /** 通知权限：Android 13+ 先申请运行时权限；被系统关闭时入队引导弹窗 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Android 13+ 未授权：直接弹运行时授权框（授权后通知即可用，无需再引导）
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
