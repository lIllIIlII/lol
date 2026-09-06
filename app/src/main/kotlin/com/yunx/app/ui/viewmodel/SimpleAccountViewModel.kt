/*
 * 吸析At - 简单 Cookie 型网盘账号 ViewModel（蓝奏云/奶牛快传/小飞机）。
 */

package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.SimpleAccountEntity
import com.yunx.app.data.repository.SimpleAccountRepository
import com.yunx.app.data.repository.SimpleNetdisk
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SimpleAccountViewModel(
    private val repository: SimpleAccountRepository
) : ViewModel() {

    val lanzouAccount: StateFlow<SimpleAccountEntity?> = observe(SimpleNetdisk.LANZOU)
    val cowAccount: StateFlow<SimpleAccountEntity?> = observe(SimpleNetdisk.COWTRANSFER)
    val feijiAccount: StateFlow<SimpleAccountEntity?> = observe(SimpleNetdisk.FEIJI)

    private fun observe(platform: String): StateFlow<SimpleAccountEntity?> =
        repository.observeAccount(platform)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )

    /** 保存 Cookie（非空且含 key=value 即成功） */
    suspend fun saveCookie(platform: String, cookie: String): Boolean =
        repository.saveCookie(platform, cookie, "")

    fun logout(platform: String) {
        viewModelScope.launch { repository.clear(platform) }
    }

    class Factory(
        private val repository: SimpleAccountRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SimpleAccountViewModel::class.java))
            return SimpleAccountViewModel(repository) as T
        }
    }
}
