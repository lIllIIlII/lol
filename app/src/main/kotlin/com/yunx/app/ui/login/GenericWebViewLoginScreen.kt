package com.yunx.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunx.app.data.repository.SimpleAccountRepository
import com.yunx.app.data.repository.SimpleNetdisk
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.viewmodel.SimpleAccountViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

data class GenericLoginConfig(
    val platform: String,
    val title: String,
    val loginUrl: String,
    val cookieUrls: List<String>,
    val cookieKeys: List<String>,
    val userAgent: String? = null,
    val localStorageProbe: String? = null,
    val tutorial: String
)

object GenericLoginConfigs {
    val lanzou = GenericLoginConfig(
        platform = SimpleNetdisk.LANZOU,
        title = "蓝奏云登录",
        loginUrl = "https://accounts.woozooo.com/accounts.php?action=login&ref=pc.woozooo.com",
        cookieUrls = listOf(
            "https://accounts.woozooo.com/",
            "https://pc.woozooo.com/",
            "https://up.woozooo.com/"
        ),
        cookieKeys = listOf("phpdisk_info"),
        tutorial = "1. 在页面中输入蓝奏云账号密码完成登录\n2. 登录成功后将自动保存登录态\n3. 登录后在网盘页点击蓝奏云卡片即可进入网盘\n\n说明：不登录也可直接解析蓝奏云分享链接，登录仅用于提升可靠性。"
    )
    val cowTransfer = GenericLoginConfig(
        platform = SimpleNetdisk.COWTRANSFER,
        title = "奶牛快传登录",
        loginUrl = "https://cowtransfer.com/login",
        cookieUrls = listOf("https://cowtransfer.com/", "https://www.cowtransfer.com/"),
        cookieKeys = listOf("session", "user", "auth", "token", "uid"),
        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        tutorial = "1. 在页面中输入手机号验证码完成登录\n2. 登录成功后会自动保存，也可点击右上角「保存」\n3. 登录后在网盘页点击奶牛快传卡片即可进入网盘\n\n说明：不登录也可直接解析奶牛快传分享链接，登录可延长分享有效期。页面加载需数秒（官网脚本较大）。"
    )
    val feiji = GenericLoginConfig(
        platform = SimpleNetdisk.FEIJI,
        title = "小飞机网盘登录",
        loginUrl = "https://www.feijipan.com/",
        cookieUrls = listOf("https://www.feijipan.com/", "https://www.feijix.com/"),
        cookieKeys = listOf("token", "session", "user", "auth"),
        userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        tutorial = "1. 打开小飞机网盘官网（feijipan.com），点击登录入口完成登录\n2. 登录成功后回到本页点击「保存」\n3. 登录后在网盘页点击小飞机卡片即可进入网盘\n\n说明：不登录也可直接解析小飞机网盘分享链接。页面为网页应用，首次加载需数秒。"
    )
    val ctfile = GenericLoginConfig(
        platform = SimpleNetdisk.CTFILE,
        title = "城通网盘登录",
        loginUrl = "https://www.ctfile.com/login",
        cookieUrls = listOf("https://www.ctfile.com/", "https://user.ctfile.com/"),
        cookieKeys = listOf("ylogin", "sid", "session"),
        tutorial = "1. 在页面中输入城通网盘账号密码完成登录\n2. 登录成功后会自动保存，也可点击右上角「保存」\n3. 登录后在网盘页点击城通网盘卡片即可进入网盘\n\n说明：不登录也可直接解析城通网盘分享链接，登录仅用于在应用内管理文件。"
    )
    val wenshushu = GenericLoginConfig(
        platform = SimpleNetdisk.WENSHUSHU,
        title = "文叔叔登录",
        loginUrl = "https://www.wenshushu.cn/login",
        cookieUrls = listOf("https://www.wenshushu.cn/", "https://wenshushu.cn/"),
        cookieKeys = listOf("token", "session", "uid", "sid"),
        localStorageProbe = "(function(){var h=[];try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);var v=localStorage.getItem(k);if(k&&v&&v.length>20&&/token|auth/i.test(k)){h.push(k+'='+v);}}}catch(e){}return h.join('; ');})()",
        tutorial = "1. 在页面中使用手机号验证码或微信扫码完成登录\n2. 登录成功后会自动保存，也可点击右上角「保存」\n3. 登录后在网盘页点击文叔叔卡片即可进入网盘\n\n说明：不登录也可直接解析文叔叔分享链接，登录仅用于在应用内管理文件。"
    )
    val cloud189 = GenericLoginConfig(
        platform = SimpleNetdisk.CLOUD189,
        title = "天翼云盘登录",
        loginUrl = "https://cloud.189.cn/api/189store/pc/login/index.htm",
        cookieUrls = listOf("https://cloud.189.cn/", "https://api.cloud.189.cn/", "https://pc.189.cn/"),
        cookieKeys = listOf("SessionID", "session", "COOKIE", "user_info", "SSON", "LT", "CUSTOMER"),
        tutorial = "1. 在页面中输入天翼云盘账号密码完成登录\n2. 登录成功后会自动保存，也可点击右上角「保存」\n3. 登录后在网盘页点击天翼云盘卡片即可进入网盘\n\n说明：不登录也可直接解析天翼云盘公开分享链接，登录可解析私密分享并提升可靠性。"
    )
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericWebViewLoginScreen(
    config: GenericLoginConfig,
    repository: SimpleAccountRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var isSavingManual by remember { mutableStateOf(false) }

    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showTutorial = true }

