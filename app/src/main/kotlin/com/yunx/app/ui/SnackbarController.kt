package com.yunx.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SnackbarController {
    internal data class Event(val seq: Long, val message: String)

    private val _events = MutableStateFlow<Event?>(null)
    private var seq = 0L

    internal val events: StateFlow<Event?> = _events

    fun show(message: String) {
        _events.value = Event(++seq, message)
    }

    fun consume(shownSeq: Long) {
        val cur = _events.value
        if (cur != null && cur.seq == shownSeq) _events.value = null
    }
}

@Composable
fun GlobalSnackbarHost(modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(hostState) {
        SnackbarController.events.collect { event ->
            if (event != null) {
                hostState.showSnackbar(event.message)
                SnackbarController.consume(event.seq)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        SnackbarHost(hostState = hostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun rememberGlobalSnackbarHostState(): SnackbarHostState {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(hostState) {
        SnackbarController.events.collect { event ->
            if (event != null) {
                hostState.showSnackbar(event.message)
                SnackbarController.consume(event.seq)
            }
        }
    }
    return hostState
}
