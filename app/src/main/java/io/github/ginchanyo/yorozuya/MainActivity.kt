package io.github.ginchanyo.yorozuya

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import io.github.ginchanyo.yorozuya.ui.theme.YorozuyaTheme

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
// Queue tab: buttons, track list and mini player. Everything comes from AppScaffold as parameters (read-only here):
// tracks is already filtered by the "Folders" tab's LibraryFilter, so this screen doesn't know about folders at all
@Composable
fun TrackScreen(
    player: PlayerState,
    tracks: List<Track>,
    currentTrack: Track?,
    hasPermission: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
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
                player = player,
                // align exists only inside Box { } (extension available in BoxScope) - pins the bar to bottom center
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// Root of the UI: bottom navigation + the screen of the selected tab.
// State that more than one tab needs (player, track list, permission) lives here and is passed down -
// "state goes down, events go up". Tab screens leave composition when switched away, so anything kept
// only inside them is lost; anything kept here survives tab switches
@Composable
fun AppScaffold(){
    var currentTab by remember { mutableStateOf(Tab.QUEUE) }
    val context = LocalContext.current
    // All playback state and controls live in PlayerState (PlayerConnection.kt); here only read fields and call methods
    val player = rememberPlayerState()
    // Keeps scroll position (and last visible items) of the Queue list. Lives here, not in TrackScreen,
    // so it survives tab switches: the list reopens where it was and auto-scroll doesn't fire again
    val listState = rememberLazyListState()
    var allTracks by remember { mutableStateOf(emptyList<Track>()) }
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
    // Launcher for the system permission dialog; the lambda receives the user's answer
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
        if (hasPermission) allTracks = loadTracks(context)
    }
    // Folder filter: changed on the "Folders" tab, used by Queue - so it lives here, above both tabs.
    // tracks is recomputed only when one of the keys changes (library loaded, selection, subfolders option),
    // not on every recomposition (e.g. each position poll)
    val library = rememberLibraryFilter()
    val tracks = remember(allTracks, library.selectedPaths, library.includeSubfolders){
        library.filter(allTracks)
    }
    // Searched in allTracks, not filtered tracks: the playing song must show even if it's outside selected folders
    val currentTrack = allTracks.firstOrNull { it.id == player.currentTrackId }
    // Scaffold - Material screen skeleton with slots (topBar, bottomBar...); innerPadding is the space
    // the bars take, so content isn't drawn under them
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                // entries - list of all enum values in declaration order (like Enum.GetValues in C#)
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
            // Only the selected tab's screen is in composition; when is exhaustive over the enum, so no else needed
            when (currentTab){
                Tab.QUEUE -> TrackScreen(
                    player = player,
                    tracks = tracks,
                    currentTrack = currentTrack,
                    hasPermission = hasPermission,
                    listState = listState
                )
                Tab.NOW_PLAYING -> NowPlayingScreen(player = player, track = currentTrack)
                Tab.PLAYLISTS -> Placeholder("Playlists")
                Tab.STORAGE -> FoldersScreen(allTracks = allTracks, library = library)
                Tab.DOWNLOADER -> Placeholder("Downloader")
            }
        }
    }
}

// Temporary screen for tabs that aren't implemented yet
@Composable
fun Placeholder(name: String){
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ){
        Text("$name - in development")
    }
}