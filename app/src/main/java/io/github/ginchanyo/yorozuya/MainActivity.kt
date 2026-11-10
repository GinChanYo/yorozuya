package io.github.ginchanyo.yorozuya

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.github.ginchanyo.yorozuya.ui.theme.YorozuyaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YorozuyaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TrackScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}



// @Composable - function that describes the state of the screen and does NOT build it.
// When data changes, Compose calls function again - it's called "recomposition".
// If I Kept "var tracks = emptyList<Track>()" then on each call this variable would have been recreated and value lost.
// Need a way to survive recomposition, that's why is used "remember".
@Composable
fun TrackScreen(modifier: Modifier = Modifier){
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    //DisposableEffect - is like a brother of LaunchedEffect, but with cleaning.
    // onDispose block executed when screen disappears. Player keeps audio decoder - it's limited resource and if not
    // freed, it causes a leak: sound keeps playing after screen closes, but decoders sooner or later will end.
    // Anything that is required to close, is closed with DisposableEffect
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    // mutableStateOf makes value observable. Assigning new - Compose notices it and redraws those places where it's read.
    // That's the whole trick: no updateUI() needed, just variable change.
    // "by" keyword is properties delegation. Without it, I would have had to write tracks.value on each addressation;
    // with it variable behaves as an ordinary one. Have to remember template:
    // "var x by remember {mutableStateOf(...)} I'll use it a lot."
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var tracks by remember {mutableStateOf(emptyList<Track>())}
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // LaunchedEffect is needed for actions, that will occur once, and not on each redrawing. Permission request is exactly like that:
    // if it's called in function's body, dialogue will appear infinite amount of times. Argument in brackets is a key:
    // Unit means: "execute once on screen occur", and "hasPermission" - "every time that this value changes, execute again".
    LaunchedEffect(Unit) {
        if(!hasPermission){
            launcher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            tracks = loadTracks(context)
        }
    }
    if (hasPermission){
        TrackList(tracks = tracks, onTrackClick = {track ->
            player.setMediaItem(MediaItem.fromUri(track.uri))
            player.prepare()
            player.play()
        }, modifier = modifier)
    }  else {
        Text(
            text = "Access to media on device is required",
            modifier = modifier.padding(16.dp)
        )
    }
}

@Composable
fun TrackList(tracks: List<Track>, onTrackClick: (Track) -> Unit, modifier: Modifier = Modifier){
    LazyColumn(modifier = modifier){
        items(tracks){ track ->
            TrackRow(track = track, onClick = {onTrackClick(track)})
        }
    }
}

@Composable
fun TrackRow(track: Track, onClick: () -> Unit){
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)){
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
        Text(
            text = track.artist ?: "Unknown artist",
            style = MaterialTheme.typography.bodySmall
        )
    }
}