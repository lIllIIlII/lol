package com.yunx.app.data.repository

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.yunx.app.data.db.Pan123AccountDao
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.network.Pan123Api
import kotlinx.coroutines.flow.Flow

class Pan123AccountRepository(
    private val dao: Pan123AccountDao,
    private val api: Pan123Api
) {

    fun observeAccount(): Flow<Pan123AccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): Pan123AccountEntity? = dao.getAccount()

    suspend fun saveToken(token: String): Boolean {
        val t = token.trim()
        if (t.isBlank()) return false
        val nickname = api.fetchNickname(t) ?: return false
        dao.upsert(
            Pan123AccountEntity(
                id = "pan123",
                accessToken = t,
                account = "",
                nickname = nickname
            )
        )
        return true
    }

    suspend fun validate(): Boolean {
        val acc = dao.getAccount() ?: return false
        val ok = api.fetchNickname(acc.accessToken) != null
        if (!ok) dao.clear()
        return ok
    }

    suspend fun logout() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        runCatching { WebStorage.getInstance().deleteAllData() }
        dao.clear()
    }
}
