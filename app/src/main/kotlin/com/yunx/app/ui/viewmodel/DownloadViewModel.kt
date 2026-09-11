package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadStats
import com.yunx.app.ui.SnackbarController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(private val manager: DownloadManager) : ViewModel() {

    val tasks: StateFlow<List<DownloadTaskEntity>> = manager.tasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val stats: StateFlow<Map<Long, DownloadStats>> = manager.stats

    fun enqueue(
        url: String,
        fileName: String,
        headers: Map<String, String> = emptyMap(),
        platform: String = ""
    ) {
        viewModelScope.launch { manager.enqueue(url, fileName, headers, platform = platform) }
    }

    fun pause(id: Long) = manager.pause(id)

    fun resume(id: Long) = manager.start(id)

    fun remove(id: Long, deleteLocal: Boolean = false) = manager.remove(id, deleteLocal)

    fun redownload(task: DownloadTaskEntity) {
        viewModelScope.launch {
            val ok = manager.redownload(task.id)
            SnackbarController.show(if (ok) "已重新加入下载" else "直链已过期，请重新获取下载链接")
        }
    }

    fun pauseAll() {
        tasks.value.filter {
            it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                it.status == DownloadTaskEntity.STATUS_PENDING
        }.forEach { manager.pause(it.id) }
    }

    fun resumeAll() {
        tasks.value.filter {
            it.status == DownloadTaskEntity.STATUS_PAUSED ||
                it.status == DownloadTaskEntity.STATUS_FAILED
        }.forEach { manager.start(it.id) }
    }

    fun removeAll(deleteLocal: Boolean = false) {
        tasks.value.toList().forEach { manager.remove(it.id, deleteLocal) }
    }

    class Factory(private val manager: DownloadManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DownloadViewModel::class.java))
            return DownloadViewModel(manager) as T
        }
    }
}
