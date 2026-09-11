package com.yunx.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.ui.theme.IosAlertDialog
import com.yunx.app.ui.theme.IosDialogActions
import com.yunx.app.ui.theme.IosDialogButton
import com.yunx.app.ui.theme.IosDialogIcon
import com.yunx.app.ui.theme.IosDialogMessage
import com.yunx.app.ui.theme.IosDialogTitle
import com.yunx.app.ui.theme.IosTagChip
import com.yunx.app.util.TextCipher
import kotlinx.coroutines.delay

@Composable
fun SafetyNoticeDialog(onDismissed: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lockSeconds = 2
    var visible by remember { mutableStateOf(!isAcknowledged(context)) }
    var seconds by remember { mutableIntStateOf(lockSeconds) }

    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
    }

    if (!visible) {
        LaunchedEffect(Unit) { onDismissed?.invoke() }
        return
    }

    IosAlertDialog(
        onDismissRequest = {
            if (seconds <= 0) {
                visible = false
                acknowledge(context)
                onDismissed?.invoke()
            }
        }
    ) {
        IosDialogIcon {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        IosDialogTitle(TextCipher.dTitle)
        Spacer(Modifier.height(8.dp))
        IosTagChip(TextCipher.dOfficial)
        Spacer(Modifier.height(12.dp))
        IosDialogMessage(TextCipher.dBody)
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        ) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = if (MaterialTheme.colorScheme.background.luminanceCompat() > 0.5f) 0.45f else 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Text(
                    text = TextCipher.dUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        IosDialogActions(
            primary = (if (seconds > 0) TextCipher.dCountdown.replace("%d", seconds.toString()) else TextCipher.dBtn) to {
                visible = false
                acknowledge(context)
                onDismissed?.invoke()
            },
            secondary = (TextCipher.dCopy) to { copyUrl(context) },
            primaryEnabled = seconds <= 0
        )
    }
}

private fun Color.luminanceCompat(): Float {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
}

private fun isAcknowledged(context: Context): Boolean {
    return runCatching {
        context.getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
            .getBoolean(TextCipher.pNoticeFlag, false)
    }.getOrDefault(false)
}

private fun acknowledge(context: Context) {
    runCatching {
        context.getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(TextCipher.pNoticeFlag, true)
            .apply()
    }
}

private fun copyUrl(context: Context) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(TextCipher.dUrl, TextCipher.dUrl))
        Toast.makeText(context, TextCipher.dCopied, Toast.LENGTH_SHORT).show()
    }
}
