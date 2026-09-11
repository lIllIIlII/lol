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
import com.yunx.app.data.network.QuarkApi
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

sealed interface QuarkCloudUiState {
    data object Loading : QuarkCloudUiState
    data class Loaded(
        val files: List<ShareFile>,
        val pathNames: List<String>,
        val dirFid: String
    ) : QuarkCloudUiState
    data class Error(val message: String) : QuarkCloudUiState
}

class QuarkCloudViewModel(
    private val api: QuarkApi,
    private val cookieProvider: suspend () -> String?,
    private val downloadManager: DownloadManager,
    private val loginState: Flow<Boolean>
) : ViewModel() {

    private val _uiState = MutableStateFlow<QuarkCloudUiState>(QuarkCloudUiState.Loading)
    val uiState: StateFlow<QuarkCloudUiState> = _uiState.asStateFlow()

    var actionFile by mutableStateOf<ShareFile?>(null)
        private set

    var cloudMessage by mutableStateOf<String?>(null)
        private set

    var isOperating by mutableStateOf(false)
        private set

    var folderProgress by mutableStateOf<String?>(null)
        private set

    private var downloadCancelRequested = false

    fun cancelDownload() {
        downloadCancelRequested = true
    }

    var refreshing by mutableStateOf(false)
        private set

    var downloadTriggered by mutableStateOf(0)
        private set

    fun consumeDownloadTriggered() {
        downloadTriggered = 0
    }

    var shareResult by mutableStateOf<ShareInfo?>(null)
        private set

    private val dirStack = ArrayDeque<String>()
    private val nameStack = ArrayDeque<String>()

    private val _moveUiState = MutableStateFlow<QuarkCloudUiState>(QuarkCloudUiState.Loading)
    val moveUiState: StateFlow<QuarkCloudUiState> = _moveUiState.asStateFlow()
    private val moveDirStack = ArrayDeque<String>()
    private val moveNameStack = ArrayDeque<String>()

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

    private fun moveLoad(dirFid: String, pathNames: List<String>) {
        _moveUiState.value = QuarkCloudUiState.Loading
        viewModelScope.launch {
            val cookie = cookieProvider()
            if (cookie.isNullOrBlank()) {
                _moveUiState.value = QuarkCloudUiState.Error("请先登录夸克网盘")
                return@launch
            }
            try {
                val files = api.listCloudFiles(dirFid, cookie) ?: emptyList()
                _moveUiState.value = QuarkCloudUiState.Loaded(files, pathNames, dirFid)
            } catch (e: Exception) {
                _moveUiState.value = QuarkCloudUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    init {
        loadRoot()
        viewModelScope.launch {
            loginState
                .drop(1)
                .distinctUntilChanged()
                .collect { loggedIn -> if (loggedIn) loadRoot() }
        }
    }

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

    var multiSelectMode by mutableStateOf(false)
        private set

    private val _selected = mutableStateListOf<ShareFile>()
    val selected: List<ShareFile> get() = _selected

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

    private fun downloadHeaders(cookie: String): Map<String, String> = mapOf(
        "Cookie" to cookie,
        "User-Agent" to com.yunx.app.data.network.QuarkConstants.API_USER_AGENT,
        "Referer" to com.yunx.app.data.network.QuarkConstants.DOWNLOAD_REFERER
    )

    private suspend fun collectFolderFiles(
        dirFid: String,
        prefix: String,
        cookie: String,
        result: MutableList<Pair<ShareFile, String>>,
        depth: Int
    ) {
        if (depth > 12) return
        val list = runCatching { api.listCloudFiles(dirFid, cookie) ?: emptyList() }
            .getOrDefault(emptyList())
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
                val cookie = cookieProvider()
                if (cookie.isNullOrBlank()) {
                    cloudMessage = "请先登录夸克网盘"
                    return@launch
                }
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
                        val link = api.getDownloadLink(file.fid, cookie) ?: return@runCatching
                        val effectiveUrl = com.yunx.app.data.network.QuarkCdn.fastest(link.downloadUrl, cookie)
                        downloadManager.enqueue(
                            url = effectiveUrl,
                            fileName = relPath,
                            size = link.size,
                            platform = DownloadPlatform.QUARK,
                            headers = downloadHeaders(cookie)
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
                val cookie = cookieProvider()
                if (cookie.isNullOrBlank()) {
                    cloudMessage = "请先登录夸克网盘"
                    return@launch
                }
                val link = api.getDownloadLink(file.fid, cookie)
                    ?: throw IllegalStateException("获取下载链接失败")
                val effectiveUrl = com.yunx.app.data.network.QuarkCdn.fastest(link.downloadUrl, cookie)
                pendingDownload = PendingDownload(
                    url = effectiveUrl,
                    fileName = link.filename.ifBlank { file.fname },
                    size = link.size,
                    headers = mapOf(
                        "Cookie" to cookie,
                        "User-Agent" to com.yunx.app.data.network.QuarkConstants.API_USER_AGENT,
                        "Referer" to com.yunx.app.data.network.QuarkConstants.DOWNLOAD_REFERER
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
                    platform = DownloadPlatform.QUARK,
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
                val cookie = cookieProvider()
                if (cookie.isNullOrBlank()) {
                    cloudMessage = "请先登录夸克网盘"
                    return@launch
                }
                if (api.renameFile(file.fid, newName, cookie)) {
                    cloudMessage = "已重命名"
                    actionFile = null
                    reloadCurrent()
                } else {
                    cloudMessage = "重命名失败"
                }
            } catch (e: Exception) {
                cloudMessage = e.message ?: "重命名失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun moveFile(toDirFid: String) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                val cookie = cookieProvider()
                if (cookie.isNullOrBlank()) {
                    cloudMessage = "请先登录夸克网盘"
                    return@launch
                }
                api.moveFile(file.fid, toDirFid, cookie)
                    ?: throw IllegalStateException("移动失败")
                cloudMessage = "已移动到目标目录"
                actionFile = null
                kotlinx.coroutines.delay(1500)
                reloadCurrent()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "移动失败"
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
                val cookie = cookieProvider()
                if (cookie.isNullOrBlank()) {
                    cloudMessage = "请先登录夸克网盘"
                    return@launch
                }
                api.deleteFile(file.fid, cookie)
                    ?: throw IllegalStateException("删除失败")
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

    fun shareFile(urlType: Int, passcode: String, expiredType: Int) {
        val file = actionFile ?: return
        viewModelScope.launch {
            isOperating = true
            try {
                val cookie = cookieProvider()
                if (cookie.isNullOrBlank()) {
                    cloudMessage = "请先登录夸克网盘"
                    return@launch
                }
                val shareId = api.createShare(
                    fidList = listOf(file.fid),
                    title = file.fname,
                    urlType = urlType,
                    passcode = passcode,
                    expiredType = expiredType,
                    cookie = cookie
                ) ?: throw IllegalStateException("创建分享失败")
                val info = api.getShareInfo(shareId, cookie)
                    ?: throw IllegalStateException("获取分享链接失败")
                shareResult = info
            } catch (e: Exception) {
                cloudMessage = e.message ?: "分享失败"
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
                val cookie = cookieProvider()
                if (cookie.isNullOrBlank()) {
                    cloudMessage = "请先登录夸克网盘"
                    return@launch
                }
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
                        val link = api.getDownloadLink(file.fid, cookie) ?: return@runCatching
                        val effectiveUrl = com.yunx.app.data.network.QuarkCdn.fastest(link.downloadUrl, cookie)
                        downloadManager.enqueue(
                            url = effectiveUrl,
                            fileName = if (relPath.contains('/')) relPath else link.filename.ifBlank { relPath },
                            size = link.size,
                            platform = DownloadPlatform.QUARK,
                            headers = downloadHeaders(cookie)
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

    fun shareSelected(urlType: Int, passcode: String, expiredType: Int) {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                val cookie = cookieProvider()
                if (cookie.isNullOrBlank()) {
                    cloudMessage = "请先登录夸克网盘"
                    return@launch
                }
                val shareId = api.createShare(
                    fidList = files.map { it.fid },
                    title = if (files.size == 1) files[0].fname else "分享 ${files.size} 个文件",
                    urlType = urlType,
                    passcode = passcode,
                    expiredType = expiredType,
                    cookie = cookie
                ) ?: throw IllegalStateException("创建分享失败")
                val info = api.getShareInfo(shareId, cookie)
                    ?: throw IllegalStateException("获取分享链接失败")
                shareResult = info
                exitMultiSelect()
            } catch (e: Exception) {
                cloudMessage = e.message ?: "分享失败"
            } finally {
                isOperating = false
            }
        }
    }

    fun moveSelected(toDirFid: String) {
        val files = _selected.toList()
        if (files.isEmpty()) return
        viewModelScope.launch {
            isOperating = true
            try {
                val cookie = cookieProvider()
                if (cookie.isNullOrBlank()) {
                    cloudMessage = "请先登录夸克网盘"
                    return@launch
                }
                files.forEach { file ->
                    api.moveFile(file.fid, toDirFid, cookie)
                }
                cloudMessage = "已移动 ${files.size} 项"
                exitMultiSelect()
                kotlinx.coroutines.delay(1500)
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
                val cookie = cookieProvider()
                if (cookie.isNullOrBlank()) {
                    cloudMessage = "请先登录夸克网盘"
                    return@launch
                }
                files.forEach { file ->
                    api.deleteFile(file.fid, cookie)
                }
                cloudMessage = "已删除 ${files.size} 项"
                exitMultiSelect()
                kotlinx.coroutines.delay(1200)
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
        if (current !is QuarkCloudUiState.Loaded) {
            loadRoot()
            return
        }
        refreshing = true
        viewModelScope.launch {
            val cookie = cookieProvider()
            if (cookie.isNullOrBlank()) {
                refreshing = false
                return@launch
            }
            try {
                val files = api.listCloudFiles(current.dirFid, cookie) ?: emptyList()
                _uiState.value = QuarkCloudUiState.Loaded(files, current.pathNames, current.dirFid)
            } catch (e: Exception) {
                cloudMessage = e.message ?: "刷新失败"
            } finally {
                refreshing = false
            }
        }
    }

    private fun reloadCurrent() {
        val current = uiState.value
        if (current is QuarkCloudUiState.Loaded) {
            load(current.dirFid, current.pathNames)
        } else {
            loadRoot()
        }
    }

    private fun load(dirFid: String, pathNames: List<String>) {
        _uiState.value = QuarkCloudUiState.Loading
        viewModelScope.launch {
            val cookie = cookieProvider()
            if (cookie.isNullOrBlank()) {
                _uiState.value = QuarkCloudUiState.Error("请先登录夸克网盘")
                return@launch
            }
            try {
                val files = api.listCloudFiles(dirFid, cookie) ?: emptyList()
                _uiState.value = QuarkCloudUiState.Loaded(files, pathNames, dirFid)
            } catch (e: Exception) {
                _uiState.value = QuarkCloudUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    class Factory(
        private val api: QuarkApi,
        private val cookieProvider: suspend () -> String?,
        private val downloadManager: DownloadManager,
        private val loginState: Flow<Boolean>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuarkCloudViewModel(api, cookieProvider, downloadManager, loginState) as T
    }
}
