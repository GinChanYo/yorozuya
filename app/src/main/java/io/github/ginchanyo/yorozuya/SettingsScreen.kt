package io.github.ginchanyo.yorozuya

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.ginchanyo.yorozuya.ui.theme.AppearanceSettings
import io.github.ginchanyo.yorozuya.ui.theme.ThemeSettings

// Settings: shown over the tab content, not a tab of its own.
// Closed by the back arrow, system back (BackHandler) or tapping any tab in NavigationBar
@Composable
fun SettingsScreen(onBack: () -> Unit, themeSettings: ThemeSettings, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    // Column - header on top, sections below it.
    // verticalScroll - plain Column doesn't scroll; rememberScrollState keeps the scroll offset across recompositions.
    // Not LazyColumn: a few sections don't need lazy loading, and every section would need wrapping in item { }
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        // Trailing lambda: the last parameter (content) is written outside the parentheses
        SettingsSection("Appearance") { AppearanceSettings(themeSettings) }
    }
}

// One block of the settings screen: colored title + whatever is passed as content (a "slot", like dragHandle).
// ColumnScope.() - receiver lambda: inside the block "this" is the Column's scope,
// so column-only modifiers (weight, align) work there as if written directly inside Column
@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        // Called inside Column, so the receiver (ColumnScope) is provided by it
        content()
    }
}
