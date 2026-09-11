package com.yunx.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.XunleiAccountEntity
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.XunleiLoginStep
import com.yunx.app.data.repository.XunleiAccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class XunleiAccountViewModel(
    private val repository: XunleiAccountRepository
) : ViewModel() {

    val xunleiAccount: StateFlow<XunleiAccountEntity?> = repository.observeAccount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    var loginStep by androidx.compose.runtime.mutableStateOf<XunleiLoginStep?>(null)
        private set

    var loginError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    var smsSent by androidx.compose.runtime.mutableStateOf(false)
        private set

    private var lastUsername = ""
    private var lastPassword = ""

    fun consumeLoginError() {
        loginError = null
    }

    fun login(username: String, password: String) {
        lastUsername = username.trim()
        lastPassword = password
        viewModelScope.launch {
            loginError = null
            loginStep = null
            smsSent = false
            val step = repository.loginWithPassword(username.trim(), password)
            if (step.needSms) {
                val reviewMap = XunleiApi.parseReviewUrl(step.reviewUrl)
                val creditKey = reviewMap["creditkey"].orEmpty()
                if (creditKey.isNotBlank()) {
                    loginStep = step.copy(
                        smsCreditKey = creditKey,
                        smsToken = reviewMap["token"].orEmpty()
                    )
                } else {
                    val smsStep = repository.sendSms(username.trim())
                    if (smsStep.smsCreditKey.isNotBlank()) {
                        smsSent = true
                        loginStep = smsStep
                    } else {
                        loginError = smsStep.message.ifBlank { "短信发送失败，请重试或检查网络" }
                        loginStep = step.copy(message = "短信发送失败")
                    }
                }
            } else if (step.sessionKey.isNotBlank() && step.sessionId.isNotBlank()) {
                val ok = repository.finishLogin(step, username.trim())
                if (!ok) loginError = "登录失败，无法换取凭证"
            } else {
                loginError = step.message.ifBlank { "登录失败，请检查账号密码" }
            }
        }
    }

    fun retryLoginAfterVerify() {
        if (lastUsername.isNotBlank() && lastPassword.isNotBlank()) {
            login(lastUsername, lastPassword)
        }
    }

    fun sendSms(mobile: String) {
        viewModelScope.launch {
            loginError = null
            val step = repository.sendSms(mobile.trim())
            if (step.smsCreditKey.isNotBlank()) smsSent = true
            loginStep = step
            if (step.smsCreditKey.isBlank()) loginError = step.message
        }
    }

    fun loginWithSms(mobile: String, code: String, creditKey: String, smsToken: String) {
        viewModelScope.launch {
            loginError = null
            val ok = repository.loginWithSms(mobile.trim(), code.trim(), creditKey, smsToken)
            if (!ok) loginError = "验证码校验失败"
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    class Factory(
        private val repository: XunleiAccountRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(XunleiAccountViewModel::class.java))
            return XunleiAccountViewModel(repository) as T
        }
    }
}
