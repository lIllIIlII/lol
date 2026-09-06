/*
 * 吸析At - iOS 风格弹窗（毛玻璃卡片 + iOS 排版 + iOS 按钮语言）。
 * 用于：欢迎弹窗 / 更新提示 / 安全提示等。替代全屏欢迎页。
 */

package com.yunx.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** iOS 系统蓝 */
val IosBlue = Color(0xFF007AFF)
val IosRed = Color(0xFFFF3B30)

/**
 * iOS 风格弹窗容器：
 * - 半透明遮罩可点击关闭（可选）；
 * - 毛玻璃卡片（预模糊壁纸中心取样 + 色罩 + 圆角 28dp）；
 * - 内容插槽 + 底部按钮插槽（水平排列、iOS 分隔样式由 IosDialogButton 自带）。
 */
@Composable
fun IosAlertDialog(
    onDismissRequest: (() -> Unit)? = null,
    dismissOnScrim: Boolean = true,
    width: androidx.compose.ui.unit.Dp = 304.dp,
    content: @Composable () -> Unit
) {
    val isDark = when (MaterialTheme.colorScheme.background.luminance()) {
        in 0.5f..1f -> false
        else -> true
    }
    Dialog(
        onDismissRequest = { if (dismissOnScrim) onDismissRequest?.invoke() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .clickable(interactionSource = interaction, indication = null) {
                    if (dismissOnScrim) onDismissRequest?.invoke()
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = width)
                    .liquidGlass(
                        shape = RoundedCornerShape(28.dp),
                        darkTheme = isDark,
                        tintAlpha = if (isDark) 0.42f else 0.70f,
                        borderAlpha = if (isDark) 0.45f else 0.85f,
                        alignToScreen = false
                    )
            ) {
                Column(
                    modifier = Modifier.padding(top = 22.dp, bottom = 14.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    content()
                }
            }
        }
    }
}

/** iOS 标题（17sp 半粗） */
@Composable
fun IosDialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF111111) else Color(0xFFF2F2F7)
    )
}

/** iOS 正文（13sp，次级灰） */
@Composable
fun IosDialogMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        textAlign = TextAlign.Center,
        color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF444449) else Color(0xFFD6D6DC)
    )
}

/**
 * iOS 风格按钮（填充式主按钮 / 纯文字次按钮）。
 * - filled：iOS 蓝圆角填充按钮（高 44dp，全宽）；
 * - text：iOS 蓝纯文字。
 */
@Composable
fun IosDialogButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean = true,
    filledColor: Color = IosBlue,
    textColor: Color = IosBlue,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .then(
                if (filled) {
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (enabled) filledColor else filledColor.copy(alpha = 0.4f))
                        .clickable(enabled = enabled) { onClick() }
                } else {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled) { onClick() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled) Color.White else textColor.copy(alpha = if (enabled) 1f else 0.4f),
            fontSize = if (filled) 16.sp else 15.sp,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** iOS 弹窗底部按钮行：主按钮全宽在上、次按钮文字在下（iOS Alert 纵向排布） */
@Composable
fun IosDialogActions(
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
    tertiary: Pair<String, () -> Unit>? = null,
    primaryEnabled: Boolean = true
) {
    Spacer(Modifier.height(18.dp))
    IosDialogButton(text = primary.first, onClick = primary.second, enabled = primaryEnabled)
    if (secondary != null) {
        Spacer(Modifier.height(8.dp))
        IosDialogButton(text = secondary.first, onClick = secondary.second, filled = false)
    }
    if (tertiary != null) {
        Spacer(Modifier.height(4.dp))
        IosDialogButton(
            text = tertiary.first,
            onClick = tertiary.second,
            filled = false,
            textColor = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF8E8E93) else Color(0xFF98989F)
        )
    }
}

/** 弹窗顶部圆形图标容器（毛玻璃浅罩） */
@Composable
fun IosDialogIcon(
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (isDark) 0.14f else 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** iOS 风格标签胶囊（次要信息） */
@Composable
fun IosTagChip(text: String, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(7.dp),
        color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 12.sp,
            color = if (isDark) Color(0xFFC9C9CF) else Color(0xFF5B5B60)
        )
    }
}

private fun Color.luminance(): Float {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
}
