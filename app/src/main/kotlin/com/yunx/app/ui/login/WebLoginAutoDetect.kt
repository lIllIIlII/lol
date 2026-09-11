package com.yunx.app.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yunx.app.ui.SnackbarController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val AUTO_DETECT_POLL_MS = 1_500L

private const val AUTO_DETECT_VALIDATE_THROTTLE_MS = 5_000L

private const val AUTO_DETECT_SAME_CREDENTIAL_RETRY_MS = 10_000L

@Composable
fun rememberWebLoginAutoDetect(
    sampleCredential: suspend () -> String?,
    isPlausible: (String) -> Boolean,
    validateAndSave: suspend (String) -> Boolean,
    isPaused: () -> Boolean = { false },
    onInFlightChange: (Boolean) -> Unit = {},
    onAutoSaved: () -> Unit
) {
    var finished by remember { mutableStateOf(false) }
    var lastValidateTs by remember { mutableLongStateOf(0L) }
    var lastFailedCredential by remember { mutableStateOf("") }
    var lastFailedTs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (!finished) {
            delay(AUTO_DETECT_POLL_MS)
            if (finished || isPaused()) continue
            val credential = try {
                sampleCredential()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (credential.isNullOrBlank() || !isPlausible(credential)) continue

            val now = System.currentTimeMillis()
            if (credential == lastFailedCredential &&
                now - lastFailedTs < AUTO_DETECT_SAME_CREDENTIAL_RETRY_MS
            ) {
                continue
            }
            if (now - lastValidateTs < AUTO_DETECT_VALIDATE_THROTTLE_MS) continue
            lastValidateTs = now
            finished = true
            onInFlightChange(true)
            val saved = try {
                validateAndSave(credential)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            onInFlightChange(false)
            if (saved) {
                SnackbarController.show("登录成功")
                onAutoSaved()
            } else {
                lastFailedCredential = credential
                lastFailedTs = System.currentTimeMillis()
                finished = false
            }
        }
    }
}
