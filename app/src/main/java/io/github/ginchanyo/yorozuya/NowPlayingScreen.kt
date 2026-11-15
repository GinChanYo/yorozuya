package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

// "Now" tab: big artwork, title/artist/album, seek bar and all playback controls.
// track is nullable - before anything was played there is no current track
@Composable
fun NowPlayingScreen(
    player: PlayerState,
    track: Track?,
    // Shows a short bubble at the bottom (Snackbar in AppScaffold) - called from onClick, never from drawing code
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier
){
    // Early return from a composable: draw the empty state and skip the rest.
    // After this "if", Kotlin smart-casts track to non-null Track, so track.title below needs no "?."
    if (track == null){
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center){
            Text("Nothing is playing")
        }
        return
    }
    val artwork = rememberArtwork(track.uri)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        // Artwork placeholder: square box that keeps its shape on any screen width
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // aspectRatio(1f) - height = width
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ){
            if (artwork != null){
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("♪", style = MaterialTheme.typography.displayLarge)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist ?: "Unknown Artist",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = track.album ?: "",
            style = MaterialTheme.typography.bodyMedium
        )

        // weight(1f) on a spacer pushes everything below it to the bottom of the screen
        Spacer(modifier = Modifier.weight(1f))
        PlayerSeekBar(player = player)
        // SpaceEvenly - equal gaps between buttons and at both edges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ){
            // "\uD83D\uDD00" - emoji written as two UTF-16 code units (surrogate pair), because emoji don't fit in one Char
            // Buttons show emoji, the message uses words - a bubble with just an emoji wouldn't say much
            TextButton(onClick = {
                player.toggleShuffle()
                showMessage(if (player.shuffleEnabled) "Shuffle: on" else "Shuffle: off")
            }){
                Text(if (player.shuffleEnabled) "\uD83D\uDD00" else "➡")
            }
            TextButton(onClick = {player.previous()}) {Text("◀◀")}
            TextButton(onClick = {player.togglePlayPause()}) {
                Text(if (player.isPlaying) "⏸" else "▶", style = MaterialTheme.typography.headlineMedium)
            }
            TextButton(onClick = {player.next()}) {Text("▶▶")}
            TextButton(onClick = {
                player.cycleRepeat()
                showMessage("Repeat: ${repeatModeName(player.repeatMode)}")
            }) {
                Text(
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ALL -> "\uD83D\uDD01"
                        Player.REPEAT_MODE_ONE -> "\uD83D\uDD02"
                        else -> "↪"
                    }
                )
            }
        }
    }
}