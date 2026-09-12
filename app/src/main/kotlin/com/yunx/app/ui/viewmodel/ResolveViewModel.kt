package com.yunx.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.BookmarkDao
import com.yunx.app.data.db.BookmarkEntity
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.BaiduConstants
import com.yunx.app.data.network.C139Constants
import com.yunx.app.data.network.LanzouApi
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.data.network.QuarkConstants
import com.yunx.app.data.network.QuarkCdn
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.UCConstants
import com.yunx.app.data.network.XunleiConstants
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareSession
import com.yunx.app.data.repository.BaiduAccountRepository
import com.yunx.app.data.repository.BaiduResolveRepository
import com.yunx.app.data.repository.C139AccountRepository
import com.yunx.app.data.repository.C139ResolveRepository
import com.yunx.app.data.repository.CowTransferResolveRepository
import com.yunx.app.data.repository.Cloud189ResolveRepository
import com.yunx.app.data.repository.LanzouResolveRepository
import com.yunx.app.data.repository.CtfileResolveRepository
import com.yunx.app.data.repository.WenshushuResolveRepository
import com.yunx.app.data.repository.SimpleAccountRepository
import com.yunx.app.data.repository.SimpleNetdisk
import com.yunx.app.data.repository.WsDiskResolveRepository
import com.yunx.app.data.repository.Pan123AccountRepository
import com.yunx.app.data.repository.Pan123ResolveRepository
import com.yunx.app.data.repository.QuarkAccountRepository
import com.yunx.app.data.repository.QuarkResolveRepository
import com.yunx.app.data.repository.ShareResolveRepository
import com.yunx.app.data.repository.UCAccountRepository
import com.yunx.app.data.repository.UCResolveRepository
import com.yunx.app.data.repository.XunleiAccountRepository
import com.yunx.app.data.repository.XunleiResolveRepository
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.ui.SnackbarController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ResolveUiState {
    data object Idle : ResolveUiState
    data object Loading : ResolveUiState
    data class Detail(val session: ShareSession, val files: List<ShareFile>) : ResolveUiState
    data class Error(val message: String) : ResolveUiState
}

data class SavedDownloadPrompt(
    val fid: String,
    val dirFid: String,
    val fileName: String,
    val platformLabel: String
)

