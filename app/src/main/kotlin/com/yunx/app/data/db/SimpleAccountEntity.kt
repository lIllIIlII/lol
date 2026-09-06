/*
 * 吸析At - 简单 Cookie 型网盘账号（蓝奏云 / 奶牛快传 / 小飞机网盘）。
 * 这三个网盘解析无需登录，登录态仅用于提升可靠性（蓝奏云账号 Cookie 绕过部分风控）。
 * 一张表通用三平台：platform 为主键（"lanzou" / "cowtransfer" / "feiji"）。
 */

package com.yunx.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "simple_account")
data class SimpleAccountEntity(
    @PrimaryKey
    val platform: String,
    /** WebView 登录后提取的整串 Cookie（加密落库） */
    val cookie: String = "",
    /** 展示名（蓝奏云用户名等，拿不到时留空） */
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface SimpleAccountDao {

    @Query("SELECT * FROM simple_account")
    fun observeAll(): Flow<List<SimpleAccountEntity>>

    @Query("SELECT * FROM simple_account WHERE platform = :platform")
    fun observeAccount(platform: String): Flow<SimpleAccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: SimpleAccountEntity)

    @Query("SELECT * FROM simple_account WHERE platform = :platform")
    suspend fun getAccount(platform: String): SimpleAccountEntity?

    @Query("DELETE FROM simple_account WHERE platform = :platform")
    suspend fun clear(platform: String)
}
