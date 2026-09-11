package com.yunx.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import com.yunx.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onPreviewOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onBack() }
    val pkgInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = pkgInfo?.versionName ?: "1.0"
    val versionCode = pkgInfo?.versionCode ?: 1

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("关于吸析At", style = MaterialTheme.typography.titleLarge) },
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppBanner(versionName = versionName, versionCode = versionCode)

            InfoCard(
                icon = Icons.Outlined.Cloud,
                title = "应用简介",
                description = "吸析At 是一款网盘分享链接解析与高速下载工具。" +
                    "粘贴分享链接，登录网盘账号后即可浏览分享内容并直接高速下载文件。"
            )

            PlatformCard()

            FeatureCard()

            TechCard()

            DisclaimerCard()

            Text(
                text = "开源协议 AGPL-3.0 · 源码与更新托管于 GitHub · 完全免费，禁止倒卖",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            PreviewOnboardingCard(onClick = onPreviewOnboarding)

            GitHubCard(context)

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "吸析At v$versionName · Made with SyTren/Ccat",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun AppBanner(versionName: String, versionCode: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.7778f)
                .clip(RoundedCornerShape(22.dp))
        ) {
            Image(
                painter = painterResource(R.drawable.about_banner),
                contentDescription = "吸析At 主视觉",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.04f),
                            0.66f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "吸析At",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "v$versionName ($versionCode) · 网盘链接解析与高速下载",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, description: String) {
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            CardIcon(icon)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformCard() {
    val platforms = listOf(
        "夸克网盘" to Icons.Outlined.Cloud,
        "UC 网盘" to Icons.Outlined.Storage,
        "迅雷网盘" to Icons.Outlined.Speed,
        "百度网盘" to Icons.Outlined.Link,
        "139 网盘" to Icons.Outlined.Cloud,
        "123云盘" to Icons.Outlined.Cloud,
        "蓝奏云" to Icons.Outlined.Link,
        "蓝奏云优享版" to Icons.Outlined.Link,
        "奶牛快传" to Icons.Outlined.Link,
        "小飞机网盘" to Icons.Outlined.Link,
        "城通网盘" to Icons.Outlined.Link,
        "文叔叔" to Icons.Outlined.Link
    )
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon(Icons.Outlined.Storage)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "支持平台",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            platforms.forEach { (name, icon) ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard() {
    val features = listOf(
        "一键解析分享链接" to "夸克 / UC / 迅雷 / 百度 / 139 / 123 分享直链识别",
        "高速分片下载" to "多线程并发 + 断点续传，充分利用带宽",
        "取链即删" to "转存后立即清理，不留残留",
        "凭证本地化" to "Cookie 加密落库，仅存本机"
    )
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon(Icons.Outlined.CheckCircle)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "功能特性",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        features.forEach { (title, desc) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechCard() {
    val techs = listOf("Kotlin", "Jetpack Compose", "Material 3", "Room", "OkHttp", "KSP")
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIcon(Icons.Outlined.Code)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "技术栈",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            techs.forEach { name ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DisclaimerCard() {
    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            CardIcon(Icons.Outlined.Shield)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "免责声明",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "本应用仅供个人学习与技术交流使用，请勿用于任何商业用途。" +
                        "下载内容版权归原作者所有，请于下载后 24 小时内删除。" +
                        "使用本应用产生的任何后果由使用者自行承担。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun PreviewOnboardingCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "重新预览欢迎界面",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "重新展示首次启动引导页",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun CardIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun GitHubCard(context: android.content.Context) {
    Card(
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lIllIIlII/lol"))
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "开源网址",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "github.com/lIllIIlII/lol（源码 · 更新 · 反馈）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
