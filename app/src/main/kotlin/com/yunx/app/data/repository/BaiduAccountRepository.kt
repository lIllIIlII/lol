package com.yunx.app.data.repository

import android.webkit.CookieManager
import com.yunx.app.data.db.BaiduAccountDao
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.BaiduConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BaiduAccountRepository(
    private val dao: BaiduAccountDao,
    private val api: BaiduApi
) {

    fun observeAccount(): Flow<BaiduAccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): BaiduAccountEntity? = dao.getAccount()

    suspend fun logoutBaidu() {
        withContext(Dispatchers.IO) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        dao.clear()
    }

    suspend fun saveBaiduAccount(cookie: String): Boolean {
        if (!BaiduConstants.isValidCookie(cookie)) return false
        val nickname = api.fetchNickname(cookie) ?: "百度用户"
        dao.upsert(
            BaiduAccountEntity(
                id = "baidu",
                cookie = cookie,
                nickname = nickname
            )
        )
        return true
    }
}
