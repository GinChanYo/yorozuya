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

// TrackList with drag-and-drop (library sh.calvin.reorderable). The list itself owns no order:
// it reports moves through onMove, the caller changes its list, and the new list comes back as `tracks`
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
    // Wraps the normal LazyListState and calls the lambda each time the dragged item passes another one
    // (several times per drag). from/to are LazyListItemInfo; .index - position in the list
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.index, to.index)
    }

    LazyColumn(state = listState, modifier = modifier) {
        // key is required: the library finds the dragged row by key, not by index (indices change while dragging)
        items(tracks, key = { it.id }) { track ->
            // ReorderableItem - makes the row movable; isDragging is true only for the row under the finger
            ReorderableItem(reorderableState, key = track.id) { isDragging ->
                // Tinted background lifts the dragged row visually above the others
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
                                // Drag starts only from the icon, so tap/long press on the row still work.
                                // onDragStopped - finger released: the single moment the order is saved
                                modifier = Modifier.draggableHandle(onDragStopped = { onDragEnd() })
                            )
                        }
                    )
                }
            }
        }
    }
}