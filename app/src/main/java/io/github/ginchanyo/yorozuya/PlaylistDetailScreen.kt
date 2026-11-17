package io.github.ginchanyo.yorozuya

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PlaylistDetailScreen(
    title: String,
    tracks: List<Track>,
    player: PlayerState,
    listState: LazyListState,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
    onRemoveTrack: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    var trackToRemove by remember { mutableStateOf<Track?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onDelete != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete playlist")
                }
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Nothing here yet")
            }
        } else {
            TrackList(
                tracks = tracks,
                currentTrackId = player.currentTrackId,
                isPlaying = player.isPlaying,
                listState = listState,
                onTrackClick = { player.playOrToggle(it, tracks) },
                onTrackLongClick = {trackToRemove = it},
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("\"$title\" will be removed. The tracks stay on your device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
    trackToRemove?.let{ track ->
        AlertDialog(
            onDismissRequest = {trackToRemove = null},
            title = { Text("Remove from \"$title\"?") },
            text = { Text(track.title)},
            confirmButton = {
                TextButton(onClick = {
                    trackToRemove = null
                    onRemoveTrack(track)
                }) {Text("Remove")}
            },
            dismissButton = {
                TextButton(onClick = {trackToRemove = null }) {Text("Cancel")}
            }
            )
    }
}