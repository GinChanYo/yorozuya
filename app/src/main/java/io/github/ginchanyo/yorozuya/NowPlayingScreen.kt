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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
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
        Row(modifier = Modifier.fillMaxWidth()){
            IconButton(onClick = onToggleFavorite){
                Icon(
                    painter = painterResource(
                        if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                    ),
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
                )
            }
        }
        PlayerSeekBar(player = player)
        // SpaceEvenly - equal gaps between buttons and at both edges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ){
            // Buttons show icons, the message uses words - icon state alone is easy to misread
            IconButton(onClick = {
                player.toggleShuffle()
                showMessage(if (player.shuffleEnabled) "Shuffle: on" else "Shuffle: off")
            }) {
                Icon(
                    painterResource(if (player.shuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle),
                    contentDescription = "Shuffle"
                )
            }
            IconButton(onClick = { player.previous() }) {
                Icon(painterResource(R.drawable.ic_skip_previous), contentDescription = "Previous")
            }
            // Bigger main button: size on IconButton - touch area, size on Icon - the drawn picture
            IconButton(onClick = { player.togglePlayPause() }, modifier = Modifier.size(64.dp)) {
                Icon(
                    painterResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(48.dp)
                )
            }
            IconButton(onClick = { player.next() }) {
                Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "Next")
            }
            IconButton(onClick = {
                player.cycleRepeat()
                showMessage("Repeat: ${repeatModeName(player.repeatMode)}")
            }) {
                Icon(
                    painterResource(
                        when (player.repeatMode) {
                            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on
                            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one_on
                            else -> R.drawable.ic_repeat
                        }
                    ),
                    contentDescription = "Repeat: ${repeatModeName(player.repeatMode)}"
                )
            }
        }
    }
}