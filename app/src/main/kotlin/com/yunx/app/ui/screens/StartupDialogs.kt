package com.yunx.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class StartupDialogKind {
    WELCOME,

    SAFETY,

    NOTIFICATION
}

object StartupDialogQueue {
    internal var current by mutableStateOf<StartupDialogKind?>(null)
        private set

    private val waiting = ArrayDeque<StartupDialogKind>()

    val isBusy: Boolean get() = current != null

    fun enqueue(kind: StartupDialogKind) {
        if (current == null) {
            current = kind
        } else {
            if (current == kind) return
            waiting.removeAll { it == kind }
            waiting.addLast(kind)
        }
    }

    fun complete() {
        current = waiting.removeFirstOrNull()
    }
}

@Composable
fun StartupDialogHost() {
    when (StartupDialogQueue.current) {
        StartupDialogKind.WELCOME -> WelcomeDialog(
            onFinish = {
                StartupDialogQueue.complete()
            }
        )

        StartupDialogKind.SAFETY -> SafetyNoticeDialog(
            onDismissed = {
                StartupDialogQueue.complete()
            }
        )

        StartupDialogKind.NOTIFICATION -> StartupNotificationGuideDialog(
            onDone = {
                StartupDialogQueue.complete()
            }
        )

        null -> Unit
    }
}

@Composable
internal fun StartupNotificationGuideDialog(onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { androidx.compose.material3.Text("开启通知权限") },
        text = {
            androidx.compose.material3.Text(
                "下载进度需要通知权限才能显示在通知栏。当前通知已被关闭，是否前往系统设置开启？"
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    onDone()
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }.onFailure {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                }
            ) { androidx.compose.material3.Text("去开启") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDone) { androidx.compose.material3.Text("暂不") }
        }
    )
}
