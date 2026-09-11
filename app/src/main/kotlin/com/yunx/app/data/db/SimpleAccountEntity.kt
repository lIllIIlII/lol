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
    val cookie: String = "",
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
