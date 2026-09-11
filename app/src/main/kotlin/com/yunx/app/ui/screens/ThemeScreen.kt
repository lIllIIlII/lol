package com.yunx.app.ui.screens

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yunx.app.R
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.theme.GlassWallpaper
import com.yunx.app.ui.theme.HoneycombPreview
import com.yunx.app.ui.theme.ThemeController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val presetColors = listOf(
    "蓝色" to 0xFF415F91L,
    "靛蓝" to 0xFF3F51B5L,
    "紫色" to 0xFF6750A4L,
    "玫红" to 0xFFC2185BL,
    "红色" to 0xFFB3261EL,
    "橙色" to 0xFFF4631CL,
    "金黄" to 0xFFF9A825L,
    "绿色" to 0xFF38761DL,
    "青色" to 0xFF00897BL,
    "天蓝" to 0xFF0288D1L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onBack() }
    var showColorPicker by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    val expandDuration = 200

    val density = LocalDensity.current
    var contentHeightPx by remember { mutableIntStateOf(0) }
    val expandProgress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        expandProgress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        )
    }

    val effectiveColorMode = if (ThemeController.colorMode == 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        1
    } else {
        ThemeController.colorMode
    }

    val settingsRepo = remember { SettingsRepository(context) }
    var appIconVariant by remember { mutableStateOf(settingsRepo.appIconVariant) }

    val scope = rememberCoroutineScope()
    var hasCustomWallpaper by remember { mutableStateOf(GlassWallpaper.hasCustom(context)) }
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { GlassWallpaper.applyCustom(context, uri) }
                hasCustomWallpaper = GlassWallpaper.hasCustom(context)
                if (ok) {
                    SnackbarController.show("背景壁纸已应用")
                } else {
                    SnackbarController.show("所选文件不是有效图片，请重新选择")
                }
            }
        }
    }
    var iconExpanded by rememberSaveable { mutableStateOf(true) }
    val switchAppIcon: (Int) -> Unit = { variant ->
        val pm = context.packageManager
        val main = ComponentName(context, "com.yunx.app.MainActivity")
        val alias = ComponentName(context, "com.yunx.app.MainActivityIcon2")
        if (variant == 1) {
            pm.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(main, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        } else {
            pm.setComponentEnabledSetting(main, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        settingsRepo.appIconVariant = variant
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("主题与外观", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            SectionLabel("外观模式")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "选择应用的明暗外观",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf("跟随系统", "浅色", "深色")
                        modes.forEachIndexed { index, label ->
                            SmoothFilterChip(
                                selected = ThemeController.darkMode == index,
                                label = label,
                                onClick = { ThemeController.setDarkMode(context, index) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "主题色",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            AnimatedVisibility(
                                visible = !expanded,
                                enter = fadeIn(tween(200)) + expandVertically(tween(200), expandFrom = Alignment.Top),
                                exit = fadeOut(tween(200)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top)
                            ) {
                                Text(
                                    text = when {
                                        effectiveColorMode == 0 -> "动态色彩（跟随壁纸）"
                                        effectiveColorMode == 2 -> "自定义颜色"
                                        else -> "默认蓝色"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        val rotation by animateFloatAsState(
                            targetValue = if (expanded) 180f else 0f,
                            label = "arrow",
                            animationSpec = tween(expandDuration)
                        )
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.rotate(rotation)
                        )
                    }

                            val animatedHeightDp = with(density) { (contentHeightPx * expandProgress.value).toDp() }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (contentHeightPx > 0) Modifier.height(animatedHeightDp) else Modifier)
                                    .clipToBounds()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight(unbounded = true)
                                        .onSizeChanged { contentHeightPx = it.height }
                                        .graphicsLayer { alpha = expandProgress.value }
                                ) {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("动态色彩", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "从系统壁纸自动取色",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = ThemeController.colorMode == 0,
                                        onCheckedChange = { on ->
                                            ThemeController.setColorMode(context, if (on) 0 else 1)
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            AnimatedVisibility(visible = effectiveColorMode != 0) {
                                Column {
                                    Text(
                                        text = "主题颜色",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(top = 10.dp, bottom = 8.dp)
                                    ) {
                                        itemsIndexed(presetColors) { _, (name, color) ->
                                            val isSelected = (effectiveColorMode == 1 && color == 0xFF415F91L) ||
                                                (effectiveColorMode == 2 && ThemeController.seedColor == color)
                                            ColorSelectionItem(
                                                color = color,
                                                name = name,
                                                isSelected = isSelected,
                                                onClick = { ThemeController.setSeedColor(context, color) }
                                            )
                                        }
                                        item {
                                            val isCustomSelected = effectiveColorMode == 2 &&
                                                ThemeController.seedColor !in presetColors.map { it.second }
                                            CustomColorButton(
                                                isSelected = isCustomSelected,
                                                customColor = ThemeController.seedColor,
                                                onClick = {
                                                    ThemeController.setColorMode(context, 2)
                                                    showColorPicker = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("桌面图标")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { iconExpanded = !iconExpanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "桌面图标",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            AnimatedVisibility(
                                visible = !iconExpanded,
                                enter = fadeIn(tween(200)) + expandVertically(tween(200), expandFrom = Alignment.Top),
                                exit = fadeOut(tween(200)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top)
                            ) {
                                Text(
                                    text = if (appIconVariant == 1) "新图标" else "经典图标",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        val iconRotation by animateFloatAsState(
                            targetValue = if (iconExpanded) 180f else 0f,
                            label = "iconArrow",
                            animationSpec = tween(200)
                        )
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.rotate(iconRotation)
                        )
                    }
                    AnimatedVisibility(
                        visible = iconExpanded,
                        enter = fadeIn(tween(200)) + expandVertically(tween(200), expandFrom = Alignment.Top),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(150), shrinkTowards = Alignment.Top)
                    ) {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                AppIconOption(
                                    iconRes = R.drawable.icon,
                                    name = "经典图标",
                                    isSelected = appIconVariant == 0,
                                    onClick = {
                                        appIconVariant = 0
                                        switchAppIcon(0)
                                    }
                                )
                                AppIconOption(
                                    iconRes = R.drawable.icon2,
                                    name = "新图标",
                                    isSelected = appIconVariant == 1,
                                    onClick = {
                                        appIconVariant = 1
                                        switchAppIcon(1)
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Android 12+ 立即生效；部分设备需回到桌面或重启启动器后查看。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("网盘视图")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "「网盘」页的布局样式",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmoothFilterChip(
                            selected = ThemeController.driveViewStyle == 0,
                            label = "列表卡片",
                            onClick = { ThemeController.setDriveViewStyle(context, 0) },
                            modifier = Modifier.weight(1f)
                        )
                        SmoothFilterChip(
                            selected = ThemeController.driveViewStyle == 1,
                            label = "蜂窝六边形",
                            onClick = { ThemeController.setDriveViewStyle(context, 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    HoneycombPreview(selected = ThemeController.driveViewStyle == 1)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "蜂窝式视图：网盘以六边形蜂巢排布，更紧凑直观。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("背景壁纸")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val previewBmp = GlassWallpaper.sharp
                    if (previewBmp != null) {
                        Image(
                            bitmap = previewBmp,
                            contentDescription = "当前背景预览",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Wallpaper,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自定义背景图片",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (hasCustomWallpaper) "当前使用自定义壁纸" else "当前使用内置壁纸",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                wallpaperPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (hasCustomWallpaper) "更换图片" else "选择图片")
                        }
                        if (hasCustomWallpaper) {
                            OutlinedButton(
                                onClick = {
                                    GlassWallpaper.clearCustom(context)
                                    hasCustomWallpaper = false
                                    SnackbarController.show("已恢复内置壁纸")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("恢复默认")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "选择相册中的图片作为应用背景，玻璃面板会自动跟随取色。图片仅保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = ThemeController.seedColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                ThemeController.setSeedColor(context, color)
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun SmoothFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = 200
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = duration, easing = LinearEasing),
        label = "container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = duration, easing = LinearEasing),
        label = "content"
    )
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ColorSelectionItem(
    color: Long,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(color.toInt()))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CustomColorButton(
    isSelected: Boolean,
    customColor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) Color(customColor.toInt())
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = null,
                tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "自定义",
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit
) {
    val initialHsv = colorToHsv(Color(initialColor.toInt()))
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val currentColor = Color.hsv(hue, saturation, value)
    var hexInput by remember(currentColor) {
        mutableStateOf(colorToHex(currentColor).removePrefix("#"))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BasicTextField(
                            value = hexInput,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                                hexInput = filtered
                                if (filtered.length == 6) {
                                    runCatching {
                                        val color = hexToColor(filtered)
                                        val hsv = colorToHsv(color)
                                        hue = hsv[0]
                                        saturation = hsv[1]
                                        value = hsv[2]
                                    }
                                }
                            },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.width(140.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(currentColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    SatValPanel(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onValChange = { s, v ->
                            saturation = s
                            value = v
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    VerticalHueSlider(
                        hue = hue,
                        onHueChange = { hue = it },
                        modifier = Modifier
                            .width(24.dp)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onColorSelected(currentColor.toArgb().toLong() and 0xFFFFFFFFL) }) {
                        Text("应用")
                    }
                }
            }
        }
    }
}

@Composable
private fun SatValPanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onValChange: (Float, Float) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onValChange(
                            (offset.x / size.width).coerceIn(0f, 1f),
                            1f - (offset.y / size.height).coerceIn(0f, 1f)
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onValChange(
                            (change.position.x / size.width).coerceIn(0f, 1f),
                            1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        )
                    }
                }
        ) {
            drawRect(color = Color.hsv(hue, 1f, 1f))
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

            val x = saturation * size.width
            val y = (1f - value) * size.height
            val cursorSize = 14f
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(x - cursorSize / 2, y - cursorSize / 2),
                size = Size(cursorSize, cursorSize),
                style = Stroke(3f)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(x - cursorSize / 2, y - cursorSize / 2),
                size = Size(cursorSize, cursorSize),
                style = Stroke(1.5f)
            )
        }
    }
}

@Composable
private fun VerticalHueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onHueChange((offset.y / size.height * 360f).coerceIn(0f, 360f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onHueChange((change.position.y / size.height * 360f).coerceIn(0f, 360f))
                    }
                }
        ) {
            val colors = (0..360 step 10).map { Color.hsv(it.toFloat(), 1f, 1f) }
            drawRect(brush = Brush.verticalGradient(colors = colors))

            val y = (hue / 360f) * size.height
            val barHeight = 6f
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, y - barHeight / 2),
                size = Size(size.width, barHeight),
                style = Stroke(2f)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(0f, y - barHeight / 2),
                size = Size(size.width, barHeight),
                style = Stroke(1f)
            )
        }
    }
}

@Composable
private fun AppIconOption(
    iconRes: Int,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "已选择",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

private fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv
}

private fun colorToHex(color: Color): String {
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}

private fun hexToColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    return Color(
        red = clean.substring(0, 2).toInt(16) / 255f,
        green = clean.substring(2, 4).toInt(16) / 255f,
        blue = clean.substring(4, 6).toInt(16) / 255f
    )
}
