package com.yunx.app.ui.screens

import com.yunx.app.ui.SnackbarController
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.yunx.app.ui.items.MultiSelectAction
import com.yunx.app.ui.items.MultiSelectBar
import com.yunx.app.ui.components.ScrollToTopButton
import com.yunx.app.ui.resolve.DownloadLinkDialog
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.CrumbBar
import com.yunx.app.ui.resolve.ShareFileRow
import com.yunx.app.ui.viewmodel.QuarkCloudUiState
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudDriveScreen(
    viewModel: QuarkCloudViewModel,
    scrollBehavior: TopAppBarScrollBehavior,
    onExit: () -> Unit,
    onDownloadStarted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    BackHandler {
        if (viewModel.multiSelectMode) {
            viewModel.exitMultiSelect()
        } else {
            val s = state
            if (s is QuarkCloudUiState.Loaded && s.pathNames.isNotEmpty()) viewModel.back() else onExit()
        }
    }
    val listState = rememberLazyListState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val scrollPositions = remember { mutableStateMapOf<String, Int>() }
    val loadedState = state as? QuarkCloudUiState.Loaded
    val displayFiles = remember(loadedState?.files, searchQuery) {
        val files = loadedState?.files ?: emptyList()
        val q = searchQuery.trim()
        if (q.isEmpty()) files else files.filter { it.fname.contains(q, ignoreCase = true) }
    }
    val currentDirKey = remember(loadedState?.pathNames) {
        loadedState?.pathNames?.joinToString("/") ?: ""
    }
    var showBatchActions by remember { mutableStateOf(false) }
    var batchInitial by remember { mutableStateOf(com.yunx.app.ui.screens.BatchStep.MENU) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.cloudMessage) {
        viewModel.cloudMessage?.let {
            SnackbarController.show(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(viewModel.downloadTriggered) {
        if (viewModel.downloadTriggered > 0) {
            viewModel.consumeDownloadTriggered()
            onDownloadStarted()
        }
    }

    viewModel.downloadLink?.let { link ->
        DownloadLinkDialog(
            link = link,
            onDownload = { viewModel.startDownload() },
            onDismiss = { viewModel.dismissDownloadDialog() }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(140))
            },
            label = "cloudState"
        ) { s ->
            when (s) {
            is QuarkCloudUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is QuarkCloudUiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onExit) { Text("返回") }
                        TextButton(onClick = { viewModel.loadRoot() }) { Text("重试") }
                    }
                }
            }

            is QuarkCloudUiState.Loaded -> Box(modifier = Modifier.fillMaxSize()) {
                val loadedKey = remember(s.pathNames) { s.pathNames.joinToString("/") }
                LaunchedEffect(loadedKey) {
                    listState.scrollToItem(scrollPositions[loadedKey] ?: 0)
                }
                PullToRefreshBox(
                    isRefreshing = viewModel.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
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
                                    text = "夸克网盘",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (searchQuery.isBlank()) "共 ${s.files.size} 项"
                                    else "匹配 ${displayFiles.size} / ${s.files.size} 项",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            rootTitle = "夸克网盘",
                            pathNames = s.pathNames,
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

            if (s.pathNames.isNotEmpty()) {
                item {
                    BackToParentItem(onClick = {
                        scrollPositions[currentDirKey] = listState.firstVisibleItemIndex
                        viewModel.back()
                    })
                }
            }

            if (displayFiles.isEmpty()) {
                item {
                    Text(
                        text = if (s.files.isEmpty()) "此目录为空" else "未找到匹配「${searchQuery.trim()}」的文件",
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
                            viewModel.openActions(file)
                        }
                    },
                    onMore = if (!viewModel.multiSelectMode && file.isdir) {
                        { viewModel.openActions(file) }
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

                AnimatedVisibility(
                    visible = viewModel.multiSelectMode,
                    enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                    exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    MultiSelectBar(
                        count = viewModel.selected.size,
                        actions = listOf(
                            MultiSelectAction("下载", Icons.Outlined.Download, MaterialTheme.colorScheme.primary) {
                                viewModel.downloadSelected()
                            },
                            MultiSelectAction("分享", Icons.Outlined.Share, MaterialTheme.colorScheme.primary) {
                                batchInitial = com.yunx.app.ui.screens.BatchStep.SHARE
                                showBatchActions = true
                            },
                            MultiSelectAction("移动", Icons.Outlined.DriveFileMove, MaterialTheme.colorScheme.primary) {
                                batchInitial = com.yunx.app.ui.screens.BatchStep.MOVE
                                showBatchActions = true
                            },
                            MultiSelectAction("删除", Icons.Outlined.Delete, MaterialTheme.colorScheme.error) {
                                showDeleteConfirm = true
                            }
                        )
                    )
                }
            }
        }
    }
    }
    }

    viewModel.actionFile?.let { file ->
        FileActionSheet(
            file = file,
            viewModel = viewModel,
            onDismiss = { viewModel.dismissActions() }
        )
    }

    if (showBatchActions) {
        BatchActionSheet(
            viewModel = viewModel,
            initialStep = batchInitial,
            onDismiss = { showBatchActions = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除文件") },
            text = { Text("确定要删除选中的 ${viewModel.selected.size} 项吗？删除后将移入回收站。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSelected()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    if (viewModel.isOperating) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDownload() }) {
                    Text("中断", color = MaterialTheme.colorScheme.error)
                }
            },
            title = { Text("处理中") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = viewModel.folderProgress ?: "正在处理，请稍候…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }
}
