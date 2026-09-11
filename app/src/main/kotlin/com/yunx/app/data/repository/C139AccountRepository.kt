package com.yunx.app.data.repository

import android.webkit.CookieManager
import com.yunx.app.data.db.C139AccountDao
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.network.C139Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class C139AccountRepository(
    private val dao: C139AccountDao
) {

    fun observeAccount(): Flow<C139AccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): C139AccountEntity? = dao.getAccount()

    suspend fun logoutC139() {
        withContext(Dispatchers.IO) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        dao.clear()
    }

    suspend fun saveC139Account(cookie: String): Boolean {
        if (!C139Constants.isValidCookie(cookie)) return false
        val nickname = C139Constants.extractAccount(cookie) ?: "139用户"
        val authorization = C139Constants.extractAuthorization(cookie).orEmpty()
        dao.upsert(
            C139AccountEntity(
                id = "c139",
                cookie = cookie,
                nickname = nickname,
                authorization = authorization
            )
        )
        return true
    }
}
