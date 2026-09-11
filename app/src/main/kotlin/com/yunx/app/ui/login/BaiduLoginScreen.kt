package com.yunx.app.ui.login

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.yunx.app.data.network.BaiduConstants
import com.yunx.app.ui.viewmodel.BaiduAccountViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaiduLoginScreen(
    viewModel: BaiduAccountViewModel,
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
    var showRiskDialog by remember { mutableStateOf(true) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
            setInitialScale(0)
            settings.userAgentString = BaiduConstants.UA_WEB
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    view?.evaluateJavascript(
                        "(function(){var m=document.querySelector('meta[name=\"viewport\"]');" +
                            "var c='width=device-width,initial-scale=1.0,maximum-scale=5.0,user-scalable=yes';" +
                            "if(m){m.setAttribute('content',c);}else{var n=document.createElement('meta');n.name='viewport';n.content=c;document.head.appendChild(n);}" +
                            "window.dispatchEvent(new Event('resize'));})()",
                        null
                    )
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(BaiduConstants.LOGIN_URL)
        }
    }

    rememberWebLoginAutoDetect(
        sampleCredential = { CookieManager.getInstance().getCookie(BaiduConstants.COOKIE_DOMAIN).orEmpty() },
        isPlausible = { BaiduConstants.isValidCookie(it) },
        validateAndSave = { viewModel.saveBaiduAccount(it) },
        isPaused = { isSaving || isSavingManual || showCookieDialog },
        onInFlightChange = { isSaving = it },
        onAutoSaved = onSaved
    )

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    BackHandler(enabled = !isSaving && !isSavingManual) { onBack() }

    val snackbarHostState = rememberGlobalSnackbarHostState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("百度网盘登录", style = MaterialTheme.typography.titleLarge) },
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
                                val cookie = CookieManager.getInstance()
                                    .getCookie(BaiduConstants.COOKIE_DOMAIN)
                                    .orEmpty()
                                val saved = viewModel.saveBaiduAccount(cookie)
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

    if (showRiskDialog) {
        AlertDialog(
            onDismissRequest = { showRiskDialog = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text("温馨提示") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "百度网盘风控严重，可能导致你的账号被风控",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "请勿高频操作（频繁解析/转存/下载），如遇异常建议降低使用频率或稍后再试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRiskDialog = false
                    showTutorial = true
                }) { Text("我知道了") }
            }
        )
    }

    if (showTutorial) {
        AlertDialog(
            onDismissRequest = { showTutorial = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text("登录教程") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "1. 在下方网页中登录百度账号",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "2. 登录完成后将自动检测登录；若未自动登录，点右上角「保存」",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "3. 或点击「粘贴」图标，手动输入 Cookie（需含 BDUSS=）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "4. Cookie 长期有效，失效后需重新登录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTutorial = false }) { Text("知道了") }
            }
        )
    }

    if (showCookieDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingManual) showCookieDialog = false },
            title = { Text("手动输入 Cookie") },
            text = {
                Column {
                    Text(
                        text = "从网页登录态复制完整的 Cookie（需包含 BDUSS=）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("粘贴 Cookie…") },
                        minLines = 4,
                        maxLines = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isSavingManual = true
                            val saved = viewModel.saveBaiduAccount(cookieInput.trim())
                            isSavingManual = false
                            if (saved) {
                                SnackbarController.show("登录成功")
                                showCookieDialog = false
                                onSaved()
                            } else {
                                SnackbarController.show("Cookie 无效，请检查是否包含 BDUSS=")
                            }
                        }
                    },
                    enabled = cookieInput.isNotBlank() && !isSavingManual
                ) {
                    if (isSavingManual) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("保存")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isSavingManual) showCookieDialog = false },
                    enabled = !isSavingManual
                ) { Text("取消") }
            }
        )
    }
}
