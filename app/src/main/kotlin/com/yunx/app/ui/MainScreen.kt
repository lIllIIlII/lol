package com.yunx.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.res.Configuration
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.ChunkDownloader
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.backup.AuthBackupManager
import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.data.update.UpdateChecker
import com.yunx.app.data.repository.BaiduAccountRepository
import com.yunx.app.data.repository.BaiduResolveRepository
import com.yunx.app.data.repository.C139AccountRepository
import com.yunx.app.data.repository.C139ResolveRepository
import com.yunx.app.data.repository.Pan123AccountRepository
import com.yunx.app.data.repository.Pan123ResolveRepository
import com.yunx.app.data.repository.QuarkAccountRepository
import com.yunx.app.data.repository.QuarkResolveRepository
import com.yunx.app.data.repository.CowTransferResolveRepository
import com.yunx.app.data.repository.Cloud189ResolveRepository
import com.yunx.app.data.repository.LanzouResolveRepository
import com.yunx.app.data.repository.CtfileResolveRepository
import com.yunx.app.data.repository.WenshushuResolveRepository
import com.yunx.app.data.repository.WsDiskResolveRepository
import com.yunx.app.data.network.WsDiskApi
import com.yunx.app.data.repository.SimpleAccountRepository
import com.yunx.app.data.repository.UCAccountRepository
import com.yunx.app.data.repository.UCResolveRepository
import com.yunx.app.data.repository.XunleiAccountRepository
import com.yunx.app.data.repository.XunleiResolveRepository
import com.yunx.app.ui.login.BaiduLoginScreen
import com.yunx.app.ui.login.C139LoginScreen
import com.yunx.app.ui.login.GenericLoginConfigs
import com.yunx.app.ui.login.GenericWebViewLoginScreen
import com.yunx.app.ui.login.Pan123LoginScreen
import com.yunx.app.ui.login.QuarkLoginScreen
import com.yunx.app.ui.login.UCLoginScreen
import com.yunx.app.ui.login.XunleiLoginScreen
import com.yunx.app.ui.login.XunleiVerifyWebViewScreen
import com.yunx.app.ui.navigation.MainTab
import com.yunx.app.ui.theme.GlassCapsuleNav
import com.yunx.app.ui.theme.WallpaperBackground
import com.yunx.app.ui.screens.AboutScreen
import com.yunx.app.ui.screens.BookmarkScreen
import com.yunx.app.ui.screens.DownloadScreen
import com.yunx.app.ui.screens.DriveScreen
import com.yunx.app.ui.screens.OnboardingScreen
import com.yunx.app.ui.screens.ResolveScreen
import com.yunx.app.ui.screens.SettingsScreen
import com.yunx.app.ui.screens.SupportScreen
import com.yunx.app.ui.screens.FeedbackScreen
import com.yunx.app.ui.screens.ThemeScreen
import com.yunx.app.ui.screens.StartupDialogKind
import com.yunx.app.ui.screens.StartupDialogQueue
import com.yunx.app.ui.screens.UpdateDialog
import com.yunx.app.ui.screens.WelcomeDialog
import com.yunx.app.ui.viewmodel.BaiduAccountViewModel
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel
import com.yunx.app.ui.viewmodel.BookmarkViewModel
import com.yunx.app.ui.viewmodel.C139AccountViewModel
import com.yunx.app.ui.viewmodel.C139CloudViewModel
import com.yunx.app.ui.viewmodel.DownloadViewModel
import com.yunx.app.ui.viewmodel.DriveQuotaViewModel
import com.yunx.app.ui.viewmodel.Pan123AccountViewModel
import com.yunx.app.ui.viewmodel.Pan123CloudViewModel
import com.yunx.app.ui.viewmodel.QuarkAccountViewModel
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel
import com.yunx.app.ui.viewmodel.ResolveViewModel
import com.yunx.app.ui.viewmodel.UCCoudViewModel
import com.yunx.app.ui.viewmodel.SimpleAccountViewModel
import com.yunx.app.ui.viewmodel.UCAccountViewModel
import com.yunx.app.ui.viewmodel.XunleiAccountViewModel
import com.yunx.app.ui.viewmodel.XunleiCloudViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yunx.app.data.network.HttpClients

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Resolve) }
    var showQuarkLogin by rememberSaveable { mutableStateOf(false) }
    var showLanzouLogin by rememberSaveable { mutableStateOf(false) }
    var showCloud189Login by rememberSaveable { mutableStateOf(false) }
    var showCowLogin by rememberSaveable { mutableStateOf(false) }
    var showFeijiLogin by rememberSaveable { mutableStateOf(false) }
    var showCtfileLogin by rememberSaveable { mutableStateOf(false) }
    var showWssLogin by rememberSaveable { mutableStateOf(false) }
    var showUCLogin by rememberSaveable { mutableStateOf(false) }
    var showXunleiLogin by rememberSaveable { mutableStateOf(false) }
    var showXunleiVerify by rememberSaveable { mutableStateOf(false) }
    var xunleiVerifyUrl by rememberSaveable { mutableStateOf("") }
    var xunleiVerifyDeviceId by rememberSaveable { mutableStateOf("") }
    var showBaiduLogin by rememberSaveable { mutableStateOf(false) }
    var showC139Login by rememberSaveable { mutableStateOf(false) }
    var showPan123Login by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showSupport by rememberSaveable { mutableStateOf(false) }
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    var showTheme by rememberSaveable { mutableStateOf(false) }
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var showUpdateDialog by remember { mutableStateOf(false) }
    var pendingRelease by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    LaunchedEffect(Unit) {
        while (StartupDialogQueue.isBusy) {
            kotlinx.coroutines.delay(300)
        }
        val release = UpdateChecker.fetchLatestRelease(context) ?: return@LaunchedEffect
        val current = UpdateChecker.currentVersion(context)
        val ignored = UpdateChecker.getIgnoredVersion(context).orEmpty()
        if (UpdateChecker.compareVersions(release.version, current) > 0 &&
            release.version != ignored
        ) {
            pendingRelease = release
            showUpdateDialog = true
        }
    }
    val api = remember { QuarkApi() }
    val ucApi = remember { UCApi() }
    val xunleiApi = remember { XunleiApi() }
    val baiduApi = remember { BaiduApi() }
    val c139Api = remember { C139Api() }
    val pan123Api = remember { Pan123Api() }
    val db = remember { AppDatabase.get(context) }
    val settings = remember { SettingsRepository(context) }
    val repository = remember {
        QuarkAccountRepository(db.quarkAccountDao(), api)
    }
    val ucRepository = remember {
        UCAccountRepository(db.ucAccountDao(), ucApi)
    }
    val xunleiRepository = remember {
        XunleiAccountRepository(db.xunleiAccountDao(), xunleiApi)
    }
    val baiduRepository = remember {
        BaiduAccountRepository(db.baiduAccountDao(), baiduApi)
    }
    val c139Repository = remember {
        C139AccountRepository(db.c139AccountDao())
    }
    val pan123Repository = remember {
        Pan123AccountRepository(db.pan123AccountDao(), pan123Api)
    }
    val simpleRepository = remember {
        SimpleAccountRepository(db.simpleAccountDao())
    }
    val lanzouResolveRepository = remember { LanzouResolveRepository(
        cookieProvider = { simpleRepository.getAccount(com.yunx.app.data.repository.SimpleNetdisk.LANZOU)?.cookie }
    ) }
    val cloud189ResolveRepository = remember { Cloud189ResolveRepository(
        cookieProvider = { simpleRepository.getAccount(com.yunx.app.data.repository.SimpleNetdisk.CLOUD189)?.cookie }
    ) }
    val cowTransferResolveRepository = remember { CowTransferResolveRepository() }
    val feijiResolveRepository = remember { WsDiskResolveRepository(WsDiskApi.FEIJI) }
    val ilanzouResolveRepository = remember { WsDiskResolveRepository(WsDiskApi.ILANZOU) }
    val ctfileResolveRepository = remember { CtfileResolveRepository() }
    val wenshushuResolveRepository = remember { WenshushuResolveRepository() }
    val backupManager = remember {
        AuthBackupManager(
            db.quarkAccountDao(),
            db.ucAccountDao(),
            db.xunleiAccountDao(),
            db.baiduAccountDao(),
            db.c139AccountDao(),
            db.pan123AccountDao()
        )
    }
    val downloadManager = remember {
        DownloadManager(
            context = context,
            dao = db.downloadTaskDao(),
            downloader = ChunkDownloader({ HttpClients.downloadClient() }),
            threadProvider = { platform -> settings.downloadThreadsFor(platform) },
            saveDirProvider = { settings.downloadDirUri },
            concurrencyProvider = { settings.maxConcurrentDownloads },
            speedLimitProvider = { settings.downloadSpeedLimit },
            retryCountProvider = { settings.downloadRetryCount },
            keepWhenLockedProvider = { settings.keepDownloadWhenLocked },
            showSpeedProvider = { settings.notificationShowSpeed }
        )
    }
    var pendingStoragePermission by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingStoragePermission?.complete(granted)
        pendingStoragePermission = null
    }
    downloadManager.storagePermissionProvider = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            val deferred = CompletableDeferred<Boolean>()
            pendingStoragePermission = deferred
            withContext(Dispatchers.Main) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            deferred.await()
        }
    }
    val viewModel: QuarkAccountViewModel = viewModel(
        factory = QuarkAccountViewModel.Factory(repository)
    )
    val ucViewModel: UCAccountViewModel = viewModel(
        factory = UCAccountViewModel.Factory(ucRepository)
    )
    val xunleiViewModel: XunleiAccountViewModel = viewModel(
        factory = XunleiAccountViewModel.Factory(xunleiRepository)
    )
    val baiduViewModel: BaiduAccountViewModel = viewModel(
        factory = BaiduAccountViewModel.Factory(baiduRepository)
    )
    val c139ViewModel: C139AccountViewModel = viewModel(
        factory = C139AccountViewModel.Factory(c139Repository)
    )
    val pan123ViewModel: Pan123AccountViewModel = viewModel(
        factory = Pan123AccountViewModel.Factory(pan123Repository)
    )
    val quarkLoginState = remember { repository.observeAccount().map { it != null } }
    val ucLoginState = remember { ucRepository.observeAccount().map { it != null } }
    val xunleiLoginState = remember { xunleiRepository.observeAccount().map { it != null } }
    val baiduLoginState = remember { baiduRepository.observeAccount().map { it != null } }
    val c139LoginState = remember { c139Repository.observeAccount().map { it != null } }
    val pan123LoginState = remember { pan123Repository.observeAccount().map { it != null } }
    val quarkCloudViewModel: QuarkCloudViewModel = viewModel(
        factory = QuarkCloudViewModel.Factory(
            api,
            { repository.getFreshCookie() },
            downloadManager,
            loginState = quarkLoginState
        )
    )
    val ucCloudViewModel: UCCoudViewModel = viewModel(
        factory = UCCoudViewModel.Factory(
            ucApi,
            { ucRepository.getFreshCookie() },
            downloadManager,
            loginState = ucLoginState
        )
    )
    xunleiApi.refreshTokenProvider = { deviceId ->
        val acc = xunleiRepository.getAccount()
        if (acc == null || acc.refreshToken.isBlank()) null
        else xunleiApi.refreshToken(acc.refreshToken, deviceId)?.also { (at, nrt) ->
            xunleiRepository.updateTokens(at, nrt)
        }
    }
    val xunleiCloudViewModel: XunleiCloudViewModel = viewModel(
        factory = XunleiCloudViewModel.Factory(
            xunleiApi,
            { xunleiRepository.getAccount()?.accessToken },
            { xunleiRepository.getAccount()?.deviceId },
            { xunleiRepository.getAccount()?.captchaToken },
            downloadManager,
            loginState = xunleiLoginState
        )
    )
    val baiduCloudViewModel: BaiduCloudViewModel = viewModel(
        factory = BaiduCloudViewModel.Factory(
            baiduApi,
            { baiduRepository.getAccount()?.cookie },
            downloadManager,
            loginState = baiduLoginState
        )
    )
    val c139CloudViewModel: C139CloudViewModel = viewModel(
        factory = C139CloudViewModel.Factory(
            c139Api,
            { c139Repository.getAccount()?.cookie },
            downloadManager,
            loginState = c139LoginState
        )
    )
    val pan123CloudViewModel: Pan123CloudViewModel = viewModel(
        factory = Pan123CloudViewModel.Factory(
            pan123Api,
            { pan123Repository.getAccount()?.accessToken },
            downloadManager,
            loginState = pan123LoginState
        )
    )
    val driveQuotaViewModel: DriveQuotaViewModel = viewModel(
        factory = DriveQuotaViewModel.Factory(
            api, { repository.getAccount()?.cookie },
            ucApi, { ucRepository.getAccount()?.cookie },
            xunleiApi,
            { xunleiRepository.getAccount()?.accessToken },
            { xunleiRepository.getAccount()?.deviceId },
            { xunleiRepository.getAccount()?.captchaToken },
            baiduApi, { baiduRepository.getAccount()?.cookie },
            c139Api, { c139Repository.getAccount()?.cookie },
            pan123Api, { pan123Repository.getAccount()?.accessToken }
        )
    )
    val xunleiResolveRepository = remember {
        XunleiResolveRepository(
            api = xunleiApi,
            accountProvider = { xunleiRepository.getAccount()?.accessToken },
            deviceIdProvider = { xunleiRepository.getAccount()?.deviceId },
            captchaProvider = { xunleiRepository.getAccount()?.captchaToken },
            refreshProvider = {
                val acc = xunleiRepository.getAccount()
                if (acc == null || acc.refreshToken.isBlank()) null
                else xunleiApi.refreshToken(acc.refreshToken, acc.deviceId)?.also { (at, nrt) ->
                    xunleiRepository.updateTokens(at, nrt)
                }
            }
        )
    }
    val baiduResolveRepository = remember {
        BaiduResolveRepository(baiduApi)
    }
    val c139ResolveRepository = remember {
        C139ResolveRepository(c139Api)
    }
    val pan123ResolveRepository = remember {
        Pan123ResolveRepository(
            api = pan123Api,
            tokenProvider = { pan123Repository.getAccount()?.accessToken }
        )
    }
    val resolveViewModel: ResolveViewModel = viewModel(
        factory = ResolveViewModel.Factory(
            repository,
            QuarkResolveRepository(api),
            ucRepository,
            UCResolveRepository(ucApi),
            xunleiRepository,
            xunleiResolveRepository,
            baiduRepository,
            baiduResolveRepository,
            c139Repository,
            c139ResolveRepository,
            pan123Repository,
            pan123ResolveRepository,
            simpleRepository,
            lanzouResolveRepository,
            cowTransferResolveRepository,
            feijiResolveRepository,
            ilanzouResolveRepository,
            ctfileResolveRepository,
            cloud189ResolveRepository,
            wenshushuResolveRepository,
            downloadManager,
            db.bookmarkDao(),
            remember { SettingsRepository(context) }
        )
    )
    val downloadViewModel: DownloadViewModel = viewModel(
        factory = DownloadViewModel.Factory(downloadManager)
    )
    val bookmarkViewModel: BookmarkViewModel = viewModel(
        factory = BookmarkViewModel.Factory(db.bookmarkDao())
    )
    val quarkAccount by viewModel.quarkAccount.collectAsState()
    val ucAccount by ucViewModel.ucAccount.collectAsState()
    val xunleiAccount by xunleiViewModel.xunleiAccount.collectAsState()
    val baiduAccount by baiduViewModel.baiduAccount.collectAsState()
    val c139Account by c139ViewModel.c139Account.collectAsState()
    val pan123Account by pan123ViewModel.pan123Account.collectAsState()
    val simpleViewModel: SimpleAccountViewModel = viewModel(
        factory = SimpleAccountViewModel.Factory(simpleRepository)
    )
    val lanzouAccount by simpleViewModel.lanzouAccount.collectAsState()
    val cloud189Account by simpleViewModel.cloud189Account.collectAsState()
    val cowAccount by simpleViewModel.cowAccount.collectAsState()
    val feijiAccount by simpleViewModel.feijiAccount.collectAsState()
    val ctfileAccount by simpleViewModel.ctfileAccount.collectAsState()
    val wssAccount by simpleViewModel.wssAccount.collectAsState()

    var showBatteryGuide by remember { mutableStateOf(false) }
    var batteryGuideShown by remember { mutableStateOf(false) }

    LaunchedEffect(resolveViewModel.downloadStarted) {
        if (resolveViewModel.downloadStarted) {
            currentTab = MainTab.Download
            resolveViewModel.consumeDownloadStarted()
        }
    }

    LaunchedEffect(Unit) {
        downloadViewModel.tasks.collect { tasks ->
            if (!batteryGuideShown && tasks.any {
                    it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                        it.status == DownloadTaskEntity.STATUS_PENDING
                }
            ) {
                batteryGuideShown = true
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (settings.keepDownloadWhenLocked &&
                    pm?.isIgnoringBatteryOptimizations(context.packageName) != true
                ) {
                    showBatteryGuide = true
                }
            }
        }
    }

    if (showQuarkLogin) {
        QuarkLoginScreen(
            viewModel = viewModel,
            onBack = { showQuarkLogin = false },
            onSaved = { showQuarkLogin = false }
        )
        return
    }

    if (showUCLogin) {
        UCLoginScreen(
            viewModel = ucViewModel,
            onBack = { showUCLogin = false },
            onSaved = { showUCLogin = false }
        )
        return
    }

    if (showXunleiLogin) {
        XunleiLoginScreen(
            viewModel = xunleiViewModel,
            onBack = { showXunleiLogin = false },
            onSaved = { showXunleiLogin = false },
            onVerify = { url, deviceId ->
                xunleiVerifyUrl = url
                xunleiVerifyDeviceId = deviceId
                showXunleiLogin = false
                showXunleiVerify = true
            }
        )
        return
    }

    if (showXunleiVerify) {
        XunleiVerifyWebViewScreen(
            verifyUrl = xunleiVerifyUrl,
            deviceId = xunleiVerifyDeviceId,
            onResult = { success, _ ->
                showXunleiVerify = false
                showXunleiLogin = true
                if (success) {
                    SnackbarController.show("验证完成，正在自动登录…")
                    xunleiViewModel.retryLoginAfterVerify()
                } else {
                    SnackbarController.show("验证未完成，请重试")
                }
            },
            onBack = {
                showXunleiVerify = false
                showXunleiLogin = true
            }
        )
        return
    }

    if (showBaiduLogin) {
        BaiduLoginScreen(
            viewModel = baiduViewModel,
            onBack = { showBaiduLogin = false },
            onSaved = { showBaiduLogin = false }
        )
        return
    }

    if (showC139Login) {
        C139LoginScreen(
            viewModel = c139ViewModel,
            onBack = { showC139Login = false },
            onSaved = { showC139Login = false }
        )
        return
    }

    if (showPan123Login) {
        Pan123LoginScreen(
            viewModel = pan123ViewModel,
            onBack = { showPan123Login = false },
            onSaved = { showPan123Login = false }
        )
        return
    }

    if (showLanzouLogin) {
        GenericWebViewLoginScreen(
            config = GenericLoginConfigs.lanzou,
            repository = simpleRepository,
            onBack = { showLanzouLogin = false },
            onSaved = { showLanzouLogin = false }
        )
        return
    }

    if (showCloud189Login) {
        GenericWebViewLoginScreen(
            config = GenericLoginConfigs.cloud189,
            repository = simpleRepository,
            onBack = { showCloud189Login = false },
            onSaved = { showCloud189Login = false }
        )
        return
    }

    if (showCowLogin) {
        GenericWebViewLoginScreen(
            config = GenericLoginConfigs.cowTransfer,
            repository = simpleRepository,
            onBack = { showCowLogin = false },
            onSaved = { showCowLogin = false }
        )
        return
    }

    if (showFeijiLogin) {
        GenericWebViewLoginScreen(
            config = GenericLoginConfigs.feiji,
            repository = simpleRepository,
            onBack = { showFeijiLogin = false },
            onSaved = { showFeijiLogin = false }
        )
        return
    }

    if (showCtfileLogin) {
        GenericWebViewLoginScreen(
            config = GenericLoginConfigs.ctfile,
            repository = simpleRepository,
            onBack = { showCtfileLogin = false },
            onSaved = { showCtfileLogin = false }
        )
        return
    }

    if (showWssLogin) {
        GenericWebViewLoginScreen(
            config = GenericLoginConfigs.wenshushu,
            repository = simpleRepository,
            onBack = { showWssLogin = false },
            onSaved = { showWssLogin = false }
        )
        return
    }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    val snackbarHostState = rememberGlobalSnackbarHostState()

    Box(modifier = Modifier.fillMaxSize()) {
    val topBarContent: @Composable () -> Unit = {
        LargeTopAppBar(
            title = {
                Text(
                    text = currentTab.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            actions = {
                if (currentTab == MainTab.Resolve) {
                    IconButton(onClick = { showBookmarks = true }) {
                        Icon(Icons.Outlined.Bookmarks, contentDescription = "收藏网盘链接")
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
    val tabContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                if (forward) {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 4 })
                } else {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 4 })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 4 })
                }
            },
            label = "mainTab"
        ) { tab ->
            saveableStateHolder.SaveableStateProvider(tab) {
                when (tab) {
                    MainTab.Resolve -> ResolveScreen(
                        scrollBehavior,
                        resolveViewModel,
                        quarkCloudViewModel,
                        xunleiCloudViewModel,
                        baiduCloudViewModel,
                        c139CloudViewModel,
                        ucCloudViewModel,
                        pan123CloudViewModel
                    )
                    MainTab.Drive -> DriveScreen(
                        scrollBehavior = scrollBehavior,
                        quarkAccount = quarkAccount,
                        ucAccount = ucAccount,
                        xunleiAccount = xunleiAccount,
                        baiduAccount = baiduAccount,
                        c139Account = c139Account,
                        pan123Account = pan123Account,
                        lanzouAccount = lanzouAccount,
                        cloud189Account = cloud189Account,
                        cowAccount = cowAccount,
                        feijiAccount = feijiAccount,
                        ctfileAccount = ctfileAccount,
                        wssAccount = wssAccount,
                        simpleViewModel = simpleViewModel,
                        quarkCloudViewModel = quarkCloudViewModel,
                        ucCloudViewModel = ucCloudViewModel,
                        xunleiCloudViewModel = xunleiCloudViewModel,
                        baiduCloudViewModel = baiduCloudViewModel,
                        c139CloudViewModel = c139CloudViewModel,
                        pan123CloudViewModel = pan123CloudViewModel,
                        driveQuotaViewModel = driveQuotaViewModel,
                        onQuarkLogin = { showQuarkLogin = true },
                        onQuarkLogout = { viewModel.logout() },
                        onDownloadStarted = { currentTab = MainTab.Download },
                        onUCLogin = { showUCLogin = true },
                        onUCLogout = { ucViewModel.logout() },
                        onXunleiLogin = { showXunleiLogin = true },
                        onXunleiLogout = { xunleiViewModel.logout() },
                        onBaiduLogin = { showBaiduLogin = true },
                        onBaiduLogout = { baiduViewModel.logout() },
                        onC139Login = { showC139Login = true },
                        onC139Logout = { c139ViewModel.logout() },
                        onPan123Login = { showPan123Login = true },
                        onPan123Logout = { pan123ViewModel.logout() },
                        onLanzouLogin = { showLanzouLogin = true },
                        onLanzouLogout = { simpleViewModel.logout(com.yunx.app.data.repository.SimpleNetdisk.LANZOU) },
                        onCloud189Login = { showCloud189Login = true },
                        onCloud189Logout = { simpleViewModel.logout(com.yunx.app.data.repository.SimpleNetdisk.CLOUD189) },
                        onCowLogin = { showCowLogin = true },
                        onCowLogout = { simpleViewModel.logout(com.yunx.app.data.repository.SimpleNetdisk.COWTRANSFER) },
                        onFeijiLogin = { showFeijiLogin = true },
                        onFeijiLogout = { simpleViewModel.logout(com.yunx.app.data.repository.SimpleNetdisk.FEIJI) },
                        onCtfileLogin = { showCtfileLogin = true },
                        onCtfileLogout = { simpleViewModel.logout(com.yunx.app.data.repository.SimpleNetdisk.CTFILE) },
                        onWssLogin = { showWssLogin = true },
                        onWssLogout = { simpleViewModel.logout(com.yunx.app.data.repository.SimpleNetdisk.WENSHUSHU) },
                        onGoResolve = { currentTab = MainTab.Resolve }
                    )
                    MainTab.Download -> DownloadScreen(scrollBehavior, downloadViewModel)
                    MainTab.Settings -> SettingsScreen(
                        scrollBehavior = scrollBehavior,
                        onThemeClick = { showTheme = true },
                        onAboutClick = { showAbout = true },
                        onSupportClick = { showSupport = true },
                        onFeedbackClick = { showFeedback = true },
                        backupManager = backupManager,
                        onDownloadUpdateApk = { url, name ->
                            scope.launch {
                                downloadManager.enqueue(url = url, fileName = name)
                                currentTab = MainTab.Download
                            }
                        }
                    )
                }
            }
        }
    }

    if (isLandscape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            WallpaperBackground()
            Row(modifier = Modifier.fillMaxSize()) {
                MainNavigationRail(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    topBarContent()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        tabContent()
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            WallpaperBackground()
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = { topBarContent() },
                bottomBar = {
                    GlassCapsuleNav(
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    tabContent()
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showAbout,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        AboutScreen(
            onBack = { showAbout = false },
            onPreviewOnboarding = {
                showAbout = false
                StartupDialogQueue.enqueue(StartupDialogKind.WELCOME)
            }
        )
    }

    AnimatedVisibility(
        visible = showSupport,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        SupportScreen(
            onBack = { showSupport = false }
        )
    }

    AnimatedVisibility(
        visible = showFeedback,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        FeedbackScreen(
            onBack = { showFeedback = false }
        )
    }

    AnimatedVisibility(
        visible = showTheme,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        ThemeScreen(
            onBack = { showTheme = false }
        )
    }

    AnimatedVisibility(
        visible = showBookmarks,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f),
        exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f),
        modifier = Modifier.fillMaxSize()
    ) {
        BookmarkScreen(
            viewModel = bookmarkViewModel,
            onBack = { showBookmarks = false },
            onResolve = { link, pwd ->
                showBookmarks = false
                currentTab = MainTab.Resolve
                resolveViewModel.startResolve(link, pwd)
            }
        )
    }
    }

    if (showBatteryGuide) {
        AlertDialog(
            onDismissRequest = { showBatteryGuide = false },
            title = { Text("保持后台下载") },
            text = {
                Text(
                    text = "「锁屏后保持下载」已开启，但应用尚未加入「忽略电池优化」白名单，息屏后可能被系统中断下载。是否前往系统设置？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryGuide = false
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
                TextButton(onClick = { showBatteryGuide = false }) { Text("暂不") }
            }
        )
    }

    pendingRelease?.let { release ->
        if (showUpdateDialog) {
            UpdateDialog(
                currentVersion = UpdateChecker.currentVersion(context),
                release = release,
                onDownload = {
                    showUpdateDialog = false
                    if (release.downloadUrl.isNotBlank()) {
                        scope.launch {
                            SnackbarController.show("正在获取下载地址…")
                            val url = UpdateChecker.resolveDownloadUrl(release)
                            if (url.isBlank()) {
                                SnackbarController.show("未找到 APK 下载链接")
                                return@launch
                            }
                            downloadManager.enqueue(url = url, fileName = "吸析At_${release.version}.apk")
                            currentTab = MainTab.Download
                        }
                        SnackbarController.show("已加入下载，完成后点击「打开」即可安装")
                    } else {
                        SnackbarController.show("未找到 APK 下载链接")
                    }
                },
                onLater = { showUpdateDialog = false },
                onIgnore = {
                    UpdateChecker.setIgnoredVersion(context, release.version)
                    showUpdateDialog = false
                }
            )
        }
    }
}

@Composable
private fun MainBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    NavigationBar {
        MainTab.values().forEach { tab ->
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.title
                    )
                },
                label = { Text(tab.title) }
            )
        }
    }
}

@Composable
private fun MainNavigationRail(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    NavigationRail {
        MainTab.values().forEach { tab ->
            NavigationRailItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.title
                    )
                },
                label = { Text(tab.title) },
                alwaysShowLabel = currentTab == tab
            )
        }
    }
}
