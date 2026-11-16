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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.ginchanyo.yorozuya.ui.theme.YorozuyaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    // Shows a short bubble at the bottom (Snackbar in AppScaffold) - called from onClick, never from drawing code
    showMessage: (String) -> Unit,
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
                // onClick - what happens on tap (runs once); trailing { } - what the button looks like
                // (runs on every recomposition, so no actions there).
                // Message is shown after the toggle: PlayerState updates its fields right away, so the text is the new mode
                TextButton(onClick = {
                    player.toggleShuffle()
                    showMessage(if (player.shuffleEnabled) "Shuffle: on" else "Shuffle: off")
                }){
                    Text(if (player.shuffleEnabled) "Shuffle: on" else "Shuffle: off")
                }
                TextButton(onClick = {
                    player.cycleRepeat()
                    showMessage("Repeat: ${repeatModeName(player.repeatMode)}")
                }) {
                    Text("Repeat: ${repeatModeName(player.repeatMode)}")
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
fun AppScaffold() {
    var currentTab by remember { mutableStateOf(Tab.QUEUE) }
    val context = LocalContext.current
    // All playback state and controls live in PlayerState (PlayerConnection.kt); here only read fields and call methods
    val player = rememberPlayerState()
    // Holds the currently shown snackbar; Scaffold draws it through snackbarHost
    val snackbarHostState = remember { SnackbarHostState() }
    // Coroutine scope tied to this composable: lets a click handler start a coroutine
    // (showSnackbar is suspend - it waits until the message disappears)
    val scope = rememberCoroutineScope()
    // Event passed down to the tabs ("events go up"): a lambda stored in a variable, same type as any (String) -> Unit parameter.
    // dismiss() first, so fast taps replace the bubble instead of queuing
    val showMessage: (String) -> Unit = { text ->
        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch {
            // Indefinite + manual dismiss after a delay - Snackbar has no "shorter than Short" option (Short is ~4 s).
            // Inner launch - a child coroutine that sits in showSnackbar while this one waits 1.5 s;
            // cancelling the child is what makes the bubble disappear.
            // If a newer tap already dismissed this message, job is finished and cancel() does nothing -
            // it can't close the newer bubble, that one belongs to another job
            val job = launch { snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Indefinite) }
            delay(1500)
            job.cancel()
        }
    }
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

    val favoriteDao = remember { YorozuyaDatabase.get(context).favoriteDao() }
    val favoriteIds by remember { favoriteDao.observeIds()}.collectAsState(initial = emptyList())

    val isCurrentFavorite = currentTrack != null && currentTrack.id in favoriteIds
    val toggleFavorite: () -> Unit = {
        val id = currentTrack?.id
        if (id != null){
            scope.launch(){
                if (id in favoriteIds){
                    favoriteDao.remove(id)
                    showMessage("Removed from favorites")
                } else {
                    favoriteDao.add(FavoriteEntity(trackId = id))
                    showMessage("Added to favorites")
                }
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Custom look instead of the default full-width Snackbar: a small pill sized to its text.
        // SnackbarHost's trailing lambda is called for each message; data.visuals.message - the text given to showSnackbar.
        // Scaffold places the host above bottomBar and centers it horizontally
        snackbarHost = {
            SnackbarHost(snackbarHostState){data ->
                // No width modifier - Surface is only as wide as its text, that's what makes it small.
                // RoundedCornerShape(50) - 50 percent of the height, fully round ends (4.dp would be a fixed radius).
                // inverseSurface/inverseOnSurface - same colors the default Snackbar uses (dark bubble in light theme).
                // contentColor - Text inside takes it automatically, no color = needed
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = data.visuals.message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                // entries - list of all enum values in declaration order (like Enum.GetValues in C#)
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = {currentTab = tab},
                        // painterResource - loads the vector drawable by its id; Icon tints it with the theme color
                        // (selected/unselected colors come from NavigationBarItem)
                        icon = {
                            Icon(
                                painter = painterResource(tab.icon),
                                contentDescription = tab.label
                            )
                        },
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
                    listState = listState,
                    showMessage = showMessage
                )
                Tab.NOW_PLAYING -> NowPlayingScreen(
                    player = player,
                    track = currentTrack,
                    showMessage = showMessage,
                    isFavorite = isCurrentFavorite,
                    onToggleFavorite = toggleFavorite)
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