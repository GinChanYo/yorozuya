package io.github.ginchanyo.yorozuya

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import io.github.ginchanyo.yorozuya.ui.theme.YorozuyaTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YorozuyaTheme {
                AppScaffold()
                }
            }
        }
    }



// @Composable - function that describes the state of the screen and does NOT build it.
// When data changes, Compose calls function again - it's called "recomposition".
// If I Kept "var tracks = emptyList<Track>()" then on each call this variable would have been recreated and value lost.
// Need a way to survive recomposition, that's why is used "remember".
@Composable
fun TrackScreen(player: PlayerState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // All playback state and controls live in PlayerState (PlayerConnection.kt); here only read fields and call methods
    val player = rememberPlayerState()
    var allTracks by remember { mutableStateOf(emptyList<Track>()) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var includeSubfolders by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("library", Context.MODE_PRIVATE) }
    // While the user drags the slider, the thumb follows the finger (seekPosition, 0..1 fraction)
    // instead of the polled position; the real seek happens once, when the finger is released
    var isSeeking by remember {mutableStateOf(false)}
    var seekPosition by remember {mutableStateOf(0f)}
    val tracks = remember(allTracks, selectedPaths, includeSubfolders) {
        when {
            selectedPaths.isEmpty() -> allTracks
            includeSubfolders -> allTracks.filter { track ->
                selectedPaths.any { track.relativePath.startsWith(it) }
            }

            else -> allTracks.filter { it.relativePath in selectedPaths }
        }
    }
    val folders = remember(allTracks, includeSubfolders){
        buildFolderList(allTracks, includeSubfolders)
    }
    val listState = rememberLazyListState() // this object keeps scroll position and allows to control it

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
        if (!hasPermission) {
            launcher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            allTracks = loadTracks(context)
            selectedPaths = prefs.getStringSet("paths", emptySet()) ?: emptySet()
            // Saved in FolderPickerDialog's onDismiss; without reading it back the checkbox resets on restart
            includeSubfolders = prefs.getBoolean("subfolders", false)
        }
    }
    // Scroll list to the playing track. Two keys: re-run when track changes AND when tracks are loaded
    // (on app return currentTrackId may arrive before the list, then index would be -1 and nothing would scroll).
    // LaunchedEffect body is a coroutine, that's why suspend function scrollToItem can be called here
    LaunchedEffect(player.currentTrackId, tracks) {
        val index = tracks.indexOfFirst { it.id == player.currentTrackId }
        if (index < 0) return@LaunchedEffect

        val info = listState.layoutInfo
        val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
        val itemHeight = info.visibleItemsInfo.firstOrNull()?.size ?: 0
        val offset = -(viewportHeight / 2 - itemHeight / 2)

        if (index !in info.visibleItemsInfo.map {it.index}){
            listState.animateScrollToItem(index, offset)
        }
    }
    // Player has no "position changed" callback (it would fire constantly), so position is polled twice a second.
    // Keys: restart when controller connects and on play/pause, so the value is fresh right after the change.
    // The loop ends by itself when the effect is cancelled (key change or screen leaves composition).
    LaunchedEffect(player.controller, player.isPlaying) {
        val mc = player.controller ?: return@LaunchedEffect
        while (true){
            // Don't overwrite position while dragging, otherwise the thumb would jump back under the finger
            if (!isSeeking){
                player.position = mc.currentPosition
            }
            // duration is C.TIME_UNSET (negative) until the track is prepared; coerceAtLeast clamps it to 0
            player.duration = mc.duration.coerceAtLeast(0L)
            // delay - suspends this coroutine only, the main thread keeps drawing (like await Task.Delay in C#).
            // 500.milliseconds - extension property turning Int into kotlin.time.Duration; clearer than bare 500
            delay(500.milliseconds)
        }
    }

    // firstOrNull searches list for track with matching ID and returns null if not found.
    // Searched in allTracks, not filtered tracks: the bar must stay even if the playing song is outside selected folders
    val currentTrack = allTracks.firstOrNull { it.id == player.currentTrackId }
    // Box - layout that stacks children on top of each other (later child is drawn above earlier one),
    // unlike Column/Row that place them one after another. Here: track list below, NowPlayingBar above it.
    // fillMaxSize - Box takes the whole screen, so its bottom edge is the screen bottom
    Box(modifier = modifier.fillMaxSize()) {
        // fillMaxSize - Column takes the whole Box, so the list below can stretch to the bottom
        Column(modifier = Modifier.fillMaxSize()) {
            // Row - buttons side by side in one line instead of stacked vertically
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {player.toggleShuffle()}){
                    Text(if (player.shuffleEnabled) "Shuffle: on" else "Shuffle: off")
                }
                TextButton(onClick = {showFolderPicker = true}) {
                    Text("Directories")
                }
                TextButton(onClick = {player.cycleRepeat()}) {
                    Text(
                        when (player.repeatMode){
                            Player.REPEAT_MODE_ALL -> "Repeat: All"
                            Player.REPEAT_MODE_ONE -> "Repeat: One"
                            else -> "Repeat: Off"
                        }
                    )
                }
            }

            if (!hasPermission) {
                Text(
                    text = "Access to media on device is required",
                    modifier = Modifier.padding(16.dp)
                )
            } else TrackList(
                // weight(1f) - list takes all height left under the buttons row
                modifier = Modifier.weight(1f).fillMaxWidth(),
                tracks = tracks,
                currentTrackId = player.currentTrackId,
                isPlaying = player.isPlaying,
                listState = listState,
                // Tap on the playing track - pause/resume; on another one - play current (filtered) list starting from it
                onTrackClick = { track ->
                    if (track.id == player.currentTrackId){
                        player.togglePlayPause()
                    } else {
                        player.playQueue(tracks, tracks.indexOf(track))
                    }
                })
        }
        // Bar is shown only when something is selected; Compose simply doesn't draw it when condition is false
        if (currentTrack != null) {
            NowPlayingBar(
                track = currentTrack,
                isPlaying = player.isPlaying,
                onPreviousClick = {player.previous()},
                onNextClick = {player.next()},
                onPlayPauseClick = {player.togglePlayPause()},
                // Slider works with 0..1 fraction; while dragging show the finger's value, otherwise the real one.
                // duration > 0 check avoids division by zero before the track is prepared
                progress = if(isSeeking) {
                    seekPosition
                } else if (player.duration > 0){
                    player.position.toFloat() / player.duration
                } else 0f,
                // Time label follows the thumb during drag, so the user sees where they'll land
                positionMs = if (isSeeking) (seekPosition * player.duration).toLong() else player.position,
                durationMs = player.duration,
                // Called on every drag step: only remember the value, don't seek yet
                onSeekChange = { fraction ->
                    isSeeking = true
                    seekPosition = fraction
                },
                // Called once when the finger is released: one real seek. position is set right away,
                // so the thumb doesn't flash back to the old spot until the next poll
                onSeekFinished = {
                    if (player.duration > 0) {
                        val target = (seekPosition * player.duration).toLong()
                        player.seekTo(target)
                        player.position = target
                    }
                    isSeeking = false
                },
                // align exists only inside Box { } (extension available in BoxScope) - pins the bar to bottom center
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        if (showFolderPicker) {
            FolderPickerDialog(
                folders = folders,
                selected = selectedPaths,
                includeSubfolders = includeSubfolders,
                onToggleSubfolders = { includeSubfolders = !includeSubfolders },
                onToggle = { path ->
                    selectedPaths = if (path in selectedPaths) {
                        selectedPaths - path
                    } else {
                        selectedPaths + path
                    }
                },
                onDismiss = {
                    prefs.edit()
                        .putStringSet("paths", selectedPaths)
                        .putBoolean("subfolders", includeSubfolders)
                        .apply()
                    showFolderPicker = false
                }
            )
        }
    }
}

@Composable
fun AppScaffold(){
    var currentTab by remember {mutableStateOf(Tab.QUEUE)}
    val player = rememberPlayerState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = {currentTab = tab},
                        icon = {Text(tab.label)},
                        label = null
                    )
                }
            }
        }
    ){ innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)){
            when (currentTab){
                Tab.QUEUE -> TrackScreen(player = player)
                Tab.NOW_PLAYING -> Placeholder("Now Playing")
                Tab.PLAYLISTS -> Placeholder("Playlists")
                Tab.STORAGE -> Placeholder("Folders")
                Tab.DOWNLOADER -> Placeholder("Downloader")
            }
        }
    }
}

@Composable
fun Placeholder(name: String){
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ){
        Text("$name - in development")
    }
}