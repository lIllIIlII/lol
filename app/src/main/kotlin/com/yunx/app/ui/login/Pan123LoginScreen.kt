package com.yunx.app.ui.login

import android.graphics.Bitmap
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
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.ui.viewmodel.Pan123AccountViewModel
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pan123LoginScreen(
    viewModel: Pan123AccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var isSavingManual by remember { mutableStateOf(false) }

    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showTutorial = true }

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
            settings.userAgentString = Pan123Constants.WEB_UA
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
            loadUrl(Pan123Constants.WEB_LOGIN_URL)
        }
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    BackHandler(enabled = !isSaving && !isSavingManual) { onBack() }

    rememberWebLoginAutoDetect(
        sampleCredential = { webView.readLocalStorageValue(Pan123Constants.LOCAL_STORAGE_TOKEN_KEY) },
        isPlausible = { it.isNotBlank() },
        validateAndSave = { viewModel.saveToken(it) },
        isPaused = { isSaving || isSavingManual || showTokenDialog },
        onInFlightChange = { isSaving = it },
        onAutoSaved = onSaved
    )

    val snackbarHostState = rememberGlobalSnackbarHostState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("123云盘登录", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { if (!isSaving && !isSavingManual) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!isSaving && !isSavingManual) showTokenDialog = true },
                        enabled = !isSaving && !isSavingManual
                    ) {
                        Icon(
                            Icons.Outlined.ContentPaste,
                            contentDescription = "手动输入 Token",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                val token = webView.readLocalStorageValue(Pan123Constants.LOCAL_STORAGE_TOKEN_KEY)
                                val saved = if (token.isBlank()) false else viewModel.saveToken(token)
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
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text("登录教程") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "1. 在下方网页中登录 123 云盘账号（支持账号密码 / 短信验证码）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "2. 登录完成后将自动检测并登录，无需手动操作",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "3. 若自动登录未触发，可点右上角「保存」手动提取（读取网页 localStorage 的 authorToken）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "4. 或点击「粘贴」图标，手动粘贴 Token",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "5. Token 长期有效，失效后需重新登录",
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

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingManual) showTokenDialog = false },
            title = { Text("手动输入 Token") },
            text = {
                Column {
                    Text(
                        text = "登录 123 云盘网页后，复制其 localStorage 中的 authorToken 值粘贴到这里（可用浏览器开发者工具查看）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("粘贴 authorToken…") },
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
                            val saved = viewModel.saveToken(tokenInput.trim())
                            isSavingManual = false
                            if (saved) {
                                SnackbarController.show("登录成功")
                                showTokenDialog = false
                                onSaved()
                            } else {
                                SnackbarController.show("Token 无效，请检查是否为完整的 authorToken")
                            }
                        }
                    },
                    enabled = tokenInput.isNotBlank() && !isSavingManual
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
                    onClick = { if (!isSavingManual) showTokenDialog = false },
                    enabled = !isSavingManual
                ) { Text("取消") }
            }
        )
    }
}

private suspend fun WebView.readLocalStorageValue(key: String): String =
    withTimeoutOrNull(2_000) {
        suspendCancellableCoroutine { cont ->
            try {
                evaluateJavascript(
                    "(function(){try{var v=localStorage.getItem('" + key + "');" +
                        "return v===null?'':encodeURIComponent(v)}catch(e){return ''}})()"
                ) { result ->
                    val raw = result?.trim() ?: ""
                    val value = when {
                        raw.isEmpty() || raw == "\"\"" || raw == "null" -> ""
                        raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ->
                            raw.substring(1, raw.length - 1)
                        else -> raw
                    }
                    val decoded = if (value.isBlank()) "" else
                        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault("")
                    if (cont.isActive) cont.resume(decoded)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume("")
            }
        }
    } ?: ""
