package com.yunx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "c139_account")
data class C139AccountEntity(
    @PrimaryKey
    val id: String = "c139",
    val cookie: String = "",
    val nickname: String = "",
    val authorization: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
