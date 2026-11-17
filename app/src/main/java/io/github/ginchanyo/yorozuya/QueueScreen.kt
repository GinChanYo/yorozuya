package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


// @Composable - function that describes the state of the screen and does NOT build it.
// When data changes, Compose calls function again - it's called "recomposition".
// If I Kept "var tracks = emptyList<Track>()" then on each call this variable would have been recreated and value lost.
// Need a way to survive recomposition, that's why is used "remember".
// Queue tab: buttons, track list and mini player. Everything comes from AppScaffold as parameters (read-only here):
// tracks is already filtered by the "Folders" tab's LibraryFilter, so this screen doesn't know about folders at all
@Composable
fun QueueScreen(
    player: PlayerState,
    tracks: List<Track>,
    currentTrack: Track?,
    hasPermission: Boolean,
    listState: LazyListState,
    // Shows a short bubble at the bottom (Snackbar in AppScaffold) - called from onClick, never from drawing code
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
)
{
    // Box - layout that stacks children on top of each other (later child is drawn above earlier one),
    // unlike Column/Row that place them one after another. Here: track list below, NowPlayingBar above it.
    // fillMaxSize - Box takes the whole screen, so its bottom edge is the screen bottom
    Box(modifier = modifier.fillMaxSize()) {
        // fillMaxSize - Column takes the whole Box, so the list below can stretch to the bottom
        Column(modifier = Modifier.fillMaxSize()) {
            // Row - buttons side by side in one line instead of stacked vertically
            Row(modifier = Modifier.fillMaxWidth()) {
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
                modifier = Modifier.weight(1f).fillMaxWidth(),
                tracks = tracks,
                currentTrackId = player.currentTrackId,
                isPlaying = player.isPlaying,
                listState = listState,
                bottomPadding = 140.dp,
                // Tap on the playing track - pause/resume; on another one - play current (filtered) list starting from it
                onTrackClick = { player.playOrToggle(it, tracks)
                }
            )
        }
        // Bar is shown only when something is selected; Compose simply doesn't draw it when condition is false
        if (currentTrack != null) {
            NowPlayingBar(
                track = currentTrack,
                isPlaying = player.isPlaying,
                onPreviousClick = {player.previous()},
                onNextClick = {player.next()},
                onPlayPauseClick = {player.togglePlayPause()},
                player = player,
                // align exists only inside Box { } (extension available in BoxScope) - pins the bar to bottom center
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}