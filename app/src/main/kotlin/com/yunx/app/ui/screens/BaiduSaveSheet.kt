package com.yunx.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.CrumbBar
import com.yunx.app.ui.resolve.ShareFileRow
import com.yunx.app.ui.viewmodel.BaiduCloudUiState
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel
import com.yunx.app.ui.viewmodel.ResolveViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaiduSaveSheet(
    resolveViewModel: ResolveViewModel,
    cloudViewModel: BaiduCloudViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cloudState by cloudViewModel.uiState.collectAsState()
    val saving = resolveViewModel.isSaving
    val message = resolveViewModel.saveMessage

    LaunchedEffect(Unit) {
        cloudViewModel.loadRoot()
    }
    LaunchedEffect(message) {
        if (message != null) {
            SnackbarController.show(message)
            resolveViewModel.consumeSaveMessage()
        }
    }

    val snackbarHostState = rememberGlobalSnackbarHostState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.SaveAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "转存到百度网盘",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = resolveViewModel.saveTarget?.fname ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            CrumbBar(
                rootTitle = "根目录",
                pathNames = (cloudState as? BaiduCloudUiState.Loaded)?.pathNames ?: emptyList(),
                onNavigate = { cloudViewModel.navigateToLevel(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if ((cloudState as? BaiduCloudUiState.Loaded)?.pathNames?.isNotEmpty() == true) {
                BackToParentItem(onClick = { cloudViewModel.back() })
                Spacer(modifier = Modifier.height(4.dp))
            }

            AnimatedContent(
                targetState = cloudState,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "baiduSaveState"
            ) { s ->
                when (s) {
                    is BaiduCloudUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                is BaiduCloudUiState.Error -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { cloudViewModel.loadRoot() }) {
                            Text("重试")
                        }
                    }
                }

                is BaiduCloudUiState.Loaded -> {
                    val dirs = s.files.filter { it.isdir }
                    if (dirs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "当前目录没有子文件夹，可直接转存到此目录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(dirs, key = { it.fid }) { dir ->
                                ShareFileRow(
                                    file = dir,
                                    onClick = { cloudViewModel.openFolder(dir) }
                                )
                            }
                        }
                    }
            }
            }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val currentDirName =
                (cloudState as? BaiduCloudUiState.Loaded)?.pathNames?.lastOrNull() ?: "根目录"
            Button(
                onClick = {
                    val dirPath = (cloudState as? BaiduCloudUiState.Loaded)?.dirPath ?: "/"
                    resolveViewModel.saveToCloud(dirPath)
                },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("转存到此目录（$currentDirName）")
                }
            }

            SnackbarHost(hostState = snackbarHostState)
        }
    }
}
