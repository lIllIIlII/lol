package com.yunx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "xunlei_account")
data class XunleiAccountEntity(
    @PrimaryKey
    val id: String = "xunlei",
    val accessToken: String = "",
    val refreshToken: String = "",
    val deviceId: String = "",
    val captchaToken: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
