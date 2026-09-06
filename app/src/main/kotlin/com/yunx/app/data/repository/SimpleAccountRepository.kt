/*
 * 吸析At - 简单 Cookie 型网盘账号仓库（蓝奏云 / 奶牛快传 / 小飞机网盘）。
 * 解析不强制登录；登录 Cookie 仅在存在时附带（蓝奏云可降低风控概率）。
 */

package com.yunx.app.data.repository

import com.yunx.app.data.db.SimpleAccountDao
import com.yunx.app.data.db.SimpleAccountEntity
import kotlinx.coroutines.flow.Flow

object SimpleNetdisk {
    const val LANZOU = "lanzou"
    const val COWTRANSFER = "cowtransfer"
    const val FEIJI = "feiji"
}

class SimpleAccountRepository(
    private val dao: SimpleAccountDao
) {

    fun observeAccount(platform: String): Flow<SimpleAccountEntity?> = dao.observeAccount(platform)

    fun observeAll(): Flow<List<SimpleAccountEntity>> = dao.observeAll()

    suspend fun getAccount(platform: String): SimpleAccountEntity? = dao.getAccount(platform)

    /**
     * 保存 WebView 登录 Cookie。
     * @return true 表示已落库（这三平台解析本身不依赖登录态，保存即成功）
     */
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
