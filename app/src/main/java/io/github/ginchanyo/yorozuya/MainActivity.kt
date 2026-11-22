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
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.ginchanyo.yorozuya.ui.theme.ThemeSettings
import io.github.ginchanyo.yorozuya.ui.theme.ThemePickerScreen
import io.github.ginchanyo.yorozuya.ui.theme.YorozuyaTheme
import io.github.ginchanyo.yorozuya.ui.theme.rememberThemeSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeSettings = rememberThemeSettings()
            YorozuyaTheme(
                theme = themeSettings.theme,
                mode = themeSettings.mode,
                amoled = themeSettings.amoled
            ) {
                AppScaffold(themeSettings = themeSettings)
            }
        }
    }
}

// Root of the UI: bottom navigation + the screen of the selected tab.
// State that more than one tab needs (player, track list, permission) lives here and is passed down -
// "state goes down, events go up". Tab screens leave composition when switched away, so anything kept
// only inside them is lost; anything kept here survives tab switches
@Composable
fun AppScaffold(themeSettings: ThemeSettings) {
    var currentTab by remember { mutableStateOf(Tab.QUEUE) }
    val context = LocalContext.current
    // All playback state and controls live in PlayerState (PlayerConnection.kt); here only read fields and call methods
    val player = rememberPlayerState()
    // Holds the currently shown snackbar; Scaffold draws it through snackbarHost
    val snackbarHostState = remember { SnackbarHostState() }
    // Coroutine scope tied to this composable: lets a click handler start a coroutine
    // (showSnackbar is suspended - it waits until the message disappears)
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
    // Keeps scroll position (and last visible items) of the Queue list. Lives here, not in QueueScreen,
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
    var actionTrack by remember {mutableStateOf<Track?>(null)}
    // Searched in allTracks, not filtered tracks: the playing song must show even if it's outside selected folders
    val currentTrack = allTracks.firstOrNull { it.id == player.currentTrackId }
    // Scroll list to the playing track. Two keys: re-run when track changes AND when tracks are loaded
    // (on app return currentTrackId may arrive before the list, then index would be -1 and nothing would scroll).
    // listState.requestScrollToItem() is ordinary not suspend function. It only remembers "on next measure start from this element"
    // When LazyColumn will appear again, it will draw on its position without animation.
    // Lives here, not in QueueScreen: QueueScreen isn't composed on other tabs, so its effect would miss track changes
    LaunchedEffect(player.currentTrackId, tracks) {
        val index = tracks.indexOfFirst { it.id == player.currentTrackId }
        if (index < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
        val itemHeight = info.visibleItemsInfo.firstOrNull()?.size ?: 0
        val offset = -(viewportHeight / 2 - itemHeight / 2)

        if (index in info.visibleItemsInfo.map {it.index})  return@LaunchedEffect
        if (currentTab == Tab.QUEUE) listState.animateScrollToItem(index, offset)
        else listState.requestScrollToItem(index, offset)
    }
    val favoriteDao = remember { YorozuyaDatabase.get(context).favoriteDao() }
    val favoriteIds by remember { favoriteDao.observeIds()}.collectAsState(initial = emptyList())

    val isCurrentFavorite = currentTrack != null && currentTrack.id in favoriteIds
    val toggleFavorite: (Long) -> Unit = { id ->
        scope.launch {
            if (id in favoriteIds) {
                favoriteDao.remove(id)
                showMessage("Removed from favorites")
            } else {
                favoriteDao.add(FavoriteEntity(trackId = id, position = favoriteDao.topPosition()))
                showMessage("Added to favorites")
            }
        }
    }
    val playlistDao = remember { YorozuyaDatabase.get(context).playlistDao() }
    val playlists by remember { playlistDao.observePlaylists() }.collectAsState(initial = emptyList())
    val playlistCounts by remember { playlistDao.observeCounts() }
        .collectAsState(initial = emptyList())
    val countsById = remember(playlistCounts){
        playlistCounts.associate { it.playlistId to it.count}
    }
    var openedList by remember { mutableStateOf<PlaylistTarget?>(null) }

    val openedTrackIds by remember(openedList) {
        when (val target = openedList) {
            PlaylistTarget.Favorites -> favoriteDao.observeIds()
            is PlaylistTarget.Custom -> playlistDao.observeTrackIds(target.id)
            null -> flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val tracksById = remember(allTracks) { allTracks.associateBy { it.id } }
    val openedTracks = remember(tracksById, openedTrackIds) {
        openedTrackIds.mapNotNull { tracksById[it] }
    }
    val detailListState = remember(openedList) { LazyListState() }
    // Scaffold - Material screen skeleton with slots (topBar, bottomBar...); innerPadding is the space
    // the bars take, so content isn't drawn under them
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
        // Column - mini player stacked above the navigation bar. Scaffold measures the whole bottomBar,
        // so innerPadding grows by the bar's height automatically and screens need no manual bottom padding
        bottomBar = {
            Column {
                // Hidden on the "Now" tab: that screen already shows the same controls
                if (currentTrack != null && currentTab != Tab.NOW_PLAYING) {
                    NowPlayingBar(
                        track = currentTrack,
                        isPlaying = player.isPlaying,
                        onPreviousClick = { player.previous() },
                        onNextClick = { player.next() },
                        onPlayPauseClick = { player.togglePlayPause() },
                        onClick = { currentTab = Tab.NOW_PLAYING },
                        player = player
                    )
                }
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
        }
    ){ innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)){
            // Only the selected tab's screen is in composition; when is exhaustive over the enum, so no else needed
            when (currentTab){
                Tab.QUEUE -> QueueScreen(
                    player = player,
                    tracks = tracks,
                    hasPermission = hasPermission,
                    listState = listState,
                    showMessage = showMessage,
                    onTrackLongClick = { actionTrack = it}
                )
                Tab.NOW_PLAYING -> NowPlayingScreen(
                    player = player,
                    track = currentTrack,
                    showMessage = showMessage,
                    isFavorite = isCurrentFavorite,
                    onToggleFavorite = {currentTrack?.let {toggleFavorite(it.id)}}
                )
                Tab.PLAYLISTS -> when (val target = openedList) {
                    null -> PlaylistsScreen(
                        playlists = playlists,
                        favoritesCount = favoriteIds.size,
                        counts = countsById,
                        onOpen = { openedList = it },
                        onCreate = { name ->
                            scope.launch {
                                val id = playlistDao.createPlaylist(PlaylistEntity(name = name))
                                openedList = PlaylistTarget.Custom(id)
                            }
                        }
                    )
                    else -> PlaylistDetailScreen(
                        title = when (target) {
                            PlaylistTarget.Favorites -> "Favorites"
                            is PlaylistTarget.Custom -> playlists.firstOrNull { it.id == target.id }?.name ?: ""
                        },
                        tracks = openedTracks,
                        player = player,
                        listState = detailListState,
                        onBack = { openedList = null },
                        onDelete = if (target is PlaylistTarget.Custom) {
                            {
                                scope.launch {
                                    playlistDao.deletePlaylist(target.id)
                                    openedList = null
                                }
                            }
                        } else null,
                        onRemoveTrack = {track ->
                            scope.launch{
                                when (target){
                                    PlaylistTarget.Favorites -> favoriteDao.remove(track.id)
                                    is PlaylistTarget.Custom -> playlistDao.removeTrack(target.id, track.id)
                                }
                                showMessage("Removed \"${track.title}\"")
                            }
                        },
                        onRename = if (target is PlaylistTarget.Custom){
                            { newName -> scope.launch {playlistDao.rename(target.id, newName)}}
                        }else null,
                        // when over the sealed interface: every list type is covered, so the result is never null
                        onReorder = when (target){
                            PlaylistTarget.Favorites -> { ids -> scope.launch { favoriteDao.reorder(ids) } }
                            is PlaylistTarget.Custom -> { ids -> scope.launch {playlistDao.reorder(target.id, ids)}}
                        }
                    )
                }
                Tab.STORAGE -> FoldersScreen(allTracks = allTracks, library = library)
                Tab.DOWNLOADER -> ThemePickerScreen(settings = themeSettings) // TODO: temporary, move to Settings
            }
            actionTrack?.let { track ->
                TrackActionsDialog(
                    track = track,
                    isFavorite = track.id in favoriteIds,
                    playlists = playlists,
                    onToggleFavorite = {
                        toggleFavorite(track.id)
                        actionTrack = null
                    },
                    onAddTo = { playlist ->
                        actionTrack = null
                        scope.launch {
                            val added = playlistDao.addTrack(playlist.id, track.id)
                            showMessage(if (added) "Added to ${playlist.name}" else "Already in ${playlist.name}")
                        }
                    },
                    onDismiss = { actionTrack = null }
                )
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