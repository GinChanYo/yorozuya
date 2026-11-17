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

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistEntity>,
    favoritesCount: Int,
    onOpen: (PlaylistTarget) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
){
    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier.fillMaxSize()){
        item {
            PlaylistRow(
                name = "Favorites",
                subtitle = "$favoritesCount tracks",
                onClick = { onOpen(PlaylistTarget.Favorites)}
            )
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistRow(
                name = playlist.name,
                subtitle = null,
                onClick = { onOpen(PlaylistTarget.Custom(playlist.id))}
            )
        }
        item {
            TextButton(
                onClick = {showCreateDialog = true},
                modifier = Modifier.padding(horizontal = 8.dp)
            ) { Text("+ New Playlist")}
        }
    }
    if (showCreateDialog){
        CreatePlaylistDialog(
            onConfirm = { name ->
                showCreateDialog = false
                onCreate(name)
            },
            onDismiss = { showCreateDialog = false }
        )
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
private fun CreatePlaylistDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit){
    var name by remember {mutableStateOf("")}
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {Text("New Playlist")},
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
            ) {Text("Create")}
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {Text("Cancel")}
        }
    )
}