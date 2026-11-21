package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ReorderableTrackList(
    tracks: List<Track>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    listState: LazyListState,
    onTrackClick: (Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.index, to.index)
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(tracks, key = { it.id }) { track ->
            ReorderableItem(reorderableState, key = track.id) { isDragging ->
                Surface(tonalElevation = if (isDragging) 4.dp else 0.dp) {
                    TrackRow(
                        track = track,
                        isCurrent = track.id == currentTrackId,
                        isPlaying = isPlaying,
                        onClick = { onTrackClick(track) },
                        onLongClick = { onTrackLongClick(track) },
                        dragHandle = {
                            Icon(
                                painter = painterResource(R.drawable.ic_drag_handle),
                                contentDescription = "Reorder",
                                modifier = Modifier.draggableHandle(onDragStopped = {onDragEnd()})
                            )
                        }
                    )
                }
            }
        }
    }
}