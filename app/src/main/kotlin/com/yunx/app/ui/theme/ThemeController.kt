package com.yunx.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yunx.app.data.prefs.SettingsRepository

object ThemeController {

    var darkMode by mutableStateOf(0)
        private set

    var colorMode by mutableStateOf(0)
        private set

    var seedColor by mutableStateOf(SettingsRepository.DEFAULT_SEED_COLOR)
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val s = SettingsRepository(context)
        darkMode = s.darkMode
        colorMode = s.themeColorMode
        seedColor = s.themeSeedColor
        initialized = true
    }

    fun setDarkMode(context: Context, value: Int) {
        darkMode = value.coerceIn(0, 2)
        SettingsRepository(context).darkMode = darkMode
    }

    fun setColorMode(context: Context, value: Int) {
        colorMode = value.coerceIn(0, 2)
        SettingsRepository(context).themeColorMode = colorMode
    }

    fun setSeedColor(context: Context, argb: Long) {
        seedColor = argb
        colorMode = 2
        SettingsRepository(context).apply {
            themeSeedColor = argb
            themeColorMode = 2
        }
    }
}