    val viewModel: SimpleAccountViewModel = viewModel(factory = SimpleAccountViewModel.Factory(repository))

    fun cookieValue(): String {
        val cm = CookieManager.getInstance()
        runCatching { cm.flush() }
        val map = LinkedHashMap<String, String>()
        for (url in config.cookieUrls) {
            val raw = runCatching { cm.getCookie(url) }.getOrNull() ?: continue
            for (pair in raw.split(";")) {
                val k = pair.substringBefore('=').trim()
                val v = pair.substringAfter('=').trim()
                if (k.isNotBlank() && v.isNotBlank()) map[k] = v
            }
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun isPlausibleCookie(cookie: String): Boolean =
        cookie.isNotBlank() && config.cookieKeys.any { cookie.contains("$it=", ignoreCase = true) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            config.userAgent?.let { settings.userAgentString = it }
            runCatching {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                CookieManager.getInstance().setAcceptCookie(true)
            }
            webViewClient = object : WebViewClient() {
                private var reloadedOnce = false

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    val isMain = request?.isForMainFrame ?: false
                    val reqUrl = request?.url?.toString().orEmpty()
                    if (isMain && !reloadedOnce && reqUrl.startsWith("http")) {
                        reloadedOnce = true
                        view?.reload()
                    }
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(config.loginUrl)
        }
    }

    val sampleCredentialImpl: suspend () -> String? = {
        val cookie = cookieValue()
        if (isPlausibleCookie(cookie)) {
            cookie
        } else {
            val probe = config.localStorageProbe
            if (probe == null) {
                null
            } else {
                val jsResult = suspendCancellableCoroutine { cont ->
                    runCatching {
                        webView.evaluateJavascript(probe) { r ->
                            cont.resumeWith(Result.success(r ?: "null"))
                        }
                    }.onFailure {
                        cont.resumeWith(Result.success("null"))
                    }
                }
                val value = runCatching { jsResult.trim().trim('"') }.getOrNull().orEmpty()
                value.takeIf { it.isNotBlank() && it != "null" && it.contains("=") }
            }
        }
    }

    rememberWebLoginAutoDetect(
        sampleCredential = { sampleCredentialImpl() },
        isPlausible = { isPlausibleCookie(it) },
        validateAndSave = { viewModel.saveCookie(config.platform, it) },
        isPaused = { isSaving || isSavingManual || showCookieDialog },
        onInFlightChange = { isSaving = it },
        onAutoSaved = onSaved
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching { webView.stopLoading() }
            webView.destroy()
        }
    }

    BackHandler(enabled = !isSaving && !isSavingManual) { onBack() }

    val snackbarHostState = rememberGlobalSnackbarHostState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(config.title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { if (!isSaving && !isSavingManual) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!isSaving && !isSavingManual) showCookieDialog = true },
                        enabled = !isSaving && !isSavingManual
                    ) {
                        Icon(
                            Icons.Outlined.ContentPaste,
                            contentDescription = "手动输入 Cookie",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val cookie = sampleCredentialImpl()
                                val saved = if (cookie.isNullOrBlank()) false else viewModel.saveCookie(config.platform, cookie)
                                isSaving = false
                                if (saved) {
                                    SnackbarController.show("登录成功")
                                    onSaved()
                                } else {
                                    SnackbarController.show("未检测到登录态，请先完成登录")
                                }
                            }
                        },
                        enabled = !isSaving && !isSavingManual
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("保存")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showTutorial) {
        AlertDialog(
            onDismissRequest = { showTutorial = false },
            icon = {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text(config.title) },
            text = { Text(config.tutorial) },
            confirmButton = {
                Button(onClick = { showTutorial = false }) { Text("知道了") }
            }
        )
    }

    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSavingManual) {
                    showCookieDialog = false
                    cookieInput = ""
                }
            },
            title = { Text("手动输入 Cookie") },
            text = {
                OutlinedTextField(
                    value = cookieInput,
                    onValueChange = { cookieInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    placeholder = { Text("粘贴整串 Cookie，如 ${config.cookieKeys.first()}=…") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = cookieInput.isNotBlank() && !isSavingManual,
                    onClick = {
                        scope.launch {
                            isSavingManual = true
                            val saved = viewModel.saveCookie(config.platform, cookieInput)
                            isSavingManual = false
                            if (saved) {
                                SnackbarController.show("登录成功")
                                showCookieDialog = false
                                cookieInput = ""
                                onSaved()
                            } else {
                                SnackbarController.show("Cookie 格式无效，请检查")
                            }
                        }
                    }
                ) {
                    if (isSavingManual) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("保存")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCookieDialog = false }) { Text("取消") }
            }
        )
    }
}
