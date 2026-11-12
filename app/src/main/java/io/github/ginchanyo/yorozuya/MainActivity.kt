package io.github.ginchanyo.yorozuya

import android.Manifest
import android.content.ComponentName
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
import androidx.compose.material3.TextButton
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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
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
    var controller by remember {mutableStateOf<MediaController?>(null)}
    var currentTrackId by remember {mutableStateOf<Long?>(null)}
    var isPlaying by remember {mutableStateOf(false)}
    var shuffleEnabled  by remember {mutableStateOf(false)}
    var tracks by remember {mutableStateOf(emptyList<Track>())}
    //DisposableEffect - is like a brother of LaunchedEffect, but with cleaning.
    // onDispose block executed when screen disappears. Player keeps audio decoder - it's limited resource and if not
    // freed, it causes a leak: sound keeps playing after screen closes, but decoders sooner or later will end.
    // Anything that is required to close, is closed with DisposableEffect
    DisposableEffect(context) {
        // Session token - services' address, thanks to which connection is available
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        // Next line established the connection async-ly: service may yet not start, that's why returns ListenableFuture
        // promise of result, like Task in C#. When connection is ready, listener fires, and I get controller via future.get() and put it in state.
        // That's why controller is declared as MediaController? - at first few milliseconds it does not exist yet.
        val future = MediaController.Builder(context, token).buildAsync()

        // Local function (function declared inside another function, like C# local functions).
        // Reads current state right after connecting, because listener only reports *future* changes:
        // without it, returning to the app showed nothing as playing until next track change
        fun syncFromPlayer(p: Player){
            currentTrackId = p.currentMediaItem?.mediaId?.toLongOrNull()
            isPlaying = p.isPlaying
        }

        future.addListener(
            {
                val mc = future.get()
                mc.addListener(object :  Player.Listener{
                    override fun onIsPlayingChanged(playing: Boolean){
                        isPlaying = playing
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentTrackId = mediaItem?.mediaId?.toLongOrNull()
                    }
                })
                syncFromPlayer(mc)
                controller = mc
            },
            MoreExecutors.directExecutor()
        )
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
        // MediaController implements same interface Player, that ExoPlayer does. That's why controller code - setMediaItems, play, pause, shuffleModeEnabled stays the same.
        // The only change is that commands go to different process, and do not execute locally. For that exact reason Media3 is set through shared interface
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
        Column(modifier = modifier){
            TextButton(
                onClick = {
                    shuffleEnabled = !shuffleEnabled
                    controller?.shuffleModeEnabled = shuffleEnabled
                }
            ) {
                Text (if (shuffleEnabled) "Shuffle: on" else "Shuffle: off")
            }
        TrackList(
            tracks = tracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onTrackClick = {track ->
                val mc =controller ?: return@TrackList // marked return: escapes lambda, not entire function. without mark Kotlin won't know where to return from
                if (track.id == currentTrackId){
                    if(mc.isPlaying) mc.pause() else mc.play()
                } else {
                    val index = tracks.indexOf(track)
                    // tracks.map {MediaItem.fromUri(it.uri)} turns list of tracks into list of elements for player
                    // It's same Select from LINQ, and "it" - implicit name for parameter in lambda, when it's alone. Then index tells from which element to start,
                    // 0L - from which millisecond inside of it
                    mc.setMediaItems(
                        tracks.map{
                            // mediaId - our own label for the item (MediaStore id as text), so any
                            // controller can tell which track is playing without relying on list indices
                            MediaItem.Builder()
                                .setUri(it.uri)
                                .setMediaId(it.id.toString())
                                .build()},
                        index,
                        0L)
                    mc.prepare()
                    mc.play()
            }
        }, modifier = modifier)
        }
    }  else {
        Text(
            text = "Access to media on device is required",
            modifier = modifier.padding(16.dp)
        )
    }
}

@Composable
fun TrackList(
    tracks: List<Track>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier){
    LazyColumn(modifier = modifier){
        items(tracks){ track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onClick = {onTrackClick(track)})
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