package com.yunx.app.data.repository

import com.yunx.app.data.db.SimpleAccountDao
import com.yunx.app.data.db.SimpleAccountEntity
import kotlinx.coroutines.flow.Flow

object SimpleNetdisk {
    const val LANZOU = "lanzou"
    const val COWTRANSFER = "cowtransfer"
    const val FEIJI = "feiji"
    const val CTFILE = "ctfile"
    const val WENSHUSHU = "wenshushu"
}

class SimpleAccountRepository(
    private val dao: SimpleAccountDao
) {

    fun observeAccount(platform: String): Flow<SimpleAccountEntity?> = dao.observeAccount(platform)

    fun observeAll(): Flow<List<SimpleAccountEntity>> = dao.observeAll()

    suspend fun getAccount(platform: String): SimpleAccountEntity? = dao.getAccount(platform)

    suspend fun saveCookie(platform: String, cookie: String, nickname: String): Boolean {
        val c = cookie.trim()
        if (c.isBlank() || !c.contains("=")) return false
        dao.upsert(
            SimpleAccountEntity(
                platform = platform,
                cookie = c,
                nickname = nickname.trim()
            )
        )
        return true
    }

    suspend fun clear(platform: String) = dao.clear(platform)
}
