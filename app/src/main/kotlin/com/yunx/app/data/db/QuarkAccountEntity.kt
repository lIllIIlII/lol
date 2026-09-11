package com.yunx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quark_account")
data class QuarkAccountEntity(
    @PrimaryKey
    val id: String = "quark",
    val cookie: String = "",
    val nickname: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
