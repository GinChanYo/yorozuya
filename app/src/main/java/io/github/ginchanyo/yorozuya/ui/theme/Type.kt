package io.github.ginchanyo.yorozuya.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.ginchanyo.yorozuya.R

// Main typeface. space_grotesk.ttf is a variable font (wght 300–700), so one file
// provides every weight: each Font entry tells Compose which weight to request from it.
val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

// Accent typeface for large headings only (static, single weight).
val BogleFamily = FontFamily(Font(R.font.bogle_regular, FontWeight.Normal))

// Monospace for timestamps: equal-width digits keep the text from jittering as it changes.
val JetBrainsMonoFamily = FontFamily(Font(R.font.jetbrainsmono_regular, FontWeight.Normal))

// Material 3 defaults (sizes, weights, line heights, letter spacing) used as a base.
private val baseline = Typography()

val Typography = Typography(
    // Display & headline: big screen titles
    displayLarge = baseline.displayLarge.copy(fontFamily = BogleFamily),
    displayMedium = baseline.displayMedium.copy(fontFamily = BogleFamily),
    displaySmall = baseline.displaySmall.copy(fontFamily = BogleFamily),
    headlineLarge = baseline.headlineLarge.copy(fontFamily = BogleFamily),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = BogleFamily),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = BogleFamily),

    // Everything else: one family, roles differ by size and weight
    titleLarge = baseline.titleLarge.copy(fontFamily = SpaceGroteskFamily),
    titleMedium = baseline.titleMedium.copy(fontFamily = SpaceGroteskFamily),
    titleSmall = baseline.titleSmall.copy(fontFamily = SpaceGroteskFamily),
    bodyLarge = baseline.bodyLarge.copy(fontFamily = SpaceGroteskFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = SpaceGroteskFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = SpaceGroteskFamily),
    labelLarge = baseline.labelLarge.copy(fontFamily = SpaceGroteskFamily),
    labelMedium = baseline.labelMedium.copy(fontFamily = SpaceGroteskFamily),
    labelSmall = baseline.labelSmall.copy(fontFamily = SpaceGroteskFamily),
)
