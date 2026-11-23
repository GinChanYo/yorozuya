package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp


// @Composable - function that describes the state of the screen and does NOT build it.
// When data changes, Compose calls function again - it's called "recomposition".
// If I Kept "var tracks = emptyList<Track>()" then on each call this variable would have been recreated and value lost.
// Need a way to survive recomposition, that's why is used "remember".
// Queue tab: buttons and track list. Everything comes from AppScaffold as parameters (read-only here):
// tracks is already filtered by the "Folders" tab's LibraryFilter, so this screen doesn't know about folders at all
@Composable
fun QueueScreen(
    player: PlayerState,
    tracks: List<Track>,
    source: QueueSource,
    sources: List<Pair<QueueSource, String>>,
    onSourceChange: (QueueSource) -> Unit,
    hasPermission: Boolean,
    listState: LazyListState,
    // Shows a short bubble at the bottom (Snackbar in AppScaffold) - called from onClick, never from drawing code
    showMessage: (String) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isActive = source == player.activeSource
    // Column - buttons row on top, track list below it.
    // fillMaxSize - takes all space Scaffold gives (innerPadding already excludes the bottom bars)
    Column(modifier = modifier.fillMaxSize()) {
        // Row - buttons side by side in one line instead of stacked vertically
        Row(modifier = Modifier.fillMaxWidth()) {
            // DropdownMenu is positioned relative to its parent, so the button and the menu share one Box
            Box {
                TextButton(
                    onClick =
                        {
                            expanded = true
                        })
                {
                    Text(sources.firstOrNull { it.first == source }?.second ?: "")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    sources.forEach { (src, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onSourceChange(src)
                                expanded = false
                            })
                    }
                }
            }
            // onClick - what happens on tap (runs once); trailing { } - what the button looks like
            // (runs on every recomposition, so no actions there).
            // Message is shown after the toggle: PlayerState updates its fields right away, so the text is the new mode
            TextButton(onClick = {
                player.toggleShuffle()
                showMessage(if (player.shuffleEnabled) "Shuffle: on" else "Shuffle: off")
            }) {
                Text(if (player.shuffleEnabled) "Shuffle: on" else "Shuffle: off")
            }
            TextButton(onClick = {
                player.cycleRepeat()
                showMessage("Repeat: ${repeatModeName(player.repeatMode)}")
            }) {
                Text("Repeat: ${repeatModeName(player.repeatMode)}")
            }
        }

        if (!hasPermission) {
            Text(
                text = "Access to media on device is required",
                modifier = Modifier.padding(16.dp)
            )
        } else TrackList(
            // weight(1f) - list takes all height left under the buttons row
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .alpha(if (isActive) 1f else 0.5f),
            tracks = tracks,
            currentTrackId = player.currentTrackId,
            isPlaying = player.isPlaying,
            listState = listState,
            // Tap on the playing track - pause/resume; on another one - play current (filtered) list starting from it
            onTrackClick = { player.playOrToggle(it, tracks, source) },
            onTrackLongClick = onTrackLongClick
        )
    }
}
