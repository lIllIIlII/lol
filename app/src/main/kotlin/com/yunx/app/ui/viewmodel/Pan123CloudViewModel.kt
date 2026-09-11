package com.yunx.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface Pan123CloudUiState {
    data object Loading : Pan123CloudUiState
    data class Loaded(
        val files: List<ShareFile>,
        val pathNames: List<String>,
        val dirId: String
    ) : Pan123CloudUiState
    data class Error(val message: String) : Pan123CloudUiState
}

class Pan123CloudViewModel(
    private val api: Pan123Api,
    private val tokenProvider: suspend () -> String?,
    private val downloadManager: DownloadManager,
    private val loginState: Flow<Boolean>
) : ViewModel() {

    private val _uiState = MutableStateFlow<Pan123CloudUiState>(Pan123CloudUiState.Loading)
    val uiState: StateFlow<Pan123CloudUiState> = _uiState.asStateFlow()

    var actionFile by mutableStateOf<ShareFile?>(null)
        private set
    var cloudMessage by mutableStateOf<String?>(null)
        private set
    var isOperating by mutableStateOf(false)
        private set
    var folderProgress by mutableStateOf<String?>(null)
        private set
    private var downloadCancelRequested = false
    var refreshing by mutableStateOf(false)
        private set
    var downloadTriggered by mutableStateOf(0)
        private set
    var shareResult by mutableStateOf<ShareInfo?>(null)
        private set
    var multiSelectMode by mutableStateOf(false)
        private set
    private val _selected = mutableStateListOf<ShareFile>()
    val selected: List<ShareFile> get() = _selected

    private val dirStack = ArrayDeque<String>()
    private val nameStack = ArrayDeque<String>()

    private val _moveUiState = MutableStateFlow<Pan123CloudUiState>(Pan123CloudUiState.Loading)
    val moveUiState: StateFlow<Pan123CloudUiState> = _moveUiState.asStateFlow()
    private val moveDirStack = ArrayDeque<String>()
    private val moveNameStack = ArrayDeque<String>()

    init {
        loadRoot()
        viewModelScope.launch {
            loginState
                .drop(1)
                .distinctUntilChanged()
                .collect { loggedIn -> if (loggedIn) loadRoot() }
        }
    }

    private suspend fun token(): String =
        tokenProvider() ?: throw IllegalStateException("请先登录123云盘")

    fun loadRoot() {
        dirStack.clear()
        nameStack.clear()
        load("0", emptyList())
    }

    fun openFolder(file: ShareFile) {
        dirStack.addLast(file.fid)
        nameStack.addLast(file.fname)
        load(file.fid, nameStack.toList())
    }

    fun back() {
        if (nameStack.isEmpty()) {
            loadRoot()
            return
        }
        dirStack.removeLast()
        nameStack.removeLast()
        load(dirStack.lastOrNull() ?: "0", nameStack.toList())
    }

    fun navigateToLevel(level: Int) {
        while (nameStack.size > level) {
            dirStack.removeLast()
            nameStack.removeLast()
        }
        load(dirStack.lastOrNull() ?: "0", nameStack.toList())
    }

    fun enterMultiSelect(file: ShareFile) {
        multiSelectMode = true
        _selected.clear()
        _selected.add(file)
    }

    fun toggleSelect(file: ShareFile) {
        if (_selected.contains(file)) _selected.remove(file) else _selected.add(file)
    }

    fun toggleSelectAll(files: List<ShareFile>) {
        if (_selected.size == files.size) _selected.clear()
        else {
            _selected.clear()
            _selected.addAll(files)
        }
    }

    fun exitMultiSelect() {
        multiSelectMode = false
        _selected.clear()
    }

    fun openActions(file: ShareFile) {
        actionFile = file
    }

    fun dismissActions() {
        actionFile = null
    }

    fun consumeMessage() {
        cloudMessage = null
    }

    fun dismissShareResult() {
        shareResult = null
    }

    fun consumeDownloadTriggered() {
        downloadTriggered = 0
    }

    fun cancelDownload() {
        downloadCancelRequested = true
    }

    fun openMoveRoot() {
        moveDirStack.clear()
        moveNameStack.clear()
        moveLoad("0", emptyList())
    }

    fun openMoveFolder(file: ShareFile) {
        moveDirStack.addLast(file.fid)
        moveNameStack.addLast(file.fname)
        moveLoad(file.fid, moveNameStack.toList())
    }

    fun moveBack() {
        if (moveNameStack.isEmpty()) return
        moveDirStack.removeLast()
        moveNameStack.removeLast()
        moveLoad(moveDirStack.lastOrNull() ?: "0", moveNameStack.toList())
    }

    fun moveNavigateToLevel(level: Int) {
        while (moveNameStack.size > level) {
            moveDirStack.removeLast()
            moveNameStack.removeLast()
        }
        moveLoad(moveDirStack.lastOrNull() ?: "0", moveNameStack.toList())
    }

    private fun moveLoad(dirId: String, pathNames: List<String>) {
        _moveUiState.value = Pan123CloudUiState.Loading
        viewModelScope.launch {
            try {
                val files = api.listCloudFiles(dirId, token()).filter { it.isdir }
                _moveUiState.value = Pan123CloudUiState.Loaded(files, pathNames, dirId)
            } catch (e: Exception) {
                _moveUiState.value = Pan123CloudUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    private fun downloadHeaders(): Map<String, String> = mapOf(
        "User-Agent" to Pan123Constants.WEB_UA,
        "Referer" to Pan123Constants.DOWNLOAD_REFERER
    )

    private suspend fun collectFolderFiles(
        dirId: String,
        prefix: String,
        token: String,
        result: MutableList<Pair<ShareFile, String>>,
        depth: Int
    ) {
        if (depth > 12) return
        val list = runCatching { api.listCloudFiles(dirId, token) }.getOrDefault(emptyList())
        list.filter { !it.isdir }.forEach { result.add(it to "$prefix/${it.fname}") }
        list.filter { it.isdir }.forEach {
            collectFolderFiles(it.fid, "$prefix/${it.fname}", token, result, depth + 1)
        }
    }

    fun downloadFolder() {
        val folder = actionFile ?: return
        if (!folder.isdir) return
        viewModelScope.launch {
            isOperating = true
            folderProgress = "正在收集文件…"
            downloadCancelRequested = false
            try {
                val tk = token()
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                collectFolderFiles(folder.fid, folder.fname, tk, tasks, 0)
                if (tasks.isEmpty()) {
                    cloudMessage = "文件夹为空"
                    actionFile = null
                    return@launch
                }
                var okCount = 0
                tasks.forEachIndexed { index, (file, relPath) ->
                    if (downloadCancelRequested) return@forEachIndexed
                    folderProgress = "正在加入下载 ${index + 1}/${tasks.size}"
                    runCatching {
                        val link = api.getDownloadLink(file, tk) ?: return@runCatching
                        downloadManager.enqueue(
                            url = link.downloadUrl,
                            fileName = relPath,
                            size = link.size,
                            platform = DownloadPlatform.PAN123,
                            headers = downloadHeaders()
                        )
                        okCount++
                    }
                }
                if (downloadCancelRequested) {
                    cloudMessage = "已中断下载"
                    actionFile = null
                    return@launch
                }
                cloudMessage = "已加入 $okCount 个下载任务"
                actionFile = null
            } catch (e: Exception) {
                cloudMessage = e.message ?: "下载文件夹失败"
            } finally {
                isOperating = false
                folderProgress = null
                downloadCancelRequested = false
            }
        }
    }

    var downloadLink by mutableStateOf<DownloadLink?>(null)
        private set

    private var pendingDownload: PendingDownload? = null

    fun downloadFile() {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                val link = api.getDownloadLink(file, token())
                    ?: throw IllegalStateException("获取下载链接失败")
                pendingDownload = PendingDownload(
                    url = link.downloadUrl,
                    fileName = file.fname.ifBlank { link.filename },
                    size = link.size,
                    headers = downloadHeaders()
                )
                downloadLink = link
            } catch (e: Exception) {
                cloudMessage = e.message ?: "下载失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun startDownload() {
        val pd = pendingDownload ?: return
        downloadLink = null
        pendingDownload = null
        viewModelScope.launch {
            isOperating = true
            try {
                downloadManager.enqueue(
                    url = pd.url,
                    fileName = pd.fileName,
                    size = pd.size,
                    platform = DownloadPlatform.PAN123,
                    headers = pd.headers
                )
                cloudMessage = "已加入下载：${pd.fileName}"
                actionFile = null
                downloadTriggered++
            } catch (e: Exception) {
                cloudMessage = e.message ?: "下载失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun dismissDownloadDialog() {
        downloadLink = null
        pendingDownload = null
    }

    fun renameFile(newName: String) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                api.renameFile(file.fid, newName, token())
                cloudMessage = "已重命名"
                actionFile = null
                reloadCurrent()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "重命名失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun moveFile(toDirId: String) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                api.moveFiles(listOf(file.fid), toDirId, token())
                cloudMessage = "已移动到目标目录"
                actionFile = null
                reloadCurrent()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "移动失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun shareFile(expirationDays: Int?, sharePwd: String?) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                val info = api.createShare(
                    fileIds = listOf(file.fid),
                    shareName = file.fname,
                    expiration = expiration(expirationDays),
                    sharePwd = sharePwd,
                    token = token()
                )
                shareResult = info.copy(expiredType = expireType(expirationDays))
            } catch (e: Exception) {
                cloudMessage = e.message ?: "分享失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun deleteFile() {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                api.deleteFiles(listOf(file), token())
                cloudMessage = "已删除「${file.fname}」"
                actionFile = null
                reloadCurrent()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "删除失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun downloadSelected() {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            folderProgress = "正在收集文件…"
            downloadCancelRequested = false
            try {
                val tk = token()
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                for (file in files) {
                    if (file.isdir) {
                        collectFolderFiles(file.fid, file.fname, tk, tasks, 0)
                    } else {
                        tasks.add(file to file.fname)
                    }
                }
                if (tasks.isEmpty()) {
                    cloudMessage = "所选文件夹为空"
                    exitMultiSelect()
                    return@launch
                }
                var okCount = 0
                tasks.forEachIndexed { index, (file, relPath) ->
                    if (downloadCancelRequested) return@forEachIndexed
                    folderProgress = "正在加入下载 ${index + 1}/${tasks.size}"
                    runCatching {
                        val link = api.getDownloadLink(file, tk) ?: return@runCatching
                        downloadManager.enqueue(
                            url = link.downloadUrl,
                            fileName = if (relPath.contains('/')) relPath else file.fname.ifBlank { link.filename },
                            size = link.size,
                            platform = DownloadPlatform.PAN123,
                            headers = downloadHeaders()
                        )
                        okCount++
                    }
                }
                if (downloadCancelRequested) {
                    cloudMessage = "已中断批量下载"
                    exitMultiSelect()
                    return@launch
                }
                cloudMessage = "已加入 $okCount 个下载任务"
                exitMultiSelect()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "批量下载失败"
            } finally {
                isOperating = false
                folderProgress = null
                downloadCancelRequested = false
            }
        }
    }

    fun shareSelected(expirationDays: Int?, sharePwd: String?) {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                val title = if (files.size == 1) files[0].fname else "分享 ${files.size} 个文件"
                val info = api.createShare(
                    fileIds = files.map { it.fid },
                    shareName = title,
                    expiration = expiration(expirationDays),
                    sharePwd = sharePwd,
                    token = token()
                )
                shareResult = info.copy(expiredType = expireType(expirationDays))
                exitMultiSelect()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "分享失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun moveSelected(toDirId: String) {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                api.moveFiles(files.map { it.fid }, toDirId, token())
                cloudMessage = "已移动 ${files.size} 项"
                exitMultiSelect()
                reloadCurrent()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "移动失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun deleteSelected() {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                api.deleteFiles(files, token())
                cloudMessage = "已删除 ${files.size} 项"
                exitMultiSelect()
                reloadCurrent()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "删除失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun refresh() {
        val current = uiState.value
        if (current !is Pan123CloudUiState.Loaded) {
            loadRoot()
            return
        }
        refreshing = true
        viewModelScope.launch {
            try {
                val files = api.listCloudFiles(current.dirId, token())
                _uiState.value = Pan123CloudUiState.Loaded(files, current.pathNames, current.dirId)
            } catch (e: Exception) {
                cloudMessage = e.message ?: "刷新失败"
            } finally {
                refreshing = false
            }
        }
    }

    private fun reloadCurrent() {
        val current = uiState.value
        if (current is Pan123CloudUiState.Loaded) {
            load(current.dirId, current.pathNames)
        } else {
            loadRoot()
        }
    }

    private fun load(dirId: String, pathNames: List<String>) {
        _uiState.value = Pan123CloudUiState.Loading
        viewModelScope.launch {
            try {
                val files = api.listCloudFiles(dirId, token())
                _uiState.value = Pan123CloudUiState.Loaded(files, pathNames, dirId)
            } catch (e: Exception) {
                _uiState.value = Pan123CloudUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    private fun expiration(days: Int?): String {
        if (days == null) return Pan123Constants.EXPIRATION_FOREVER
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val offsetMin = TimeZone.getDefault().getOffset(cal.timeInMillis) / 60000
        val sign = if (offsetMin >= 0) "+" else "-"
        val abs = kotlin.math.abs(offsetMin)
        return sdf.format(Date(cal.timeInMillis)) +
            String.format("%s%02d:%02d", sign, abs / 60, abs % 60)
    }

    private fun expireType(days: Int?): Int = when (days) {
        null -> 1
        1 -> 2
        7 -> 3
        else -> 4
    }

    class Factory(
        private val api: Pan123Api,
        private val tokenProvider: suspend () -> String?,
        private val downloadManager: DownloadManager,
        private val loginState: Flow<Boolean>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            Pan123CloudViewModel(api, tokenProvider, downloadManager, loginState) as T
    }
}
