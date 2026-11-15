package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// Seek slider + time labels, shared by the mini player and the "Now" tab.
// Owns its drag state: while the finger is down the thumb follows it, the real seek happens once on release.
// Each call site gets its own isSeeking/seekPosition (remember belongs to that call) - fine, since the
// mini player and the "Now" tab are never on screen at the same time
@Composable
fun PlayerSeekBar(player: PlayerState, modifier: Modifier = Modifier) {
    // UI-only state, so it isn't in PlayerState. seekPosition - 0..1 fraction under the finger
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0f) }

    // when without a subject - cleaner form of the if / else if / else chain.
    // Slider works with 0..1 fraction; duration > 0 check avoids division by zero before the track is prepared
    val progress = when {
        isSeeking -> seekPosition
        player.duration > 0 -> player.position.toFloat() / player.duration
        else -> 0f
    }
    // Time label follows the thumb during drag
    val shownPosition = if (isSeeking) (seekPosition * player.duration).toLong() else player.position

    Column(modifier = modifier) {
        Slider(
            value = progress,
            // Called on every drag step: only remember the value, don't seek yet
            onValueChange = { fraction ->
                isSeeking = true
                seekPosition = fraction
            },
            // Called once when the finger is released: one real seek. position is set right away,
            // so the thumb doesn't flash back to the old spot until the next poll
            onValueChangeFinished = {
                if (player.duration > 0) {
                    val target = (seekPosition * player.duration).toLong()
                    player.seekTo(target)
                    player.position = target
                }
                isSeeking = false
            }
        )
        // SpaceBetween - position at the left edge, duration at the right edge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(shownPosition), style = MaterialTheme.typography.labelSmall)
            Text(formatTime(player.duration), style = MaterialTheme.typography.labelSmall)
        }
    }
}