package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
    // Slot: the caller passes a piece of UI to draw at the row's end (e.g. a drag handle icon). null - nothing is drawn
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
            // Small play icon before the title of the playing track; Text takes the width left after it
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent && isPlaying) {
                    Icon(
                        painterResource(R.drawable.ic_play),
                        contentDescription = "Playing",
                        tint = titleColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 1
                )
            }
            Text(
                text = track.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (dragHandle != null) {
            Spacer(modifier = Modifier.width(8.dp))
            dragHandle()
        }
    }
}

// Scrolls so that item `index` ends up in the middle of the viewport. Does nothing if it's already fully visible.
// Extension function: declared outside LazyListState but called like its own method - listState.centerOn(...).
// suspend - animateScrollToItem waits for the animation to finish, so this can only run inside a coroutine.
// animate = false - requestScrollToItem: no animation, applied on the next measure (list off screen or just opened)
suspend fun LazyListState.centerOn(index: Int, animate: Boolean) {
    val info = layoutInfo
    // visibleItemsInfo also contains items cut by the edge (e.g. last one peeking from the bottom),
    // so check that the item is entirely inside the viewport, not just present in the list.
    // offset - item's top edge in px from the viewport top; size - its height
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item != null && item.offset >= info.viewportStartOffset &&
        item.offset + item.size <= info.viewportEndOffset) return
    val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
    val itemHeight = info.visibleItemsInfo.firstOrNull()?.size ?: 0
    // Negative offset - item is placed this many px below the top edge, i.e. at the center
    val offset = -(viewportHeight / 2 - itemHeight / 2)
    if (animate) animateScrollToItem(index, offset) else requestScrollToItem(index, offset)
}
