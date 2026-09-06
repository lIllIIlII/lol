/*
 * 吸析At - 底部胶囊导航栏（液态玻璃）。
 * 悬浮胶囊造型：玻璃底 + 圆角 32dp（全圆头）+ 选中指示器（Material3 胶囊）+ 弹性切换。
 * 全部玻璃绘制在 clip(shape) 内 → 圆角边缘无任何突出（修复旧版边角溢出）。
 */

package com.yunx.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yunx.app.ui.navigation.MainTab

@Composable
fun GlassCapsuleNav(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.let { bg ->
        (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) <= 0.5f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .liquidGlass(
                    shape = RoundedCornerShape(33.dp),
                    darkTheme = isDark,
                    tintAlpha = if (isDark) 0.22f else 0.5f,
                    borderAlpha = if (isDark) 0.65f else 0.95f
                )
                .clip(RoundedCornerShape(33.dp))
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == currentTab
                GlassNavItem(
                    tab = tab,
                    selected = selected,
                    isDark = isDark,
                    onClick = { if (!selected) onTabSelected(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GlassNavItem(
    tab: MainTab,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else {
            if (isDark) Color(0xFFB8B8C0) else Color(0xFF6E6E73)
        },
        animationSpec = tween(180), label = "navTint"
    )
    val labelTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else {
            if (isDark) Color(0xFFA8A8B0) else Color(0xFF8A8A8F)
        },
        animationSpec = tween(180), label = "navLabel"
    )
    // 选中指示器：尺寸弹性过渡
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 46.dp else 34.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "navInd"
    )
    Box(
        modifier = modifier
            .height(66.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 指示器底（选中时着色，弹性尺寸过渡）
            Box(
                modifier = Modifier
                    .size(width = indicatorWidth, height = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.title,
                    modifier = Modifier.size(22.dp),
                    tint = iconTint
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = tab.title,
                color = labelTint,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
