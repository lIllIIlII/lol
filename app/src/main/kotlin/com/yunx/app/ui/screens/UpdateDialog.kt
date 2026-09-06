/*
 * 吸析At - 发现新版本弹窗（iOS 风格 + 液态玻璃）。
 * 非强制：稍后 / 忽略本次 / 应用内下载更新。
 */

package com.yunx.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.data.update.UpdateChecker
import com.yunx.app.ui.theme.IosAlertDialog
import com.yunx.app.ui.theme.IosDialogActions
import com.yunx.app.ui.theme.IosDialogButton
import com.yunx.app.ui.theme.IosDialogIcon
import com.yunx.app.ui.theme.IosDialogMessage
import com.yunx.app.ui.theme.IosDialogTitle
import com.yunx.app.ui.theme.IosTagChip

@Composable
fun UpdateDialog(
    currentVersion: String,
    release: UpdateChecker.Release,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
    downloading: Boolean = false
) {
    IosAlertDialog(
        onDismissRequest = onLater,
        dismissOnScrim = true
    ) {
        IosDialogIcon {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        IosDialogTitle("发现新版本")
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IosTagChip("v${release.version.removePrefix("v").removePrefix("V")}")
            Spacer(Modifier.width(8.dp))
            IosDialogMessage("当前 v$currentVersion")
        }
        if (release.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 2.dp)
            ) {
                Text(
                    text = release.notes,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Start,
                    color = if (MaterialTheme.colorScheme.background.luminanceCompat() > 0.5f) Color(0xFF444449) else Color(0xFFD6D6DC)
                )
            }
        }
        IosDialogActions(
            primary = (if (downloading) "下载中…" else "下载更新") to onDownload,
            secondary = "稍后" to onLater,
            tertiary = "忽略此版本" to onIgnore,
            primaryEnabled = !downloading && release.downloadUrl.isNotBlank()
        )
    }
}

private fun Color.luminanceCompat(): Float {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
}
