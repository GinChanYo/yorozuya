package io.github.ginchanyo.yorozuya.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.material3.rememberDynamicColorScheme

// Plain values instead of ThemeSettings itself: the theme doesn't care where they come from,
// and previews can call YorozuyaTheme { } without SharedPreferences
@Composable
fun YorozuyaTheme(
    theme: AppTheme = AppTheme.DYNAMIC,
    mode: ThemeMode = ThemeMode.SYSTEM,
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Local copy so Kotlin can smart-cast Color? to Color inside the else branch
    val seed = theme.seed
    val colorScheme = if (seed == null) {
        // Wallpaper colors (Android 12+, always available since minSdk is 33)
        val context = LocalContext.current
        val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // System scheme has no AMOLED option, so paint the big surfaces pure black ourselves
        if (amoled && darkTheme) scheme.copy(background = Color.Black, surface = Color.Black) else scheme
    } else {
        // MaterialKolor builds the full scheme from one seed; remember* caches it until inputs change
        rememberDynamicColorScheme(seedColor = seed, isDark = darkTheme, isAmoled = amoled)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
