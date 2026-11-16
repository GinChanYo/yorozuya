package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun FavoriteScreen(
    player: PlayerState,
    tracks: List<Track>,
    listState: LazyListState,
    modifier: Modifier = Modifier
){
    if (tracks.isEmpty()){
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center){
            Text("No favorites yet - tap ♡ on the Song tab")
        }
        return
    }
    TrackList(
        tracks = tracks,
        currentTrackId = player.currentTrackId,
        isPlaying = player.isPlaying,
        listState = listState,
        onTrackClick = { track ->
            if (track.id == player.currentTrackId) player.togglePlayPause()
            else player.playQueue(tracks,tracks.indexOf(track))
        },
        modifier = modifier.fillMaxSize()
    )
}