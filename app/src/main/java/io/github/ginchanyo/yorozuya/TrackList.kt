package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Mini player at the bottom of the screen: title, artist and Play/Pause button.
// It doesn't know about the controller: it only gets data and a callback (onPlayPauseClick),
// so the parent decides what a click does. This is "state hoisting" - common Compose pattern.

@Composable
fun TrackList(
    tracks: List<Track>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    // State is created in parent (TrackScreen) and passed down, so parent can scroll the list - state hoisting again
    listState: LazyListState,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        // contentPadding - empty space added after the last item (inside scrollable area, unlike Modifier.padding),
        // so last track can be scrolled out from under NowPlayingBar
        // 140.dp - bar grew with the slider and time labels; re-check on device if the bar changes again
        contentPadding = PaddingValues(bottom = 140.dp),
        modifier = modifier
    ) {
        items(tracks) { track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onClick = { onTrackClick(track) })
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val titleColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (isCurrent && isPlaying) "▶ ${track.title}" else track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            maxLines = 1
        )
        Text(
            text = track.artist ?: "Unknown Artist",
            style = MaterialTheme.typography.bodySmall
        )
    }
}