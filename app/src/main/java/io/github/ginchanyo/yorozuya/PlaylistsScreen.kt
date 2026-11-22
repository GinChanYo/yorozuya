package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource

// "Lists" tab: header with backup buttons, Favorites, custom playlists with track counts, "New Playlist".
// Knows nothing about the database or files: data comes in, taps go out as callbacks (onImport/onExport
// only report the tap; the file dialogs live in AppScaffold)
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistEntity>,
    favoritesCount: Int,
    onOpen: (PlaylistTarget) -> Unit,
    onCreate: (String) -> Unit,
    counts: Map<Long, Int>,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    // Column - fixed header on top, the list takes the rest (weight below)
    Column(modifier = modifier.fillMaxSize()) {
        // Header: title pushed left by weight(1f), icon buttons at the right edge
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Playlists",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onImport) {
                Icon(painterResource(R.drawable.ic_backup_restore), contentDescription = "Import backup")
            }
            IconButton(onClick = onExport) {
                Icon(painterResource(R.drawable.ic_backup), contentDescription = "Export backup")
            }
        }
        // weight(1f) - the list gets all height left under the header and scrolls inside it
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                PlaylistRow(
                    name = "Favorites",
                    subtitle = "$favoritesCount tracks",
                    onClick = { onOpen(PlaylistTarget.Favorites) }
                )
            }
            items(playlists, key = { it.id }) { playlist ->
                val count = counts[playlist.id] ?: 0
                PlaylistRow(
                    name = playlist.name,
                    subtitle = "$count ${if (count == 1) "track" else "tracks"}",
                    onClick = { onOpen(PlaylistTarget.Custom(playlist.id)) }
                )
            }
            item {
                TextButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { Text("+ New Playlist") }
            }
        }
        if (showCreateDialog) {
            PlaylistNameDialog(
                title = "New playlist",
                confirmLabel = "Create",
                onConfirm = { name ->
                    showCreateDialog = false
                    onCreate(name)
                },
                onDismiss = { showCreateDialog = false }
            )
        }
    }
}

@Composable
private fun PlaylistRow(name: String, subtitle: String?, onClick: () -> Unit){
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ){
        Text(name, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null){
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember {mutableStateOf(initialName)}
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {Text(title)},
        text = {OutlinedTextField(
            value = name,
            onValueChange = { name = it},
            singleLine = true,
            placeholder = {Text("Name")}
        )},
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim())},
                enabled = name.isNotBlank()
            ) {Text(confirmLabel)}
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {Text("Cancel")}
        }
    )
}