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

    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        enqueueStartupDialogs()
        setContent {
            ComposeEmptyActivityTheme {
                MainScreen()
                StartupDialogHost()
            }
        }
    }

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
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            StartupDialogQueue.enqueue(StartupDialogKind.NOTIFICATION)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
