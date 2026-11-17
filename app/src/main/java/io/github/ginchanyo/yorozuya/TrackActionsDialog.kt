package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
@Composable
fun TrackActionsDialog(
    track: Track,
    isFavorite: Boolean,
    playlists: List<PlaylistEntity>,
    onToggleFavorite: () -> Unit,
    onAddTo: (PlaylistEntity) -> Unit,
    onDismiss: () -> Unit,
){
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            LazyColumn {
                item {
                    ActionRow(
                        text = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        onClick = onToggleFavorite
                    )
                }
                if (playlists.isNotEmpty()){
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                }
                items(playlists, key = { it.id}) {playlist ->
                    ActionRow(text = "Add to ${playlist.name}", onClick = {onAddTo(playlist)})
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {Text("Close")}
        }
    )
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit){
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}