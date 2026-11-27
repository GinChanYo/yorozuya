package io.github.ginchanyo.yorozuya.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp

// Content of the "Appearance" section on the Settings screen: color theme, light/dark mode, AMOLED black.
// No outer title or vertical padding - SettingsSection provides them; horizontal padding matches the section title
@Composable
fun AppearanceSettings(settings: ThemeSettings) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        AppTheme.entries.forEach { theme ->
            Row(
                // The whole row is clickable, not just the small radio circle
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settings.updateTheme(theme) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = settings.theme == theme, onClick = { settings.updateTheme(theme) })
                // Wallpaper has no seed, so show the system accent color instead
                val swatch = theme.seed ?: colorResource(android.R.color.system_accent1_500)
                Spacer(
                    Modifier
                        .size(20.dp)
                        .background(swatch, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Text(theme.label)
            }
        }

        Text("Mode", style = MaterialTheme.typography.titleMedium)
        // Segmented buttons: one connected row of options, exactly one selected
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.mode == mode,
                    onClick = { settings.updateMode(mode) },
                    // itemShape rounds only the outer corners of the first and last button
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                ) { Text(mode.label) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { settings.toggleAmoled() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // weight(1f) makes the text take the free space and pushes the switch to the right
            Text("Pure black (AMOLED)", modifier = Modifier.weight(1f))
            Switch(checked = settings.amoled, onCheckedChange = { settings.toggleAmoled() })
        }
    }
}
