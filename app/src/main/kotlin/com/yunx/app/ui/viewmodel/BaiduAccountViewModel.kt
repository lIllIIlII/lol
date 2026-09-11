package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.repository.BaiduAccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BaiduAccountViewModel(
    private val repository: BaiduAccountRepository
) : ViewModel() {

    val baiduAccount: StateFlow<BaiduAccountEntity?> = repository.observeAccount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    suspend fun saveBaiduAccount(cookie: String): Boolean =
        repository.saveBaiduAccount(cookie)

    fun logout() {
        viewModelScope.launch { repository.logoutBaidu() }
    }

    class Factory(
        private val repository: BaiduAccountRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BaiduAccountViewModel::class.java))
            return BaiduAccountViewModel(repository) as T
        }
    }
}
