package com.yunx.app.ui.screens
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunx.app.data.backup.AuthBackupManager
import com.yunx.app.data.backup.AuthCrypto
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.download.DownloadSaver
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.data.update.UpdateChecker
import com.yunx.app.ui.SnackbarController
import com.yunx.app.util.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val threadOptions = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512)

private data class ThreadPlatform(val platform: String, val label: String)

private val threadPlatforms = listOf(
    ThreadPlatform(DownloadPlatform.QUARK, "夸克网盘"),
    ThreadPlatform(DownloadPlatform.UC, "UC 网盘"),
    ThreadPlatform(DownloadPlatform.XUNLEI, "迅雷网盘"),
    ThreadPlatform(DownloadPlatform.BAIDU, "百度网盘"),
    ThreadPlatform(DownloadPlatform.C139, "139 网盘"),
    ThreadPlatform(DownloadPlatform.PAN123, "123 云盘"),
)

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    scrollBehavior: TopAppBarScrollBehavior,
    onThemeClick: () -> Unit,
    onAboutClick: () -> Unit,
    onSupportClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    backupManager: AuthBackupManager,
    onDownloadUpdateApk: (url: String, fileName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showThreadsDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var updateRelease by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var showExportAuthDialog by remember { mutableStateOf(false) }
    var pendingImportContent by remember { mutableStateOf<String?>(null) }
    var showImportAuthDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var selectedThreadPlatform by remember { mutableStateOf(threadPlatforms.first()) }
    var showPlatformThreadDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    var downloadDirUri by remember { mutableStateOf(settingsRepo.downloadDirUri) }
    var showDevMenu by remember { mutableStateOf(false) }
    var maxConcurrent by remember { mutableStateOf(settingsRepo.maxConcurrentDownloads) }
    var speedLimitBps by remember { mutableStateOf(settingsRepo.downloadSpeedLimit) }
    var retryCount by remember { mutableStateOf(settingsRepo.downloadRetryCount) }
    var showConcurrencyDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showRetryDialog by remember { mutableStateOf(false) }
    var keepLocked by remember { mutableStateOf(settingsRepo.keepDownloadWhenLocked) }
    var showSpeed by remember { mutableStateOf(settingsRepo.notificationShowSpeed) }
    var askDownloadAfterSave by remember { mutableStateOf(settingsRepo.askDownloadAfterSave) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notifyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (granted) {
            showSpeed = true
            settingsRepo.notificationShowSpeed = true
        }
    }
    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            settingsRepo.downloadDirUri = uri.toString()
            downloadDirUri = uri.toString()
            SnackbarController.show("下载保存目录已更新")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                try {
                    val text = runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                    if (text == null) {
                        SnackbarController.show("读取文件失败")
                        return@launch
                    }
                    if (AuthCrypto.isEncrypted(text)) {
                        pendingImportContent = text
                        showImportAuthDialog = true
                    } else {
                        val count = runCatching {
                            withContext(Dispatchers.IO) { backupManager.importJson(text) }
                        }.getOrElse { e ->
                            SnackbarController.show("导入失败：${e.message}")
                            return@launch
                        }
                        SnackbarController.show("已恢复 $count 个平台的认证信息")
                    }
                } finally {
                    isImporting = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionLabel("下载")
        SettingsItem(
            icon = Icons.Outlined.Tune,
            title = "下载线程数",
            description = "按网盘分别设置分片并发数（默认 32，最高 512）",
            onClick = { showThreadsDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            icon = Icons.Outlined.FolderOpen,
            title = "下载保存目录",
            description = downloadDirUri?.let { "已自定义：${DownloadSaver.safDirDisplay(it)}" }
                ?: "系统默认 Download（点击自定义）",
            onClick = { dirLauncher.launch(null) },
            trailing = if (downloadDirUri != null) {
                {
                    TextButton(
                        onClick = {
                            downloadDirUri = null
                            settingsRepo.downloadDirUri = null
                            SnackbarController.show("已恢复默认下载目录")
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "恢复默认",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                null
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            icon = Icons.Outlined.Layers,
            title = "最大同时下载任务数",
            description = "同时下载 $maxConcurrent 个任务（限制后台并发，避免占满带宽）",
            onClick = { showConcurrencyDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            icon = Icons.Outlined.Speed,
            title = "下载速度限制",
            description = speedLimitText(speedLimitBps),
            onClick = { showSpeedDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            icon = Icons.Outlined.Refresh,
            title = "失败自动重试",
            description = if (retryCount == 0) "失败后不自动重试" else "失败后自动重试 $retryCount 次（断点续传）",
            onClick = { showRetryDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            icon = Icons.Outlined.Power,
            title = "锁屏后保持下载",
            description = "开启后下载时获取 WakeLock 维持网络，并可加入「忽略电池优化」白名单",
            onClick = {
                keepLocked = !keepLocked
                settingsRepo.keepDownloadWhenLocked = keepLocked
                if (keepLocked) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (pm?.isIgnoringBatteryOptimizations(context.packageName) != true) {
                        showBatteryDialog = true
                    }
                }
            },
            trailing = { Switch(checked = keepLocked, onCheckedChange = null) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            icon = Icons.Outlined.Notifications,
            title = "通知栏下载进度",
            description = when {
                !notificationsEnabled ->
                    "通知未开启，下载通知将不可见（点击去开启）"
                showSpeed -> "完整通知：进度条 + 下载速度"
                else -> "仅显示通知（隐藏下载速度）"
            },
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!notificationsEnabled) {
                    openNotificationSettings(context)
                } else {
                    showSpeed = !showSpeed
                    settingsRepo.notificationShowSpeed = showSpeed
                }
            },
            trailing = { Switch(checked = showSpeed, onCheckedChange = null) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("外观")
        SettingsItem(
            icon = Icons.Outlined.Palette,
            title = "主题与外观",
            description = "主题色、动态色彩与深色模式",
            onClick = onThemeClick
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsItem(
            icon = Icons.Outlined.CloudDownload,
            title = "转存后询问下载",
            description = if (askDownloadAfterSave) "转存到网盘后弹窗询问是否立即下载" else "转存后不询问（可去网盘页手动下载）",
            onClick = {
                askDownloadAfterSave = !askDownloadAfterSave
                settingsRepo.askDownloadAfterSave = askDownloadAfterSave
            },
            trailing = { Switch(checked = askDownloadAfterSave, onCheckedChange = null) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("通用")
        SettingsItem(
            icon = Icons.Outlined.SystemUpdate,
            title = "检查更新",
            description = "启动时自动检测，也可手动检查新版本",
            onClick = {
                scope.launch {
                    SnackbarController.show("正在检查更新…")
                    val release = runCatching { UpdateChecker.fetchLatestRelease(context) }.getOrNull()
                    val current = UpdateChecker.currentVersion(context)
                    if (release == null) {
                        SnackbarController.show("检查更新失败，请检查网络")
                    } else if (UpdateChecker.compareVersions(release.version, current) > 0) {
                        updateRelease = release
                    } else {
                        SnackbarController.show("已是最新版本")
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem(
            icon = Icons.Outlined.Article,
            title = "导出日志",
            description = "导出崩溃日志与应用信息，便于排查问题",
            onClick = { showLogDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem(
            icon = Icons.Outlined.Mail,
            title = "反馈联系（原汇报日志）",
            description = "扫微信 / QQ 二维码加开发者好友，直接反馈问题",
            onClick = onFeedbackClick
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem(
            icon = Icons.Outlined.Code,
            title = "开源网址",
            description = "github.com/lIllIIlII/lol",
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lIllIIlII/lol"))
                    )
                }
            },
            trailing = {
                Icon(
                    Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("网盘认证")
        SettingsItem(
            icon = Icons.Outlined.Backup,
            title = "导出网盘认证",
            description = "使用至少 8 位口令加密 Cookie/JWT 后导出",
            onClick = { showExportAuthDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem(
            icon = Icons.Outlined.Restore,
            title = "导入网盘认证",
            description = "选择加密或明文的认证备份文件，恢复网盘登录",
            onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("关于")
        SettingsItem(
            icon = Icons.Outlined.Info,
            title = "关于吸析",
            description = "版本信息、支持平台与技术说明",
            onClick = onAboutClick,
            onLongClick = { showDevMenu = true }
        )

        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem(
            icon = Icons.Outlined.VolunteerActivism,
            title = "支持开发",
            description = "微信扫码捐赠，支持项目持续维护",
            onClick = onSupportClick
        )
    }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("导出日志") },
            text = {
                Column {
                    Text(
                        text = "选择日志导出方式：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showLogDialog = false
                            scope.launch {
                                val file = withContext(Dispatchers.IO) { LogExporter.export(context) }
                                if (file != null && LogExporter.share(context, file)) {
                                    SnackbarController.show("日志已分享")
                                } else {
                                    SnackbarController.show("导出日志失败")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("分享日志（发送到其他应用）")
                    }
                    TextButton(
                        onClick = {
                            showLogDialog = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    LogExporter.saveToDownloads(context)
                                }
                                SnackbarController.show(if (ok) "已保存到下载目录" else "保存失败")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存到下载目录")
                    }
                    TextButton(
                        onClick = {
                            showLogDialog = false
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    LogExporter.clearLogcat()
                                }
                                SnackbarController.show(if (ok) "日志缓存已清空" else "清空失败")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("清空日志缓存（logcat -c）")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) { Text("取消") }
            }
        )
    }

    if (showDevMenu) {
        AlertDialog(
            onDismissRequest = { showDevMenu = false },
            title = { Text("开发调试") },
            text = {
                Column {
                    Button(
                        onClick = {
                            showDevMenu = false
                            scope.launch {
                                val release = runCatching { UpdateChecker.fetchLatestRelease(context) }.getOrNull()
                                updateRelease = release ?: UpdateChecker.Release(
                                    version = "1.2.4",
                                    notes = "这是调试预览弹窗，用于查看更新弹窗 UI。",
                                    downloadUrl = "",
                                    publishedAt = ""
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("显示检查更新弹窗") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevMenu = false }) { Text("关闭") }
            }
        )
    }

    updateRelease?.let { release ->
        UpdateDialog(
            currentVersion = UpdateChecker.currentVersion(context),
            release = release,
            onDownload = {
                updateRelease = null
                if (release.downloadUrl.isNotBlank()) {
                    onDownloadUpdateApk(release.downloadUrl, "吸析At_${release.version}.apk")
                    SnackbarController.show("已加入下载 吸析At_${release.version}.apk")
                } else {
                    SnackbarController.show("未找到 APK 下载链接")
                }
            },
            onLater = { updateRelease = null },
            onIgnore = {
                UpdateChecker.setIgnoredVersion(context, release.version)
                updateRelease = null
            }
        )
    }

    if (showThreadsDialog) {
        AlertDialog(
            onDismissRequest = { showThreadsDialog = false },
            title = { Text("下载线程数") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "按网盘分别设置分片并发数；线程数不是越多越好，适当调整",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    threadPlatforms.forEach { item ->
                        val current = settingsRepo.downloadThreadsFor(item.platform)
                        val isXunlei = item.platform == DownloadPlatform.XUNLEI
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isXunlei) {
                                    selectedThreadPlatform = item
                                    showPlatformThreadDialog = true
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (isXunlei) "固定 8 线程" else "$current 线程",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isXunlei) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            if (!isXunlei) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThreadsDialog = false }) { Text("取消") }
            }
        )
    }

    if (showPlatformThreadDialog) {
        val current = settingsRepo.downloadThreadsFor(selectedThreadPlatform.platform)
        AlertDialog(
            onDismissRequest = { showPlatformThreadDialog = false },
            title = { Text("${selectedThreadPlatform.label}线程数") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    threadOptions.chunked(2).forEach { rowValues ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowValues.forEach { value ->
                                RadioThreadRow(
                                    value = value,
                                    threads = current,
                                    onSelect = { v ->
                                        settingsRepo.setDownloadThreads(selectedThreadPlatform.platform, v)
                                        showPlatformThreadDialog = false
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowValues.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlatformThreadDialog = false }) { Text("取消") }
            }
        )
    }

    if (showExportAuthDialog) {
        ExportAuthDialog(
            onDismiss = { showExportAuthDialog = false },
            onConfirm = { password, onlyLoggedIn ->
                showExportAuthDialog = false
                isExporting = true
                scope.launch {
                    try {
                        val content = runCatching {
                            withContext(Dispatchers.IO) { backupManager.export(password, onlyLoggedIn) }
                        }.getOrNull()
                        if (content == null) {
                            SnackbarController.show("导出失败")
                            return@launch
                        }
                        val encrypted = true
                        val saved = withContext(Dispatchers.IO) {
                            backupManager.saveToDownloads(context, content, encrypted)
                        }
                        SnackbarController.show(
                            if (saved) {
                                if (encrypted) "已加密导出到下载目录" else "已导出到下载目录"
                            } else {
                                "导出失败"
                            }
                        )
                    } finally {
                        isExporting = false
                    }
                }
            }
        )
    }

    if (showImportAuthDialog) {
        ImportAuthDialog(
            onDismiss = {
                showImportAuthDialog = false
                pendingImportContent = null
            },
            onConfirm = { password ->
                showImportAuthDialog = false
                val content = pendingImportContent
                pendingImportContent = null
                if (content != null) {
                    isImporting = true
                    scope.launch {
                        try {
                            val count = try {
                                withContext(Dispatchers.IO) { backupManager.import(content, password) }
                            } catch (e: javax.crypto.AEADBadTagException) {
                                SnackbarController.show("密码错误，解密失败")
                                return@launch
                            } catch (e: Exception) {
                                SnackbarController.show("导入失败：${e.message}")
                                return@launch
                            }
                            SnackbarController.show("已恢复 $count 个平台的认证信息")
                        } finally {
                            isImporting = false
                        }
                    }
                }
            }
        )
    }

    if (isExporting) OperationLoadingDialog("正在导出认证…")
    if (isImporting) OperationLoadingDialog("正在导入认证…")

    if (showConcurrencyDialog) {
        val options = listOf(1, 2, 3, 5, 8)
        AlertDialog(
            onDismissRequest = { showConcurrencyDialog = false },
            title = { Text("最大同时下载任务数") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    options.forEach { v ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = maxConcurrent == v,
                                onClick = {
                                    maxConcurrent = v
                                    settingsRepo.maxConcurrentDownloads = v
                                    showConcurrencyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("同时下载 $v 个任务", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConcurrencyDialog = false }) { Text("取消") }
            }
        )
    }

    if (showSpeedDialog) {
        val presets = listOf(0L, 1L * 1024 * 1024, 2L * 1024 * 1024, 5L * 1024 * 1024, 10L * 1024 * 1024)
        var tempSelected by remember { mutableStateOf<Long?>(null) }
        var customKb by remember {
            mutableStateOf(
                if (speedLimitBps > 0 && speedLimitBps !in presets) (speedLimitBps / 1024).toString() else ""
            )
        }
        val effective = tempSelected ?: speedLimitBps
        val isCustom = when {
            tempSelected == -1L -> true
            tempSelected == null -> speedLimitBps > 0 && speedLimitBps !in presets
            else -> false
        }
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("下载速度限制") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    presets.forEach { v ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !isCustom && effective == v,
                                onClick = { tempSelected = v }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (v == 0L) "不限速" else speedLimitText(v),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustom,
                            onClick = {
                                tempSelected = -1L
                                if (speedLimitBps > 0 && speedLimitBps !in presets && customKb.isBlank()) {
                                    customKb = (speedLimitBps / 1024).toString()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = customKb,
                            onValueChange = {
                                customKb = it.filter(Char::isDigit).take(6)
                                tempSelected = -1L
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("自定义 KB/s") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isCustom) {
                            val kb = customKb.toLongOrNull()?.coerceAtLeast(1L)
                            if (kb != null) {
                                speedLimitBps = kb * 1024
                                settingsRepo.downloadSpeedLimit = kb * 1024
                            }
                        } else if (tempSelected != null) {
                            val v = tempSelected ?: speedLimitBps
                            speedLimitBps = v
                            settingsRepo.downloadSpeedLimit = v
                        }
                        showSpeedDialog = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("取消") }
            }
        )
    }

    if (showRetryDialog) {
        val options = listOf(0, 1, 2, 3, 5, 8, 10)
        AlertDialog(
            onDismissRequest = { showRetryDialog = false },
            title = { Text("失败自动重试") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    options.forEach { v ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = retryCount == v,
                                onClick = {
                                    retryCount = v
                                    settingsRepo.downloadRetryCount = v
                                    showRetryDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (v == 0) "不自动重试" else "失败后自动重试 $v 次",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRetryDialog = false }) { Text("取消") }
            }
        )
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text("保持后台下载") },
            text = {
                Text(
                    text = "为确保障屏后下载不中断，建议将云析加入「忽略电池优化」白名单。是否前往系统设置？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryDialog = false
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                ) { Text("前往设置") }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) { Text("暂不") }
            }
        )
    }
}

@Composable
private fun ExportAuthDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String, onlyLoggedIn: Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var onlyLoggedIn by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出网盘认证") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "设置至少 8 位密码对认证文件进行 AES 加密。密码请务必牢记，丢失无法找回。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("加密密码（至少 8 位）") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                Text(
                    text = "导出范围",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = onlyLoggedIn,
                        onClick = { onlyLoggedIn = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("仅导出当前已登录的网盘", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !onlyLoggedIn,
                        onClick = { onlyLoggedIn = false }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出全部绑定的网盘", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password, onlyLoggedIn) },
                enabled = password.length >= 8
            ) { Text("导出") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ImportAuthDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入网盘认证") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "该备份文件已加密，请输入导出时设置的密码进行解密。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("解密密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) { Text("解密并导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun OperationLoadingDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(message) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val shape = MaterialTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun RadioThreadRow(
    value: Int,
    threads: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = threads == value,
            onClick = { onSelect(value) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$value 线程",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun speedLimitText(bps: Long): String {
    if (bps <= 0) return "不限速"
    return if (bps >= 1024 * 1024) {
        val mb = bps / (1024.0 * 1024.0)
        if (mb >= 10) String.format("%.0f MB/s", mb) else String.format("%.1f MB/s", mb)
    } else {
        "${bps / 1024} KB/s"
    }
}
