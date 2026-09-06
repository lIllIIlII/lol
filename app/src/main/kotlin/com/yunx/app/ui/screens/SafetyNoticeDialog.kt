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

package com.yunx.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.ui.theme.IosAlertDialog
import com.yunx.app.ui.theme.IosDialogActions
import com.yunx.app.ui.theme.IosDialogButton
import com.yunx.app.ui.theme.IosDialogIcon
import com.yunx.app.ui.theme.IosDialogMessage
import com.yunx.app.ui.theme.IosDialogTitle
import com.yunx.app.ui.theme.IosTagChip
import com.yunx.app.util.TextCipher
import kotlinx.coroutines.delay

/**
 * 安全提示弹窗（首次启动展示一次）。
 * @param onDismissed 弹窗关闭回调（弹窗队列用它继续下一个弹窗）
 */
@Composable
fun SafetyNoticeDialog(onDismissed: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lockSeconds = 2
    var visible by remember { mutableStateOf(!isAcknowledged(context)) }
    var seconds by remember { mutableIntStateOf(lockSeconds) }

    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
    }

    if (!visible) {
        // 已确认过（或直接关闭）：立即通知队列继续
        LaunchedEffect(Unit) { onDismissed?.invoke() }
        return
    }

    // iOS 风格 + 液态玻璃安全提示（首次启动，确认一次后不再弹出）
    IosAlertDialog(
        onDismissRequest = {
            if (seconds <= 0) {
                visible = false
                acknowledge(context)
                onDismissed?.invoke()
            }
        }
    ) {
        IosDialogIcon {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        IosDialogTitle(TextCipher.dTitle)
        Spacer(Modifier.height(8.dp))
        IosTagChip(TextCipher.dOfficial)
        Spacer(Modifier.height(12.dp))
        IosDialogMessage(TextCipher.dBody)
        Spacer(Modifier.height(12.dp))
        // 官方地址卡片（玻璃内衬）
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = if (MaterialTheme.colorScheme.background.luminanceCompat() > 0.5f) 0.45f else 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Text(
                    text = TextCipher.dUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        IosDialogActions(
            primary = (if (seconds > 0) TextCipher.dCountdown.replace("%d", seconds.toString()) else TextCipher.dBtn) to {
                visible = false
                acknowledge(context)
                onDismissed?.invoke()
            },
            secondary = (TextCipher.dCopy) to { copyUrl(context) },
            primaryEnabled = seconds <= 0
        )
    }
}

private fun Color.luminanceCompat(): Float {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
}

private fun isAcknowledged(context: Context): Boolean {
    return runCatching {
        context.getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
            .getBoolean(TextCipher.pNoticeFlag, false)
    }.getOrDefault(false)
}

private fun acknowledge(context: Context) {
    runCatching {
        context.getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(TextCipher.pNoticeFlag, true)
            .apply()
    }
}

private fun copyUrl(context: Context) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(TextCipher.dUrl, TextCipher.dUrl))
        Toast.makeText(context, TextCipher.dCopied, Toast.LENGTH_SHORT).show()
    }
}
