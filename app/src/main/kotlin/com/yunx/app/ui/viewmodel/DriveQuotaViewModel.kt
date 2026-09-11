package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.model.QuotaInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class DriveQuotaViewModel(
    private val quarkApi: QuarkApi,
    private val quarkCookie: suspend () -> String?,
    private val ucApi: UCApi,
    private val ucCookie: suspend () -> String?,
    private val xunleiApi: XunleiApi,
    private val xunleiToken: suspend () -> String?,
    private val xunleiDeviceId: suspend () -> String?,
    private val xunleiCaptcha: suspend () -> String?,
    private val baiduApi: BaiduApi,
    private val baiduCookie: suspend () -> String?,
    private val c139Api: C139Api,
    private val c139Cookie: suspend () -> String?,
    private val pan123Api: Pan123Api,
    private val pan123Token: suspend () -> String?
) : ViewModel() {

    private val _quarkQuota = MutableStateFlow<QuotaInfo?>(null)
    val quarkQuota: StateFlow<QuotaInfo?> = _quarkQuota.asStateFlow()

    private val _ucQuota = MutableStateFlow<QuotaInfo?>(null)
    val ucQuota: StateFlow<QuotaInfo?> = _ucQuota.asStateFlow()

    private val _xunleiQuota = MutableStateFlow<QuotaInfo?>(null)
    val xunleiQuota: StateFlow<QuotaInfo?> = _xunleiQuota.asStateFlow()

    private val _baiduQuota = MutableStateFlow<QuotaInfo?>(null)
    val baiduQuota: StateFlow<QuotaInfo?> = _baiduQuota.asStateFlow()

    private val _c139Quota = MutableStateFlow<QuotaInfo?>(null)
    val c139Quota: StateFlow<QuotaInfo?> = _c139Quota.asStateFlow()

    private val _pan123Quota = MutableStateFlow<QuotaInfo?>(null)
    val pan123Quota: StateFlow<QuotaInfo?> = _pan123Quota.asStateFlow()

    val loading = MutableStateFlow(false)

    fun loadAll() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            coroutineScope {
                launch {
                    val qc = quarkCookie()
                    if (qc != null) {
                        _quarkQuota.value = runCatching { quarkApi.getQuota(qc) }.getOrNull()
                    }
                }
                launch {
                    val uc = ucCookie()
                    if (uc != null) {
                        _ucQuota.value = runCatching { ucApi.getQuota(uc) }.getOrNull()
                    }
                }
                launch {
                    val xl = xunleiToken()
                    if (xl != null) {
                        val deviceId = xunleiDeviceId() ?: ""
                        val captcha = xunleiCaptcha() ?: ""
                        _xunleiQuota.value = runCatching { xunleiApi.getQuota(xl, deviceId, captcha) }.getOrNull()
                    }
                }
                launch {
                    val bd = baiduCookie()
                    if (bd != null) {
                        _baiduQuota.value = runCatching { baiduApi.getQuota(bd) }.getOrNull()
                    }
                }
                launch {
                    val c139 = c139Cookie()
                    if (c139 != null) {
                        _c139Quota.value = runCatching { c139Api.getQuota(c139) }.getOrNull()
                    }
                }
                launch {
                    val p123 = pan123Token()
                    if (p123 != null) {
                        _pan123Quota.value = runCatching { pan123Api.getQuota(p123) }.getOrNull()
                    }
                }
            }
            loading.value = false
        }
    }

    class Factory(
        private val quarkApi: QuarkApi,
        private val quarkCookie: suspend () -> String?,
        private val ucApi: UCApi,
        private val ucCookie: suspend () -> String?,
        private val xunleiApi: XunleiApi,
        private val xunleiToken: suspend () -> String?,
        private val xunleiDeviceId: suspend () -> String?,
        private val xunleiCaptcha: suspend () -> String?,
        private val baiduApi: BaiduApi,
        private val baiduCookie: suspend () -> String?,
        private val c139Api: C139Api,
        private val c139Cookie: suspend () -> String?,
        private val pan123Api: Pan123Api,
        private val pan123Token: suspend () -> String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DriveQuotaViewModel(
                quarkApi, quarkCookie,
                ucApi, ucCookie,
                xunleiApi, xunleiToken, xunleiDeviceId, xunleiCaptcha,
                baiduApi, baiduCookie,
                c139Api, c139Cookie,
                pan123Api, pan123Token
            ) as T
    }
}
