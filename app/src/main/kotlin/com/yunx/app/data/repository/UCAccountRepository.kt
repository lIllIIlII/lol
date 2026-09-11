package com.yunx.app.data.repository

import android.webkit.CookieManager
import com.yunx.app.data.db.UCAccountDao
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.UCConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UCAccountRepository(
    private val dao: UCAccountDao,
    private val api: UCApi
) {

    private var lastRefreshTs = 0L

    private val sinkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        api.cookieSink = { merged ->
            sinkScope.launch {
                dao.getAccount()?.let { acc ->
                    if (acc.cookie != merged) {
                        dao.upsert(acc.copy(cookie = merged, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
        }
    }

    fun observeAccount(): Flow<UCAccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): UCAccountEntity? = dao.getAccount()

    suspend fun getFreshCookie(): String? {
        val acc = dao.getAccount() ?: return null
        val need = System.currentTimeMillis() - lastRefreshTs > UCConstants.PUUS_REFRESH_INTERVAL_MS
        if (!need) return acc.cookie
        val refreshed = api.refreshSession(acc.cookie)
        return if (refreshed != null) {
            dao.upsert(acc.copy(cookie = refreshed, updatedAt = System.currentTimeMillis()))
            lastRefreshTs = System.currentTimeMillis()
            refreshed
        } else {
            acc.cookie
        }
    }

    suspend fun logoutUC() {
        withContext(Dispatchers.IO) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        dao.clear()
    }

    suspend fun saveUCAccount(cookie: String): Boolean {
        if (!UCConstants.isValidCookie(cookie)) return false
        val nickname = api.fetchNickname(cookie) ?: "UC用户"
        dao.upsert(
            UCAccountEntity(
                id = "uc",
                cookie = cookie,
                nickname = nickname
            )
        )
        lastRefreshTs = System.currentTimeMillis()
        return true
    }
}
