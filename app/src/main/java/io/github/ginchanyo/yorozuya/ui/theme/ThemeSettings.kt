package io.github.ginchanyo.yorozuya.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// Theme choice that survives restarts: same pattern as LibraryFilter. Values are Compose state (so the theme
// redraws on change) with a private setter, and every update writes to SharedPreferences right away.
// @Stable - tells Compose that changes are reported through state, so it can skip work safely
@Stable
class ThemeSettings(private val prefs: SharedPreferences) {
    // Enums are stored by name; ?.let + find turns the saved text back into a value.
    // Unknown or missing name (first launch, renamed enum entry) falls back to the default via ?:
    var theme by mutableStateOf(
        prefs.getString("theme", null)
            ?.let { name -> AppTheme.entries.find { it.name == name } }
            ?: AppTheme.DYNAMIC
    )
        private set

    var mode by mutableStateOf(
        prefs.getString("mode", null)
            ?.let { name -> ThemeMode.entries.find { it.name == name } }
            ?: ThemeMode.SYSTEM
    )
        private set

    var amoled by mutableStateOf(prefs.getBoolean("amoled", false))
        private set

    fun updateTheme(value: AppTheme) { theme = value; save() }
    fun updateMode(value: ThemeMode) { mode = value; save() }
    fun toggleAmoled() { amoled = !amoled; save() }

    // All three values are written together; apply() saves in the background (commit() would block)
    private fun save() {
        prefs.edit()
            .putString("theme", theme.name)
            .putString("mode", mode.name)
            .putBoolean("amoled", amoled)
            .apply()
    }
}

// Created once in MainActivity, above YorozuyaTheme, because the theme itself depends on these values
@Composable
fun rememberThemeSettings(): ThemeSettings {
    val context = LocalContext.current
    return remember {
        ThemeSettings(context.getSharedPreferences("theme", Context.MODE_PRIVATE))
    }
}