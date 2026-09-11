package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.repository.Pan123AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class Pan123AccountViewModel(
    private val repository: Pan123AccountRepository
) : ViewModel() {

    val pan123Account: StateFlow<Pan123AccountEntity?> = repository.observeAccount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    suspend fun saveToken(token: String): Boolean = repository.saveToken(token)

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    class Factory(
        private val repository: Pan123AccountRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(Pan123AccountViewModel::class.java))
            return Pan123AccountViewModel(repository) as T
        }
    }
}
