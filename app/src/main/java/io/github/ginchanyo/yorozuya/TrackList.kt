package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Scrollable list of tracks; highlights the playing one. Like NowPlayingBar, it only gets data and
// a callback (onTrackClick) - the parent decides what a tap does
@Composable
fun TrackList(
    tracks: List<Track>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    // State is created in AppScaffold and passed down, so it survives tab switches
    listState: LazyListState,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: ((Track) -> Unit)? = null,
    // Empty space after the last item, so it can be scrolled out from under an overlay (e.g. NowPlayingBar)
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        // contentPadding - empty space added after the last item (inside scrollable area, unlike Modifier.padding),
        // so last track can be scrolled out from under NowPlayingBar
        // 140.dp - bar grew with the slider and time labels; re-check on device if the bar changes again
        contentPadding = PaddingValues(bottom = bottomPadding),
        modifier = modifier
    ) {
        items(tracks) { track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onClick = { onTrackClick(track) },
                onLongClick = if (onTrackLongClick != null) { { onTrackLongClick(track) } } else null,
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    dragHandle: (@Composable () -> Unit)? = null
) {
    val titleColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ){
        ArtworkThumb(uri = track.uri, size = 48.dp, sizePx = 128)
        Spacer(modifier = Modifier.width(12.dp))
        // weight(1f) - text column takes all width left after the artwork and spacer
        Column(modifier = Modifier.weight(1f)) {
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
            if (dragHandle != null){
                Spacer(modifier = Modifier.width(8.dp))
                dragHandle()
            }
    }
}
