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

@Stable
class ThemeSettings(private val prefs: SharedPreferences) {
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

    private fun save() {
        prefs.edit()
            .putString("theme", theme.name)
            .putString("mode", mode.name)
            .putBoolean("amoled", amoled)
            .apply()
    }
}

@Composable
fun rememberThemeSettings(): ThemeSettings {
    val context = LocalContext.current
    return remember {
        ThemeSettings(context.getSharedPreferences("theme", Context.MODE_PRIVATE))
    }
}