package com.yunx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pan123_account")
data class Pan123AccountEntity(
    @PrimaryKey
    val id: String = "pan123",
    val accessToken: String = "",
    val account: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
