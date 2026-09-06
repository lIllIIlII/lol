/*
 * 吸析At - 启动弹窗队列。
 *
 * 修复：欢迎弹窗 / 安全提示 / 通知引导 / 更新弹窗原先同时弹出互相重叠。
 * 方案：全局单例队列（进程级 Compose 状态），一次只展示一个，
 * 关闭一个自动弹下一个；更新检测等次要弹窗排队等待。
 */

package com.yunx.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 启动弹窗种类（展示顺序 = 枚举声明顺序） */
enum class StartupDialogKind {
    /** 欢迎弹窗（首次启动 / 关于页重看） */
    WELCOME,

    /** 安全提示（首次启动防假冒提示） */
    SAFETY,

    /** 通知被禁用引导 */
    NOTIFICATION
}

/**
 * 全局启动弹窗队列：
 * - enqueue：加入队尾；若当前空闲则立即展示；
 * - complete：当前弹窗关闭，自动弹下一个；
 * - isBusy：队列是否还有弹窗未展示完（更新检测等错峰用）。
 */
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

    /** 当前弹窗关闭，进入下一个 */
    fun complete() {
        current = waiting.removeFirstOrNull()
    }
}

/**
 * 启动弹窗宿主：渲染队列当前弹窗（一次一个，绝不重叠）。
 * MainActivity 在 MainScreen 之后调用。
 */
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

/** 通知被禁用时的引导弹窗：跳系统应用通知设置页 */
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
