package com.yunx.app.ui.login

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.yunx.app.data.network.XunleiConstants
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import org.json.JSONObject

private class XunleiJsBridge(
    private val onSuccess: (String) -> Unit,
    private val onClose: () -> Unit
) {
    @JavascriptInterface
    fun onVerifyResult(resultJson: String) = onSuccess(resultJson)

    @JavascriptInterface
    fun close() = onClose()
}

private fun attachVerificationBridge(webView: WebView, bridge: XunleiJsBridge) {
    webView.addJavascriptInterface(bridge, "XLJSWebViewBridge")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XunleiVerifyWebViewScreen(
    verifyUrl: String,
    deviceId: String,
    onResult: (success: Boolean, extra: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    val trustedInitialUrl = remember(verifyUrl) { XunleiVerificationPolicy.isTrustedPage(verifyUrl) }

    LaunchedEffect(verifyUrl, trustedInitialUrl) {
        if (!trustedInitialUrl) onResult(false, "untrusted_verify_url")
    }

    val bridge: XunleiJsBridge = remember(onResult) {
        XunleiJsBridge(
            onSuccess = { resultJson ->
                Handler(Looper.getMainLooper()).post { onResult(true, resultJson) }
            },
            onClose = {
                Handler(Looper.getMainLooper()).post { onResult(false, "user_closed") }
            }
        )
    }

    val webView = remember(verifyUrl) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
            settings.userAgentString = XunleiConstants.APP_UA
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (trustedInitialUrl) attachVerificationBridge(this, bridge)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                    if (!XunleiVerificationPolicy.isTrustedPage(url)) {
                        view?.stopLoading()
                        view?.removeJavascriptInterface("XLJSWebViewBridge")
                        onResult(false, "untrusted_verify_navigation")
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    if (XunleiVerificationPolicy.isTrustedPage(url)) {
                        view?.evaluateJavascript(buildInitScript(deviceId), null)
                    } else {
                        view?.removeJavascriptInterface("XLJSWebViewBridge")
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (XunleiVerificationPolicy.isTrustedCallback(url)) {
                        onResult(true, url.orEmpty())
                        return true
                    }
                    return !XunleiVerificationPolicy.isTrustedPage(url)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString()
                    if (XunleiVerificationPolicy.isTrustedCallback(url)) {
                        onResult(true, url.orEmpty())
                        return true
                    }
                    return !XunleiVerificationPolicy.isTrustedPage(url)
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(if (trustedInitialUrl) withDeviceId(verifyUrl, deviceId) else "about:blank")
        }
    }

    DisposableEffect(Unit) { onDispose { webView.destroy() } }
    BackHandler { onBack() }
    val snackbarHostState = rememberGlobalSnackbarHostState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("迅雷安全验证", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}

private fun withDeviceId(url: String, deviceId: String): String {
    if (deviceId.isBlank()) return url
    val sep = if (url.contains('?')) '&' else '?'
    return "$url${sep}deviceid=${Uri.encode(deviceId)}"
}

private fun buildInitScript(deviceId: String): String {
    val pkg = "ANDROID-com.xunlei.downloadprovider"
    val cv = XunleiConstants.APP_CLIENT_VERSION
    val pkgJs = JSONObject.quote(pkg)
    val clientVersionJs = JSONObject.quote(cv)
    val deviceIdJs = JSONObject.quote(deviceId)
    return """
        (function(){
          try {
            window.env = 'android';
            window.appid = '40';
            window.appName = $pkgJs;
            window.clientVersion = $clientVersionJs;
            window.deviceid = $deviceIdJs;
            window.platformVersion = '10';
            window.event = 'login3';
          } catch(e) {}
          function fire(){
            if (window.__xunleiInited) return true;
            if (window.XlCaptcha && typeof window.XlCaptcha.init === 'function') {
              try {
                // 防御：补齐缺失的原生方法（包里未定义 isMobileSDK）
                if (typeof window.XlCaptcha.isMobileSDK !== 'function') {
                  window.XlCaptcha.isMobileSDK = function(){ return false; };
                }
                window.XlCaptcha.init({
                  appid: '40',
                  appName: $pkgJs,
                  clientVersion: $clientVersionJs,
                  deviceid: $deviceIdJs,
                  event: 'login3',
                  platformVersion: '10',
                  IFRAME_BOX_ID: 'captch-wrap',
                  VERTIFYSUCCFUNC: function(res){
                    try { window.XLJSWebViewBridge.onVerifyResult(JSON.stringify(res || {})); } catch(e){}
                  }
                });
                window.__xunleiInited = true;
                return true;
              } catch(e) { console.error('XlCaptcha.init failed', e); }
            }
            return false;
          }
          if (!fire()) {
            var t = setInterval(function(){ if (fire()) { clearInterval(t); } }, 120);
            setTimeout(function(){ clearInterval(t); }, 10000);
          }
        })();
    """.trimIndent()
}
