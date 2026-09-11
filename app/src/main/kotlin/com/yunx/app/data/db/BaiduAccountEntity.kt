package com.yunx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "baidu_account")
data class BaiduAccountEntity(
    @PrimaryKey
    val id: String = "baidu",
    val cookie: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