class ResolveViewModel(
    private val accountRepository: QuarkAccountRepository,
    private val resolveRepository: QuarkResolveRepository,
    private val ucAccountRepository: UCAccountRepository,
    private val ucResolveRepository: UCResolveRepository,
    private val xunleiAccountRepository: XunleiAccountRepository,
    private val xunleiResolveRepository: XunleiResolveRepository,
    private val baiduAccountRepository: BaiduAccountRepository,
    private val baiduResolveRepository: BaiduResolveRepository,
    private val c139AccountRepository: C139AccountRepository,
    private val c139ResolveRepository: C139ResolveRepository,
    private val pan123AccountRepository: Pan123AccountRepository,
    private val pan123ResolveRepository: Pan123ResolveRepository,
    private val simpleAccountRepository: SimpleAccountRepository,
    private val lanzouResolveRepository: LanzouResolveRepository,
    private val cowTransferResolveRepository: CowTransferResolveRepository,
    private val feijiResolveRepository: WsDiskResolveRepository,
    private val ilanzouResolveRepository: WsDiskResolveRepository,
    private val ctfileResolveRepository: CtfileResolveRepository,
    private val cloud189ResolveRepository: Cloud189ResolveRepository,
    private val wenshushuResolveRepository: WenshushuResolveRepository,
    private val downloadManager: DownloadManager,
    private val bookmarkDao: BookmarkDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    var uiState by mutableStateOf<ResolveUiState>(ResolveUiState.Idle)
        private set

    var downloadLink by mutableStateOf<DownloadLink?>(null)
        private set

    var downloadError by mutableStateOf<String?>(null)
    private set

    var isFetchingDownloadLink by mutableStateOf(false)
        private set

    var saveTarget by mutableStateOf<ShareFile?>(null)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var saveMessage by mutableStateOf<String?>(null)
        private set

    var saveDownloadAsk by mutableStateOf<SavedDownloadPrompt?>(null)
        private set

    var isSavedFileLoading by mutableStateOf(false)
        private set

    private val loginOptional: Boolean
        get() = currentPlatform == SharePlatform.LANZOU ||
            currentPlatform == SharePlatform.ILANZOU ||
            currentPlatform == SharePlatform.COWTRANSFER ||
            currentPlatform == SharePlatform.FEIJI ||
            currentPlatform == SharePlatform.CTFILE ||
            currentPlatform == SharePlatform.WENSHUSHU ||
            currentPlatform == SharePlatform.CLOUD189

    val canSave: Boolean
        get() = currentPlatform == SharePlatform.QUARK ||
            currentPlatform == SharePlatform.UC ||
            currentPlatform == SharePlatform.XUNLEI ||
            currentPlatform == SharePlatform.BAIDU ||
            currentPlatform == SharePlatform.C139 ||
            currentPlatform == SharePlatform.PAN123

    val isSaveXunlei: Boolean
        get() = currentPlatform == SharePlatform.XUNLEI

    val isSaveBaidu: Boolean
        get() = currentPlatform == SharePlatform.BAIDU

    val isBaidu: Boolean
        get() = currentPlatform == SharePlatform.BAIDU

    val isSaveC139: Boolean
        get() = currentPlatform == SharePlatform.C139

    val isSaveUC: Boolean
        get() = currentPlatform == SharePlatform.UC

    val isSavePan123: Boolean
        get() = currentPlatform == SharePlatform.PAN123

    fun requestSave(file: ShareFile) {
        saveTarget = file
        saveMessage = null
    }

    fun dismissSave() {
        saveTarget = null
        isSaving = false
    }

    fun consumeSaveMessage() {
        saveMessage = null
    }

    fun saveToCloud(toDirFid: String) {
        val file = saveTarget ?: return
        val s = session ?: return
        viewModelScope.launch {
            isSaving = true
            try {
                when (currentPlatform) {
                    SharePlatform.XUNLEI -> {
                        val credential = currentCredential()
                        if (credential.isNullOrBlank()) {
                            saveMessage = "请先登录迅雷网盘"
                            return@launch
                        }
                        xunleiResolveRepository.transferFile(s, file, toDirFid, credential)
                            .onSuccess { newFid ->
                                saveMessage = "已保存到迅雷网盘"
                                saveTarget = null
                                offerSavedDownload(newFid, toDirFid, file)
                            }
                            .onFailure {
                                saveMessage = it.message ?: "转存失败"
                            }
                    }
                    SharePlatform.BAIDU -> {
                        val credential = currentCredential()
                        if (credential.isNullOrBlank()) {
                            saveMessage = "请先登录百度网盘"
                            return@launch
                        }
                        baiduResolveRepository.transferFile(s, file, toDirFid, credential)
                            .onSuccess { newFid ->
                                saveMessage = "已保存到百度网盘"
                                saveTarget = null
                                offerSavedDownload(newFid, toDirFid, file)
                            }
                            .onFailure {
                                saveMessage = it.message ?: "转存失败"
                            }
                    }
                    SharePlatform.C139 -> {
                        val credential = currentCredential()
                        if (credential.isNullOrBlank()) {
                            saveMessage = "请先登录139网盘"
                            return@launch
                        }
                        c139ResolveRepository.transferFile(s, file, toDirFid, credential)
                            .onSuccess { newFid ->
                                saveMessage = "已保存到139网盘"
                                saveTarget = null
                                offerSavedDownload(newFid, toDirFid, file)
                            }
                            .onFailure {
                                saveMessage = it.message ?: "转存失败"
                            }
                    }
                    SharePlatform.UC -> {
                        val credential = currentCredential()
                        if (credential.isNullOrBlank()) {
                            saveMessage = "请先登录UC网盘"
                            return@launch
                        }
                        ucResolveRepository.transferFile(s, file, toDirFid, credential)
                            .onSuccess { newFid ->
                                saveMessage = "已保存到UC网盘"
                                saveTarget = null
                                offerSavedDownload(newFid, toDirFid, file)
                            }
                            .onFailure {
                                saveMessage = it.message ?: "转存失败"
                            }
                    }
                    SharePlatform.PAN123 -> {
                        val credential = currentCredential()
                        if (credential.isNullOrBlank()) {
                            saveMessage = "请先登录123云盘"
                            return@launch
                        }
                        pan123ResolveRepository.transferFile(s, file, toDirFid, credential)
                            .onSuccess { newFid ->
                                saveMessage = "已保存到123云盘"
                                saveTarget = null
                                offerSavedDownload(newFid, toDirFid, file)
                            }
                            .onFailure {
                                saveMessage = it.message ?: "转存失败"
                            }
                    }
                    else -> {
                        val credential = currentCredential()
                        if (credential.isNullOrBlank()) {
                            saveMessage = "请先登录夸克网盘"
                            return@launch
                        }
                        resolveRepository.saveToCloud(s, file, toDirFid, credential)
                            .onSuccess { newFid ->
                                saveMessage = "已保存到夸克网盘"
                                saveTarget = null
                                offerSavedDownload(newFid, toDirFid, file)
                            }
                            .onFailure {
                                saveMessage = it.message ?: "转存失败"
                            }
                    }
                }
            } finally {
                isSaving = false
            }
        }
    }

    private fun offerSavedDownload(newFid: String, dirFid: String, file: ShareFile) {
        if (!settingsRepository.askDownloadAfterSave) return
        if (file.isdir) return
        if (newFid.isBlank() || newFid == "0") return
        saveDownloadAsk = SavedDownloadPrompt(
            fid = newFid,
            dirFid = dirFid,
            fileName = file.fname,
            platformLabel = platformName()
        )
    }

    fun confirmSaveDownload() {
        val prompt = saveDownloadAsk ?: return
        saveDownloadAsk = null
        viewModelScope.launch {
            isSavedFileLoading = true
            try {
                val credential = currentCredential()
                val quarkCred = when (currentPlatform) {
                    SharePlatform.QUARK -> accountRepository.getFreshCookie() ?: credential
                    SharePlatform.UC -> ucAccountRepository.getFreshCookie() ?: credential
                    else -> credential
                }
                val result = when (currentPlatform) {
                    SharePlatform.XUNLEI -> xunleiResolveRepository.getDownloadLink(prompt.fid, quarkCred)
                    SharePlatform.BAIDU -> baiduResolveRepository.getDownloadLink(prompt.fid, quarkCred)
                    SharePlatform.C139 -> c139ResolveRepository.getDownloadLink(prompt.fid, quarkCred)
                    SharePlatform.PAN123 -> pan123ResolveRepository.getSavedFileDownloadLink(prompt.fid, prompt.dirFid, quarkCred)
                    SharePlatform.UC -> ucResolveRepository.getDownloadLink(prompt.fid, quarkCred)
                    else -> resolveRepository.getDownloadLink(prompt.fid, quarkCred)
                }
                result.onSuccess { link ->
                    val fixed = link.copy(
                        filename = link.filename.ifBlank { prompt.fileName },
                        size = if (link.size > 0) link.size else 0L
                    )
                    enqueueDownload(fixed, quarkCred, prompt.fileName.ifBlank { fixed.filename })
                    downloadStarted = true
                }.onFailure {
                    saveMessage = it.message ?: "获取下载链接失败"
                }
            } finally {
                isSavedFileLoading = false
            }
        }
    }

    fun dismissSaveDownload() {
        saveDownloadAsk = null
    }

    var downloadStarted by mutableStateOf(false)
        private set

    var multiSelectMode by mutableStateOf(false)
        private set

    private val _selected = mutableStateListOf<ShareFile>()
    val selected: List<ShareFile> get() = _selected

    var isBatchWorking by mutableStateOf(false)
        private set

    var batchProgress by mutableStateOf<String?>(null)
        private set

    private var batchCancelRequested = false

    fun cancelBatch() {
        batchCancelRequested = true
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

    fun batchSaveToCloud() {
        val files = _selected.toList()
        val s = session ?: return
        viewModelScope.launch {
            isBatchWorking = true
            batchCancelRequested = false
            try {
                val credential = currentCredential()
                if (credential.isNullOrBlank() && !loginOptional) {
                    downloadError = "请先登录${platformName()}"
                    return@launch
                }
                var okCount = 0
                var interrupted = false
                for (file in files) {
                    if (batchCancelRequested) {
                        interrupted = true
                        downloadError = "已中断批量转存"
                        break
                    }
                    runCatching {
                        when (currentPlatform) {
                            SharePlatform.XUNLEI -> {
                                xunleiResolveRepository.transferFile(s, file, "", credential)
                            }
                            SharePlatform.BAIDU -> {
                                baiduResolveRepository.transferFile(s, file, "/", credential)
                            }
                            SharePlatform.C139 -> {
                                c139ResolveRepository.transferFile(s, file, "/", credential)
                            }
                            SharePlatform.UC -> {
                                ucResolveRepository.transferFile(s, file, UCConstants.DEFAULT_PDIR_FID, credential)
                            }
                            SharePlatform.PAN123 -> {
                                pan123ResolveRepository.transferFile(s, file, "0", credential)
                            }
                            else -> {
                                resolveRepository.saveToCloud(s, file, QuarkConstants.DEFAULT_PDIR_FID, credential)
                            }
                        }
                    }.onSuccess { okCount++ }
                }
                if (!interrupted) {
                    downloadError = if (okCount > 0) "已转存 $okCount 项到${platformName()}" else "转存失败"
                }
                exitMultiSelect()
            } finally {
                isBatchWorking = false
                batchCancelRequested = false
            }
        }
    }

    fun batchDownload() {
        val files = _selected.toList()
        val s = session ?: return
        viewModelScope.launch {
            isBatchWorking = true
            batchProgress = "正在收集文件…"
            batchCancelRequested = false
            try {
                val credential = currentCredential()
                if (credential.isNullOrBlank() && !loginOptional) {
                    downloadError = "请先登录网盘"
                    return@launch
                }
                val quarkCred = when (currentPlatform) {
                    SharePlatform.QUARK -> accountRepository.getFreshCookie() ?: credential
                    SharePlatform.UC -> ucAccountRepository.getFreshCookie() ?: credential
                    else -> credential
                }
                val tasks = mutableListOf<Pair<ShareFile, String>>()
                for (file in files) {
                    if (file.isdir) {
                        collectShareFolder(s, file.fid, file.fname, quarkCred, tasks, 0)
                    } else {
                        tasks.add(file to "")
                    }
                }
                if (tasks.isEmpty()) {
                    downloadError = "所选文件夹为空"
                    exitMultiSelect()
                    return@launch
                }
                var okCount = 0
                var interrupted = false
                for ((index, task) in tasks.withIndex()) {
                    if (batchCancelRequested) {
                        interrupted = true
                        downloadError = "已中断批量下载"
                        break
                    }
                    val (file, relPath) = task
                    batchProgress = "${index + 1}/${tasks.size}"
                    runCatching {
                        currentRepo().getShareDownloadLink(s, file, quarkCred).getOrNull()?.let { link ->
                            enqueueDownload(link, quarkCred, if (relPath.isBlank()) link.filename else relPath)
                            okCount++
                        }
                    }
                }
                if (!interrupted) {
                    downloadError = if (okCount > 0) "已加入 $okCount 个下载任务" else "获取下载链接失败"
                    if (okCount > 0) downloadStarted = true
                }
                exitMultiSelect()
            } finally {
                isBatchWorking = false
                batchProgress = null
                batchCancelRequested = false
            }
        }
    }

    private suspend fun collectShareFolder(
        s: ShareSession,
        dirFid: String,
        prefix: String,
        credential: String,
        result: MutableList<Pair<ShareFile, String>>,
        depth: Int
    ) {
        if (depth > 12) return
        val files = runCatching {
            currentRepo().listFiles(s, dirFid, credential).getOrNull() ?: emptyList()
        }.getOrDefault(emptyList())
        files.filter { !it.isdir }.forEach { result.add(it to "$prefix/${it.fname}") }
        files.filter { it.isdir }.forEach {
            collectShareFolder(s, it.fid, "$prefix/${it.fname}", credential, result, depth + 1)
        }
    }

    fun consumeDownloadStarted() {
        downloadStarted = false
    }

    fun consumeDownloadError() {
        downloadError = null
    }

    private var session: ShareSession? = null
    private var currentDirFid = QuarkConstants.DEFAULT_PDIR_FID
    private val dirStack = ArrayDeque<String>()

    private var currentLink: String? = null
    private var currentPwd: String? = null

    var pathNames by mutableStateOf<List<String>>(emptyList())
        private set

    private var currentPlatform: SharePlatform = SharePlatform.QUARK

    private suspend fun currentCredential(): String = when (currentPlatform) {
        SharePlatform.UC -> ucAccountRepository.getAccount()?.cookie.orEmpty()
        SharePlatform.XUNLEI -> xunleiAccountRepository.getAccount()?.accessToken.orEmpty()
        SharePlatform.BAIDU -> baiduAccountRepository.getAccount()?.cookie.orEmpty()
        SharePlatform.C139 -> c139AccountRepository.getAccount()?.cookie.orEmpty()
        SharePlatform.PAN123 -> pan123AccountRepository.getAccount()?.accessToken.orEmpty()
        SharePlatform.LANZOU -> simpleAccountRepository.getAccount(SimpleNetdisk.LANZOU)?.cookie.orEmpty()
        SharePlatform.CLOUD189 -> simpleAccountRepository.getAccount(SimpleNetdisk.CLOUD189)?.cookie.orEmpty()
        SharePlatform.COWTRANSFER -> ""
        SharePlatform.FEIJI -> ""
        SharePlatform.ILANZOU -> ""
        SharePlatform.CTFILE -> ""
        SharePlatform.WENSHUSHU -> ""
        else -> accountRepository.getAccount()?.cookie.orEmpty()
    }

    private fun currentRepo(): ShareResolveRepository = when (currentPlatform) {
        SharePlatform.UC -> ucResolveRepository
        SharePlatform.XUNLEI -> xunleiResolveRepository
        SharePlatform.BAIDU -> baiduResolveRepository
        SharePlatform.C139 -> c139ResolveRepository
        SharePlatform.PAN123 -> pan123ResolveRepository
        SharePlatform.LANZOU -> lanzouResolveRepository
        SharePlatform.COWTRANSFER -> cowTransferResolveRepository
        SharePlatform.FEIJI -> feijiResolveRepository
        SharePlatform.ILANZOU -> ilanzouResolveRepository
        SharePlatform.CTFILE -> ctfileResolveRepository
        SharePlatform.WENSHUSHU -> wenshushuResolveRepository
        SharePlatform.CLOUD189 -> cloud189ResolveRepository
        else -> resolveRepository
    }

    private fun currentDefaultDirFid(): String = when (currentPlatform) {
        SharePlatform.UC -> UCConstants.DEFAULT_PDIR_FID
        SharePlatform.XUNLEI -> "0"
        SharePlatform.BAIDU -> ""
        SharePlatform.C139 -> "0"
        SharePlatform.PAN123 -> "0"
        SharePlatform.LANZOU, SharePlatform.COWTRANSFER, SharePlatform.FEIJI, SharePlatform.ILANZOU,
        SharePlatform.CTFILE, SharePlatform.WENSHUSHU, SharePlatform.CLOUD189 -> "0"
        else -> QuarkConstants.DEFAULT_PDIR_FID
    }

    private fun platformName(): String = when (currentPlatform) {
        SharePlatform.UC -> "UC 网盘"
        SharePlatform.XUNLEI -> "迅雷网盘"
        SharePlatform.BAIDU -> "百度网盘"
        SharePlatform.C139 -> "139 网盘"
        SharePlatform.PAN123 -> "123云盘"
        SharePlatform.LANZOU -> "蓝奏云"
        SharePlatform.ILANZOU -> "蓝奏云优享版"
        SharePlatform.COWTRANSFER -> "奶牛快传"
        SharePlatform.FEIJI -> "小飞机网盘"
        SharePlatform.CTFILE -> "城通网盘"
        SharePlatform.WENSHUSHU -> "文叔叔"
        SharePlatform.CLOUD189 -> "天翼云盘"
        else -> "夸克网盘"
    }

    fun startResolve(link: String, pwd: String?) {
        currentLink = link
        currentPwd = pwd
        viewModelScope.launch {
            uiState = ResolveUiState.Loading
            val parsed = ShareLinkParser.parse(link)
            if (parsed == null) {
                uiState = ResolveUiState.Error("无法识别分享链接")
                return@launch
            }
            currentPlatform = parsed.platform
            val credential = currentCredential()
            if (credential.isNullOrBlank() && !loginOptional) {
                uiState = ResolveUiState.Error("请先在「网盘」页登录${platformName()}")
                return@launch
            }
            val repo = currentRepo()
            repo.createSession(link, pwd, credential)
                .onSuccess { s ->
                    session = s
                    currentDirFid = currentDefaultDirFid()
                    dirStack.clear()
                    pathNames = emptyList()
                    loadFiles(s, currentDirFid, credential, repo)
                }
                .onFailure { e ->
                    uiState = ResolveUiState.Error(e.message ?: "解析失败")
                }
        }
    }

    fun openFolder(file: ShareFile) {
        val s = session ?: return
        dirStack.addLast(currentDirFid)
        pathNames = pathNames + file.fname
        currentDirFid = file.fid
        viewModelScope.launch {
            uiState = ResolveUiState.Loading
            val credential = currentCredential()
            if (credential.isNullOrBlank() && !loginOptional) {
                uiState = ResolveUiState.Error("登录已失效，请重新登录")
                return@launch
            }
            loadFiles(s, file.fid, credential.orEmpty(), currentRepo())
        }
    }

    fun goBack() {
        val s = session ?: return
        if (dirStack.isEmpty()) return
        currentDirFid = dirStack.removeLast()
        pathNames = pathNames.dropLast(1)
        viewModelScope.launch {
            uiState = ResolveUiState.Loading
            val credential = currentCredential()
            if (credential.isNullOrBlank() && !loginOptional) return@launch
            loadFiles(s, currentDirFid, credential.orEmpty(), currentRepo())
        }
    }

    fun navigateBack() {
        if (dirStack.isEmpty()) {
            backToInput()
        } else {
            goBack()
        }
    }

    fun backToInput() {
        session = null
        downloadLink = null
        currentLink = null
        currentPwd = null
        pathNames = emptyList()
        uiState = ResolveUiState.Idle
    }

    fun addCurrentToBookmark(title: String, category: String) {
        val link = currentLink?.takeIf { it.isNotBlank() }
        if (link == null) {
            SnackbarController.show("缺少分享链接")
            return
        }
        val cat = category.ifBlank { BookmarkEntity.DEFAULT_CATEGORY }
        val resolvedTitle = title.ifBlank { session?.title.orEmpty() }
        viewModelScope.launch {
            bookmarkDao.insert(
                BookmarkEntity(
                    link = link,
                    title = resolvedTitle,
                    platform = currentPlatform.name,
                    pwd = currentPwd.orEmpty(),
                    category = cat
                )
            )
            SnackbarController.show("已收藏到「$cat」")
        }
    }

    fun navigateToLevel(level: Int) {
        val s = session ?: return
        if (level < 0 || level > pathNames.size) return
        if (level == pathNames.size) return
        while (dirStack.size > level) dirStack.removeLast()
        currentDirFid = if (dirStack.isEmpty()) currentDefaultDirFid() else dirStack.last()
        pathNames = pathNames.take(level)
        viewModelScope.launch {
            val credential = currentCredential() ?: return@launch
            loadFiles(s, currentDirFid, credential, currentRepo())
        }
    }

    fun fetchDownloadLink(file: ShareFile) {
        viewModelScope.launch {
            downloadLink = null
            downloadError = null
            isFetchingDownloadLink = true
            try {
                val s = session
                if (s == null) {
                    downloadError = "请先解析分享"
                    return@launch
                }
                val credential = currentCredential()
                if (credential.isNullOrBlank() && !loginOptional) {
                    downloadError = "登录已失效，请重新登录"
                    return@launch
                }
                val quarkCred = when (currentPlatform) {
                    SharePlatform.QUARK -> accountRepository.getFreshCookie() ?: credential
                    SharePlatform.UC -> ucAccountRepository.getFreshCookie() ?: credential
                    else -> credential
                }
                currentRepo().getShareDownloadLink(s, file, quarkCred)
                    .onSuccess { downloadLink = it }
                    .onFailure { downloadError = it.message ?: "获取下载链接失败" }
            } finally {
                isFetchingDownloadLink = false
            }
        }
    }

    fun dismissDownloadDialog() {
        val link = downloadLink
        downloadLink = null
        if (link?.cleanupDirFid != null) {
            viewModelScope.launch {
                val credential = accountRepository.getAccount()?.cookie ?: return@launch
                link.cleanupDirFid?.let { dirFid ->
                    resolveRepository.cleanupTempDir(dirFid, credential)
                }
            }
        }
    }

    private suspend fun enqueueDownload(
        link: DownloadLink,
        credential: String,
        fileName: String = link.filename
    ) {
        val isUC = currentPlatform == SharePlatform.UC
        val isXunlei = currentPlatform == SharePlatform.XUNLEI
        val isBaidu = currentPlatform == SharePlatform.BAIDU
        val isC139 = currentPlatform == SharePlatform.C139
        val isPan123 = currentPlatform == SharePlatform.PAN123
        val isQuark = currentPlatform == SharePlatform.QUARK
        val isLanzou = currentPlatform == SharePlatform.LANZOU
        val isCow = currentPlatform == SharePlatform.COWTRANSFER
        val isFeiji = currentPlatform == SharePlatform.FEIJI
        val isIlanzou = currentPlatform == SharePlatform.ILANZOU
        val isCtfile = currentPlatform == SharePlatform.CTFILE
        val isWss = currentPlatform == SharePlatform.WENSHUSHU
        val isCloud189 = currentPlatform == SharePlatform.CLOUD189
        val platform = when {
            isXunlei -> DownloadPlatform.XUNLEI
            isUC -> DownloadPlatform.UC
            isBaidu -> DownloadPlatform.BAIDU
            isC139 -> DownloadPlatform.C139
            isPan123 -> DownloadPlatform.PAN123
            isLanzou -> DownloadPlatform.LANZOU
            isCow -> DownloadPlatform.COWTRANSFER
            isFeiji -> DownloadPlatform.FEIJI
            isIlanzou -> DownloadPlatform.ILANZOU
            isCtfile -> DownloadPlatform.CTFILE
            isWss -> DownloadPlatform.WENSHUSHU
            isCloud189 -> DownloadPlatform.CLOUD189
            else -> DownloadPlatform.QUARK
        }
        val effectiveCredential = when (currentPlatform) {
            SharePlatform.QUARK -> accountRepository.getFreshCookie() ?: credential
            SharePlatform.UC -> ucAccountRepository.getFreshCookie() ?: credential
            else -> credential
        }
        val headers = when {
            isLanzou -> mapOf(
                "User-Agent" to LanzouApi.USER_AGENT,
                "Referer" to "https://pc.woozooo.com/"
            )
            isCow || isFeiji || isIlanzou -> mapOf("User-Agent" to LanzouApi.USER_AGENT)
            isCtfile || isWss -> mapOf("User-Agent" to LanzouApi.USER_AGENT)
            isCloud189 -> mapOf("User-Agent" to com.yunx.app.data.network.Cloud189Api.USER_AGENT)
            isXunlei -> mapOf("User-Agent" to XunleiConstants.APP_UA)
            isBaidu -> mapOf(
                "Cookie" to credential,
                "User-Agent" to BaiduConstants.UA_NETDISK
            )
            isC139 -> mapOf("User-Agent" to C139Constants.PC_UA)
            isPan123 -> mapOf(
                "User-Agent" to Pan123Constants.WEB_UA,
                "Referer" to Pan123Constants.DOWNLOAD_REFERER
            )
            isUC -> mapOf(
                "Cookie" to credential,
                "User-Agent" to UCConstants.USER_AGENT,
                "Referer" to UCConstants.DOWNLOAD_REFERER,
                "Origin" to UCConstants.WEB_ORIGIN
            )
            else -> mapOf(
                "Cookie" to effectiveCredential,
                "User-Agent" to QuarkConstants.API_USER_AGENT,
                "Referer" to QuarkConstants.DOWNLOAD_REFERER
            )
        }
        val effectiveUrl = if (isQuark) {
            QuarkCdn.fastest(link.downloadUrl, effectiveCredential)
        } else {
            link.downloadUrl
        }
        downloadManager.enqueue(
            url = effectiveUrl,
            fileName = fileName,
            headers = headers,
            size = link.size,
            platform = platform
        ) {
            val dirFid = link.cleanupDirFid
            if (dirFid != null) {
                val credential = currentCredential()
                if (!credential.isNullOrBlank()) {
                    resolveRepository.cleanupTempDir(dirFid, credential)
                }
            }
        }
    }

    fun startDownload(link: DownloadLink) {
        viewModelScope.launch {
            downloadLink = null
            val credential = currentCredential()
            if (credential.isNullOrBlank()) {
                downloadError = "请先登录网盘"
                return@launch
            }
            enqueueDownload(link, credential)
            downloadStarted = true
        }
    }

    private suspend fun loadFiles(
        s: ShareSession,
        dirFid: String,
        credential: String,
        repo: ShareResolveRepository
    ) {
        repo.listFiles(s, dirFid, credential)
            .onSuccess { files ->
                uiState = ResolveUiState.Detail(s, files)
            }
            .onFailure { e ->
                uiState = ResolveUiState.Error(e.message ?: "获取文件列表失败")
            }
    }

    class Factory(
        private val accountRepository: QuarkAccountRepository,
        private val resolveRepository: QuarkResolveRepository,
        private val ucAccountRepository: UCAccountRepository,
        private val ucResolveRepository: UCResolveRepository,
        private val xunleiAccountRepository: XunleiAccountRepository,
        private val xunleiResolveRepository: XunleiResolveRepository,
        private val baiduAccountRepository: BaiduAccountRepository,
        private val baiduResolveRepository: BaiduResolveRepository,
        private val c139AccountRepository: C139AccountRepository,
        private val c139ResolveRepository: C139ResolveRepository,
        private val pan123AccountRepository: Pan123AccountRepository,
        private val pan123ResolveRepository: Pan123ResolveRepository,
        private val simpleAccountRepository: SimpleAccountRepository,
        private val lanzouResolveRepository: LanzouResolveRepository,
        private val cowTransferResolveRepository: CowTransferResolveRepository,
        private val feijiResolveRepository: WsDiskResolveRepository,
        private val ilanzouResolveRepository: WsDiskResolveRepository,
        private val ctfileResolveRepository: CtfileResolveRepository,
        private val cloud189ResolveRepository: Cloud189ResolveRepository,
        private val wenshushuResolveRepository: WenshushuResolveRepository,
        private val downloadManager: DownloadManager,
        private val bookmarkDao: BookmarkDao,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ResolveViewModel::class.java))
            return ResolveViewModel(
                accountRepository, resolveRepository,
                ucAccountRepository, ucResolveRepository,
                xunleiAccountRepository, xunleiResolveRepository,
                baiduAccountRepository, baiduResolveRepository,
                c139AccountRepository, c139ResolveRepository,
                pan123AccountRepository, pan123ResolveRepository,
                simpleAccountRepository,
                lanzouResolveRepository,
                cowTransferResolveRepository,
                feijiResolveRepository,
                ilanzouResolveRepository,
                ctfileResolveRepository,
                cloud189ResolveRepository,
                wenshushuResolveRepository,
                downloadManager,
                bookmarkDao,
                settingsRepository
            ) as T
        }
    }
}
