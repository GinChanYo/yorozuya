package io.github.ginchanyo.yorozuya.ui.theme

import androidx.compose.ui.graphics.Color

// Each theme is just a name and one seed color - MaterialKolor derives the whole
// Material 3 scheme (primary, surface, outline... ~30 roles) from it, for light and dark.
// DYNAMIC has no seed: it takes colors from the system wallpaper instead
enum class AppTheme(val label: String, val seed: Color?) {
    DYNAMIC("Wallpaper", null),
    SILVER("Silver", Color(0xFF6B8CAE)),
    CRIMSON("Crimson", Color(0xFFB33A3A)),
    FOREST("Forest", Color(0xFF4C7A4C))
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark")
}