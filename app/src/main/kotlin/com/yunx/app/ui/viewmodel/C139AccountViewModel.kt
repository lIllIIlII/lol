package com.yunx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.repository.C139AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class C139AccountViewModel(
    private val repository: C139AccountRepository
) : ViewModel() {

    val c139Account: StateFlow<C139AccountEntity?> = repository.observeAccount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    suspend fun saveC139Account(cookie: String): Boolean =
        repository.saveC139Account(cookie)

    fun logout() {
        viewModelScope.launch { repository.logoutC139() }
    }

    class Factory(
        private val repository: C139AccountRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(C139AccountViewModel::class.java))
            return C139AccountViewModel(repository) as T
        }
    }
}
