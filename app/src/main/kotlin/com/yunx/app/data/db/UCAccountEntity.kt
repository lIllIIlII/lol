package com.yunx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "uc_account")
data class UCAccountEntity(
    @PrimaryKey
    val id: String = "uc",
    val cookie: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
