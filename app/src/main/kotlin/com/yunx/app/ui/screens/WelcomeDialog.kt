/*
 * 吸析At - 欢迎弹窗（iOS 风格，替代旧版全屏欢迎页）。
 * 首次启动展示：应用图标 + 简介 + 三条核心能力 + 开始使用。
 */

package com.yunx.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.R
import com.yunx.app.ui.theme.IosAlertDialog
import com.yunx.app.ui.theme.IosDialogActions
import com.yunx.app.ui.theme.IosDialogMessage
import com.yunx.app.ui.theme.IosDialogTitle

@Composable
fun WelcomeDialog(
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    IosAlertDialog(
        onDismissRequest = null,
        dismissOnScrim = false
    ) {
        // 应用图标（新 logo）
        Image(
            painter = painterResource(R.drawable.icon),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(14.dp))
        IosDialogTitle("欢迎使用 吸析At")
        Spacer(Modifier.height(6.dp))
        IosDialogMessage("网盘分享解析与高速下载工具\n完全免费 · 简洁纯净")

        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            WelcomeFeature(Icons.Outlined.Link, "一键解析", "夸克 / UC / 迅雷 / 百度 / 139 / 123 / 蓝奏云 / 奶牛 / 小飞机")
            WelcomeFeature(Icons.Outlined.Speed, "高速下载", "多线程分片下载，不限速")
            WelcomeFeature(Icons.Outlined.CloudDownload, "转存收藏", "登录网盘后一键转存到自己的云盘")
        }

        IosDialogActions(
            primary = ("开始使用") to {
                // 自管首次引导标记（队列宿主无需关心存储）
                runCatching {
                    context.getSharedPreferences("yunx_prefs", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("onboarding_shown", true)
                        .apply()
                }
                onFinish()
            }
        )
    }
}

@Composable
private fun WelcomeFeature(icon: ImageVector, title: String, subtitle: String) {
    val isDark = MaterialTheme.colorScheme.background.lumin() <= 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF111111)
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = if (isDark) Color(0xFFB0B0B8) else Color(0xFF6B6B70)
            )
        }
    }
}

private fun Color.lumin(): Float {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
}
