package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Mini player at the bottom of the screen: title, artist and Play/Pause button.
// It doesn't know about the controller: it only gets data and a callback (onPlayPauseClick),
// so the parent decides what a click does. This is "state hoisting" - common Compose pattern.
@Composable
fun NowPlayingBar(
    track: Track,
    isPlaying: Boolean,
    // () -> Unit - function type: takes nothing, returns nothing (like Action in C#)
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    // 0..1 position of the slider thumb
    progress: Float,
    // Shown as text under the slider, in milliseconds
    positionMs: Long,
    durationMs: Long,
    // (Float) -> Unit - function that takes the new slider fraction (like Action<float> in C#)
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    // Convention: every reusable composable accepts a modifier, so the caller can position it (e.g. align)
    modifier: Modifier = Modifier
) {
    // Surface - container with background color, shape and elevation.
    // tonalElevation - in Material 3 "higher" surfaces are tinted slightly, instead of only casting a shadow
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp
    ) {
        // Column - slider on top, time labels under it, then the row with title and buttons
        Column{
            // Slider - draggable bar; value must be in 0..1 by default (valueRange can change that).
            // onValueChange fires on every move, onValueChangeFinished once on release
            Slider(
                value = progress,
                onValueChange = onSeekChange,
                onValueChangeFinished = onSeekFinished,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            // SpaceBetween - first child at the left edge, last at the right edge, free space between them
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(positionMs), style = MaterialTheme.typography.labelSmall)
                Text(text = formatTime(durationMs), style = MaterialTheme.typography.labelSmall)
            }
            // Row - places children horizontally; CenterVertically aligns text and buttons on one middle line
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            )
            {
                ArtworkThumb(uri = track.uri, size = 40.dp, sizePx = 96)
                Spacer(modifier = Modifier.width(12.dp))
                // weight(1f) - take all width left after other children (the button), pushing button to the right edge.
                // Available only inside Row/Column scope, same idea as align in Box
                Column(modifier = Modifier.weight(1f)) {
                    // maxLines = 1 + Ellipsis - long title is cut to one line and ends with "…"
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // ?: (Elvis) - if artist is null, show fallback text
                    Text(
                        text = track.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onPreviousClick){Text("◀")}
                TextButton(onClick = onPlayPauseClick){Text(if (isPlaying)"⏸" else "▶")}
                TextButton(onClick = onNextClick){Text("▶▶")}
            }
        }
    }
}

// 83500 ms -> "1:23". Plain function, not @Composable - only formats data.
// "%d:%02d" - same format specifiers as printf in C: %02d pads seconds with a leading zero ("1:05", not "1:5")
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}