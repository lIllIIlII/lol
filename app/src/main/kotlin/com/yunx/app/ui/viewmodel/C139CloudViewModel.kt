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
import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.C139Constants
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed interface C139CloudUiState {
    data object Loading : C139CloudUiState
    data class Loaded(
        val files: List<ShareFile>,
        val pathNames: List<String>,
        val dirId: String
    ) : C139CloudUiState
    data class Error(val message: String) : C139CloudUiState
}

class C139CloudViewModel(
    private val api: C139Api,
    private val cookieProvider: suspend () -> String?,
    private val downloadManager: DownloadManager,
    private val loginState: Flow<Boolean>
) : ViewModel() {

    private val _uiState = MutableStateFlow<C139CloudUiState>(C139CloudUiState.Loading)
    val uiState: StateFlow<C139CloudUiState> = _uiState.asStateFlow()

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

    private val _moveUiState = MutableStateFlow<C139CloudUiState>(C139CloudUiState.Loading)
    val moveUiState: StateFlow<C139CloudUiState> = _moveUiState.asStateFlow()
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

    private suspend fun cookie(): String =
        cookieProvider() ?: throw IllegalStateException("请先登录139网盘")

    fun loadRoot() {
        dirStack.clear()
        nameStack.clear()
        load("/", emptyList())
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
        load(dirStack.lastOrNull() ?: "/", nameStack.toList())
    }

    fun navigateToLevel(level: Int) {
        while (nameStack.size > level) {
            dirStack.removeLast()
            nameStack.removeLast()
        }
        load(dirStack.lastOrNull() ?: "/", nameStack.toList())
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
        moveLoad("/", emptyList())
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
        moveLoad(moveDirStack.lastOrNull() ?: "/", moveNameStack.toList())
    }

    fun moveNavigateToLevel(level: Int) {
        while (moveNameStack.size > level) {
            moveDirStack.removeLast()
            moveNameStack.removeLast()
        }
        moveLoad(moveDirStack.lastOrNull() ?: "/", moveNameStack.toList())
    }

    private fun moveLoad(dirId: String, pathNames: List<String>) {
        _moveUiState.value = C139CloudUiState.Loading
        viewModelScope.launch {
            try {
                val files = api.listFolders(dirId, cookie())
                _moveUiState.value = C139CloudUiState.Loaded(files, pathNames, dirId)
            } catch (e: Exception) {
                _moveUiState.value = C139CloudUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    private fun downloadHeaders(): Map<String, String> = mapOf(
        "User-Agent" to C139Constants.PC_UA,
        "Referer" to "https://yun.139.com/"
    )

    private suspend fun collectFolderFiles(
        dirId: String,
        prefix: String,
        cookie: String,
        result: MutableList<Pair<ShareFile, String>>,
        depth: Int
    ) {
        if (depth > 12) return
        val list = runCatching { api.listCloudFiles(dirId, cookie) }.getOrDefault(emptyList())
        list.filter { !it.isdir }.forEach { result.add(it to "$prefix/${it.fname}") }
        list.filter { it.isdir }.forEach {
            collectFolderFiles(it.fid, "$prefix/${it.fname}", cookie, result, depth + 1)
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
                val cookie = cookie()
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                collectFolderFiles(folder.fid, folder.fname, cookie, tasks, 0)
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
                        val link = api.getDownloadUrl(file.fid, cookie) ?: return@runCatching
                        downloadManager.enqueue(
                            url = link.downloadUrl,
                            fileName = relPath,
                            size = link.size,
                            platform = DownloadPlatform.C139,
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
                val link = api.getDownloadUrl(file.fid, cookie())
                    ?: throw IllegalStateException("获取下载链接失败")
                pendingDownload = PendingDownload(
                    url = link.downloadUrl,
                    fileName = file.fname.ifBlank { link.filename },
                    size = link.size,
                    headers = mapOf(
                        "User-Agent" to C139Constants.PC_UA,
                        "Referer" to "https://yun.139.com/"
                    )
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
                    platform = DownloadPlatform.C139,
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
                api.renameFile(file.fid, newName, cookie())
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
                val taskId = api.moveFiles(listOf(file.fid), toDirId, cookie())
                    ?: throw IllegalStateException("移动失败")
                pollTask(taskId)
                cloudMessage = "已移动到目标目录"
                actionFile = null
                delay(1500)
                reloadCurrent()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "移动失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun shareFile(period: Int?) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                val coLst = if (file.isdir) emptyList() else listOf(file.fid)
                val caLst = if (file.isdir) listOf(file.fid) else emptyList()
                val info = api.createShare(coLst, caLst, period, file.fname, cookie())
                shareResult = info
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
                val taskId = api.deleteFiles(listOf(file.fid), cookie())
                    ?: throw IllegalStateException("删除失败")
                pollTask(taskId)
                cloudMessage = "已删除「${file.fname}」"
                actionFile = null
                delay(1200)
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
                val cookie = cookie()
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                for (file in files) {
                    if (file.isdir) {
                        collectFolderFiles(file.fid, file.fname, cookie, tasks, 0)
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
                var failCount = 0
                tasks.forEachIndexed { index, (file, relPath) ->
                    if (downloadCancelRequested) return@forEachIndexed
                    folderProgress = "正在加入下载 ${index + 1}/${tasks.size}"
                    runCatching {
                        val link = api.getDownloadUrl(file.fid, cookie) ?: return@runCatching
                        downloadManager.enqueue(
                            url = link.downloadUrl,
                            fileName = if (relPath.contains('/')) relPath else file.fname.ifBlank { link.filename },
                            size = link.size,
                            platform = DownloadPlatform.C139,
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
                cloudMessage = if (failCount > 0) {
                    "已加入 $okCount 个下载任务（$failCount 个失败）"
                } else {
                    "已加入 $okCount 个下载任务"
                }
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

    fun shareSelected(period: Int?) {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                val coLst = files.filter { !it.isdir }.map { it.fid }
                val caLst = files.filter { it.isdir }.map { it.fid }
                val title = if (files.size == 1) files[0].fname else "分享 ${files.size} 个文件"
                val info = api.createShare(coLst, caLst, period, title, cookie())
                shareResult = info
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
                val taskId = api.moveFiles(files.map { it.fid }, toDirId, cookie())
                    ?: throw IllegalStateException("移动失败")
                pollTask(taskId)
                cloudMessage = "已移动 ${files.size} 项"
                exitMultiSelect()
                delay(1500)
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
                val taskId = api.deleteFiles(files.map { it.fid }, cookie())
                    ?: throw IllegalStateException("删除失败")
                pollTask(taskId)
                cloudMessage = "已删除 ${files.size} 项"
                exitMultiSelect()
                delay(1200)
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
        if (current !is C139CloudUiState.Loaded) {
            loadRoot()
            return
        }
        refreshing = true
        viewModelScope.launch {
            try {
                val files = api.listCloudFiles(current.dirId, cookie())
                _uiState.value = C139CloudUiState.Loaded(files, current.pathNames, current.dirId)
            } catch (e: Exception) {
                cloudMessage = e.message ?: "刷新失败"
            } finally {
                refreshing = false
            }
        }
    }

    private fun reloadCurrent() {
        val current = uiState.value
        if (current is C139CloudUiState.Loaded) {
            load(current.dirId, current.pathNames)
        } else {
            loadRoot()
        }
    }

    private fun load(dirId: String, pathNames: List<String>) {
        _uiState.value = C139CloudUiState.Loading
        viewModelScope.launch {
            try {
                val files = api.listCloudFiles(dirId, cookie())
                _uiState.value = C139CloudUiState.Loaded(files, pathNames, dirId)
            } catch (e: Exception) {
                _uiState.value = C139CloudUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    private suspend fun pollTask(taskId: String) {
        delay(500)
        repeat(30) {
            val status = api.getTask(taskId, cookie())
            if (status.status == "Succeed" || status.progress >= 100) return
            if (status.results.any { it.second.isNotBlank() && it.second != "0000" }) {
                throw IllegalStateException("任务失败（${status.results.first().second}）")
            }
            delay(800)
        }
        throw IllegalStateException("任务超时")
    }

    class Factory(
        private val api: C139Api,
        private val cookieProvider: suspend () -> String?,
        private val downloadManager: DownloadManager,
        private val loginState: Flow<Boolean>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            C139CloudViewModel(api, cookieProvider, downloadManager, loginState) as T
    }
}
