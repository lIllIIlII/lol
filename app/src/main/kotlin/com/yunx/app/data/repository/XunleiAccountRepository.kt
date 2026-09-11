package com.yunx.app.data.repository

import com.yunx.app.data.db.XunleiAccountDao
import com.yunx.app.data.db.XunleiAccountEntity
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.XunleiLoginStep
import kotlinx.coroutines.flow.Flow

class XunleiAccountRepository(
    private val dao: XunleiAccountDao,
    private val api: XunleiApi
) {

    fun observeAccount(): Flow<XunleiAccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): XunleiAccountEntity? = dao.getAccount()

    suspend fun loginWithPassword(
        username: String,
        password: String
    ): XunleiLoginStep {
        val deviceId = XunleiApi.newDeviceId()
        return api.loginWithPassword(username, password, deviceId)
    }

    suspend fun sendSms(mobile: String): XunleiLoginStep {
        val deviceId = XunleiApi.newDeviceId()
        return api.sendSms(mobile, deviceId)
    }

    suspend fun loginWithSms(
        mobile: String,
        smsCode: String,
        creditKey: String,
        smsToken: String
    ): Boolean {
        val deviceId = XunleiApi.newDeviceId()
        val step = api.smsLogin(mobile, smsCode, creditKey, smsToken, deviceId)
        if (step.sessionId.isBlank()) return false
        val captchaToken = api.initCaptcha(deviceId, mobile) ?: ""
        val tokens = api.exchangeToken(step.sessionId, deviceId, captchaToken) ?: return false
        dao.upsert(
            XunleiAccountEntity(
                id = "xunlei",
                accessToken = tokens.first,
                refreshToken = tokens.second,
                deviceId = deviceId,
                captchaToken = captchaToken,
                nickname = step.nickname.ifBlank { "迅雷用户" }
            )
        )
        return true
    }

    suspend fun finishLogin(
        step: XunleiLoginStep,
        username: String
    ): Boolean {
        if (step.sessionId.isBlank()) return false
        val deviceId = XunleiApi.newDeviceId()
        val captchaToken = api.initCaptcha(deviceId, username) ?: ""
        val tokens = api.exchangeToken(step.sessionId, deviceId, captchaToken) ?: return false
        dao.upsert(
            XunleiAccountEntity(
                id = "xunlei",
                accessToken = tokens.first,
                refreshToken = tokens.second,
                deviceId = deviceId,
                captchaToken = captchaToken,
                nickname = step.nickname.ifBlank { "迅雷用户" }
            )
        )
        return true
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val acc = dao.getAccount() ?: return
        dao.upsert(acc.copy(accessToken = accessToken, refreshToken = refreshToken))
    }

    suspend fun logout() {
        dao.clear()
    }
}
