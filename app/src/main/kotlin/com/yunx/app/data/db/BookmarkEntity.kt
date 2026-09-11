package com.yunx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val link: String,
    val title: String = "",
    val platform: String = "",
    val pwd: String = "",
    val category: String = DEFAULT_CATEGORY,
    val createTime: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_CATEGORY = "未分类"

        val PRESET_CATEGORIES = listOf(
            DEFAULT_CATEGORY, "视频", "文档", "软件", "音乐", "图片", "压缩包", "其他"
        )
    }
}
