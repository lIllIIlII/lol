package com.yunx.app.ui.resolve

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.yunx.app.data.db.BookmarkEntity
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.ui.components.ScrollToTopButton
import com.yunx.app.ui.items.MultiSelectAction
import com.yunx.app.ui.items.MultiSelectBar
import com.yunx.app.ui.screens.AddToBookmarkDialog
import com.yunx.app.ui.screens.BaiduSaveSheet
import com.yunx.app.ui.screens.C139SaveSheet
import com.yunx.app.ui.screens.Pan123SaveSheet
import com.yunx.app.ui.screens.SaveToCloudSheet
import com.yunx.app.ui.screens.UCSaveSheet
import com.yunx.app.ui.screens.XunleiSaveSheet
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel
import com.yunx.app.ui.viewmodel.C139CloudViewModel
import com.yunx.app.ui.viewmodel.Pan123CloudViewModel
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel
import com.yunx.app.ui.viewmodel.ResolveViewModel
import com.yunx.app.ui.viewmodel.UCCoudViewModel
import com.yunx.app.ui.viewmodel.XunleiCloudViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val BAIDU_LIMIT_BYTES = 300L * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDetailScreen(
    session: ShareSession,
    files: List<ShareFile>,
    viewModel: ResolveViewModel,
    quarkCloudViewModel: QuarkCloudViewModel,
    xunleiCloudViewModel: XunleiCloudViewModel,
    baiduCloudViewModel: BaiduCloudViewModel,
    c139CloudViewModel: C139CloudViewModel,
    ucCloudViewModel: UCCoudViewModel,
    pan123CloudViewModel: Pan123CloudViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    listState: LazyListState,
    scrollPositions: MutableMap<String, Int>,
    onExit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pathNames = viewModel.pathNames
    val context = LocalContext.current
    val baiduSettings = remember { SettingsRepository(context) }
    var baiduLimitDismissed by remember { mutableStateOf(baiduSettings.baiduLimitHintDismissed) }
    var showBaiduLimitDialog by remember { mutableStateOf(false) }
    var pendingBaiduAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showAddBookmark by remember { mutableStateOf(false) }

    fun checkBaiduLimit(file: ShareFile, proceed: () -> Unit) {
        if (viewModel.isBaidu && !baiduLimitDismissed && file.fsize > BAIDU_LIMIT_BYTES) {
            pendingBaiduAction = proceed
            showBaiduLimitDialog = true
        } else {
            proceed()
        }
    }
    BackHandler {
        if (viewModel.multiSelectMode) viewModel.exitMultiSelect() else onBack()
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val displayFiles = remember(files, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) files else files.filter { it.fname.contains(q, ignoreCase = true) }
    }
    val currentDirKey = remember(pathNames) { pathNames.joinToString("/") }
    LaunchedEffect(currentDirKey) {
        listState.scrollToItem(scrollPositions[currentDirKey] ?: 0)
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 16.dp,
                        bottom = if (viewModel.multiSelectMode) 96.dp else 16.dp
                    ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.multiSelectMode) {
                            IconButton(onClick = { viewModel.exitMultiSelect() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "取消选择")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "已选 ${viewModel.selected.size} 项",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (viewModel.selected.size == displayFiles.size) "已全选" else "点击选择更多文件",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.toggleSelectAll(displayFiles) }) {
                                Text(if (viewModel.selected.size == displayFiles.size) "取消全选" else "全选")
                            }
                        } else {
                            IconButton(onClick = onExit) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title.ifBlank { "分享内容" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (searchQuery.isBlank()) "共 ${files.size} 项"
                                    else "匹配 ${displayFiles.size} / ${files.size} 项",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { showAddBookmark = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkAdd,
                                    contentDescription = "添加至收藏",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = if (showSearch) "关闭搜索" else "搜索文件",
                                    tint = if (showSearch) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                    if (!viewModel.multiSelectMode) {
                        CrumbBar(
                            rootTitle = session.title.ifBlank { "分享内容" },
                            pathNames = pathNames,
                            onNavigate = { level ->
                                scrollPositions[currentDirKey] = listState.firstVisibleItemIndex
                                viewModel.navigateToLevel(level)
                            }
                        )
                    }
                    AnimatedVisibility(
                        visible = showSearch && !viewModel.multiSelectMode,
                        enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(140)) + fadeOut(tween(120))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("搜索当前目录文件") },
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Filled.Close, contentDescription = "清空搜索")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = MaterialTheme.shapes.large
                            )
                        }
                    }
                }
            }

            if (pathNames.isNotEmpty()) {
                item {
                    BackToParentItem(onClick = {
                        scrollPositions[currentDirKey] = listState.firstVisibleItemIndex
                        onBack()
                    })
                }
            }

            if (displayFiles.isEmpty()) {
                item {
                    Text(
                        text = if (files.isEmpty()) "此目录为空" else "未找到匹配「${searchQuery.trim()}」的文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(displayFiles, key = { it.fid }) { file ->
                ShareFileRow(
                    file = file,
                    onClick = {
                        if (viewModel.multiSelectMode) {
                            viewModel.toggleSelect(file)
                        } else if (file.isdir) {
                            scrollPositions[currentDirKey] = listState.firstVisibleItemIndex
                            viewModel.openFolder(file)
                        } else {
                            checkBaiduLimit(file) { viewModel.fetchDownloadLink(file) }
                        }
                    },
                    onSave = if (!viewModel.multiSelectMode && viewModel.canSave) {
                        { viewModel.requestSave(file) }
                    } else {
                        null
                    },
                    onLongClick = if (!viewModel.multiSelectMode) {
                        { viewModel.enterMultiSelect(file) }
                    } else {
                        null
                    },
                    selected = viewModel.selected.contains(file),
                    showCheckbox = viewModel.multiSelectMode
                )
            }
        }

        ScrollToTopButton(
            listState = listState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = if (viewModel.multiSelectMode) 104.dp else 16.dp
                )
        )

        if (viewModel.multiSelectMode) {
            MultiSelectBar(
                count = viewModel.selected.size,
                actions = buildList {
                    if (viewModel.canSave) {
                        add(
                            MultiSelectAction("转存", Icons.Outlined.SaveAlt, MaterialTheme.colorScheme.primary) {
                                viewModel.batchSaveToCloud()
                            }
                        )
                    }
                    add(
                        MultiSelectAction("下载", Icons.Outlined.Download, MaterialTheme.colorScheme.primary) {
                            val hasBig = viewModel.selected.any { it.fsize > BAIDU_LIMIT_BYTES }
                            if (viewModel.isBaidu && !baiduLimitDismissed && hasBig) {
                                pendingBaiduAction = { viewModel.batchDownload() }
                                showBaiduLimitDialog = true
                            } else {
                                viewModel.batchDownload()
                            }
                        }
                    )
                }
            )
        }
    }

    if (showBaiduLimitDialog) {
        var neverShow by remember { mutableStateOf(baiduLimitDismissed) }
        AlertDialog(
            onDismissRequest = { showBaiduLimitDialog = false },
            title = { Text("下载大文件提示") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "百度网盘非会员超过300MB会被限速，下载速度可能较慢。是否继续下载？",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = neverShow, onCheckedChange = { neverShow = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("不再显示此提示", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBaiduLimitDialog = false
                        baiduSettings.baiduLimitHintDismissed = neverShow
                        baiduLimitDismissed = neverShow
                        pendingBaiduAction?.invoke()
                        pendingBaiduAction = null
                    }
                ) { Text("继续下载") }
            },
            dismissButton = {
                TextButton(onClick = { showBaiduLimitDialog = false }) { Text("取消") }
            }
        )
    }

    if (viewModel.isBatchWorking) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelBatch() }) {
                    Text("中断", color = MaterialTheme.colorScheme.error)
                }
            },
            title = { Text("批量处理中") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = viewModel.batchProgress?.let { "正在获取下载链接 $it" }
                            ?: "正在批量处理，请稍候…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }

    if (showAddBookmark) {
        AddToBookmarkDialog(
            title = session.title.ifBlank { "分享内容" },
            initialCategory = BookmarkEntity.DEFAULT_CATEGORY,
            categories = BookmarkEntity.PRESET_CATEGORIES,
            onConfirm = { title, category ->
                showAddBookmark = false
                viewModel.addCurrentToBookmark(title, category)
            },
            onDismiss = { showAddBookmark = false }
        )
    }

    if (viewModel.saveTarget != null) {
        when {
            viewModel.isSaveXunlei -> XunleiSaveSheet(
                resolveViewModel = viewModel,
                cloudViewModel = xunleiCloudViewModel,
                onDismiss = { viewModel.dismissSave() }
            )
            viewModel.isSaveBaidu -> BaiduSaveSheet(
                resolveViewModel = viewModel,
                cloudViewModel = baiduCloudViewModel,
                onDismiss = { viewModel.dismissSave() }
            )
            viewModel.isSaveC139 -> C139SaveSheet(
                resolveViewModel = viewModel,
                cloudViewModel = c139CloudViewModel,
                onDismiss = { viewModel.dismissSave() }
            )
            viewModel.isSaveUC -> UCSaveSheet(
                resolveViewModel = viewModel,
                cloudViewModel = ucCloudViewModel,
                onDismiss = { viewModel.dismissSave() }
            )
            viewModel.isSavePan123 -> Pan123SaveSheet(
                resolveViewModel = viewModel,
                cloudViewModel = pan123CloudViewModel,
                onDismiss = { viewModel.dismissSave() }
            )
            else -> SaveToCloudSheet(
                resolveViewModel = viewModel,
                cloudViewModel = quarkCloudViewModel,
                onDismiss = { viewModel.dismissSave() }
            )
        }
    }

    viewModel.saveDownloadAsk?.let { prompt ->
        AlertDialog(
            onDismissRequest = { if (!viewModel.isSavedFileLoading) viewModel.dismissSaveDownload() },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmSaveDownload() },
                    enabled = !viewModel.isSavedFileLoading
                ) { Text("立即下载") }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissSaveDownload() },
                    enabled = !viewModel.isSavedFileLoading
                ) { Text("暂不") }
            },
            icon = {
                if (viewModel.isSavedFileLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            title = { Text("已转存到${prompt.platformLabel}") },
            text = {
                Text(
                    if (viewModel.isSavedFileLoading) "正在获取下载链接…"
                    else "「${prompt.fileName}」已保存到你的${prompt.platformLabel}，是否立即下载？",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }
}

@Composable
internal fun BackToParentItem(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "返回上一级",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun CrumbBar(
    rootTitle: String,
    pathNames: List<String>,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val crumbs = buildList {
        add(rootTitle.ifBlank { "根目录" })
        pathNames.forEach { add(it) }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(crumbs.size, crumbs.lastOrNull()) {
        scroll.scrollTo(scroll.maxValue)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(start = 8.dp, top = 4.dp)
    ) {
        crumbs.forEachIndexed { i, name ->
            val isLast = i == crumbs.size - 1
            if (!isLast) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable { onNavigate(i) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShareFileRow(
    file: ShareFile,
    onClick: () -> Unit,
    onSave: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    showCheckbox: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCheckbox) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (file.isdir) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (file.isdir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (file.isdir) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fname,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(if (file.isdir) "文件夹" else formatSize(file.fsize))
                        val time = formatModifyTime(file.modifyTime)
                        if (time.isNotBlank()) {
                            append("  ·  ")
                            append(time)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onSave != null) {
                IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.SaveAlt,
                        contentDescription = "转存",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (onMore != null) {
                IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

internal fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return String.format("%.1f %s", value, units[i])
}

private fun parseModifyTimeMillis(raw: String): Long? {
    val s = raw.trim()
    if (s.isEmpty()) return null

    if (s.all(Char::isDigit)) {
        return when (s.length) {
            13 -> s.toLongOrNull()
            10 -> s.toLongOrNull()?.times(1000L)
            14 -> runCatching {
                SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).apply {
                    isLenient = false
                }.parse(s)?.time
            }.getOrNull()
            else -> s.toLongOrNull()?.let { if (it > 100_000_000_000L) it else it * 1000L }
        }
    }

    var work = s.replace('T', ' ')
    var tz: TimeZone? = null
    if (work.endsWith("Z", ignoreCase = true)) {
        tz = TimeZone.getTimeZone("UTC")
        work = work.dropLast(1)
    } else {
        val m = Regex("([+-]\\d{2}:?\\d{2})$").find(work)
        if (m != null) {
            tz = TimeZone.getTimeZone("GMT${m.groupValues[1]}")
            work = work.removeRange(m.range)
        }
    }
    val body = work.trim().substringBefore('.')
    val zone: TimeZone? = tz

    val patterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd",
        "yyyy/MM/dd"
    )
    for (p in patterns) {
        val fmt = SimpleDateFormat(p, Locale.getDefault())
        fmt.isLenient = false
        if (zone != null) fmt.timeZone = zone
        runCatching { fmt.parse(body) }.getOrNull()?.let { return it.time }
    }
    return null
}

internal fun formatModifyTime(raw: String): String {
    val millis = parseModifyTimeMillis(raw) ?: return ""
    if (millis <= 0) return ""
    val cal = Calendar.getInstance()
    val currentYear = cal.get(Calendar.YEAR)
    cal.timeInMillis = millis
    val pattern = if (cal.get(Calendar.YEAR) == currentYear) "MM-dd HH:mm" else "yyyy-MM-dd"
    return runCatching {
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }.getOrDefault("")
}
