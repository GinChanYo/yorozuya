package io.github.ginchanyo.yorozuya

import android.Manifest
import android.R
import android.content.ComponentName
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.github.ginchanyo.yorozuya.ui.theme.YorozuyaTheme
import kotlinx.coroutines.delay

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
fun TrackScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentTrackId by remember { mutableStateOf<Long?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var allTracks by remember { mutableStateOf(emptyList<Track>()) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var includeSubfolders by remember { mutableStateOf(false) }
    var repeatMode by remember {mutableStateOf(Player.REPEAT_MODE_OFF)}
    val prefs = remember { context.getSharedPreferences("library", Context.MODE_PRIVATE) }
    // Playback position and track length in milliseconds, polled from the controller (see LaunchedEffect below)
    var position by remember {mutableStateOf(0L)}
    var duration by remember { mutableStateOf(0L) }
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
    val listState =
        rememberLazyListState() // this object keeps scroll position and allows to control it
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
        fun syncFromPlayer(p: Player) {
            shuffleEnabled = p.shuffleModeEnabled
            currentTrackId = p.currentMediaItem?.mediaId?.toLongOrNull()
            isPlaying = p.isPlaying
            repeatMode = p.repeatMode
        }

        future.addListener(
            {
                val mc = future.get()
                mc.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
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
    LaunchedEffect(currentTrackId, tracks) {
        val index = tracks.indexOfFirst { it.id == currentTrackId }
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
    LaunchedEffect(controller, isPlaying) {
        val mc = controller ?:return@LaunchedEffect
        while (true){
            // Don't overwrite position while dragging, otherwise the thumb would jump back under the finger
            if (!isSeeking){
                position = mc.currentPosition
            }
            // duration is C.TIME_UNSET (negative) until the track is prepared; coerceAtLeast clamps it to 0
            duration = mc.duration.coerceAtLeast(0L)
            // delay - suspends this coroutine only, the main thread keeps drawing (like await Task.Delay in C#)
            delay(500)
        }
    }

    // firstOrNull searches list for track with matching ID and returns null if not found.
    // Searched in allTracks, not filtered tracks: the bar must stay even if the playing song is outside selected folders
    val currentTrack = allTracks.firstOrNull { it.id == currentTrackId }
    // Box - layout that stacks children on top of each other (later child is drawn above earlier one),
    // unlike Column/Row that place them one after another. Here: track list below, NowPlayingBar above it.
    // fillMaxSize - Box takes the whole screen, so its bottom edge is the screen bottom
    Box(modifier = modifier.fillMaxSize()) {
        // fillMaxSize - Column takes the whole Box, so the list below can stretch to the bottom
        Column(modifier = Modifier.fillMaxSize()) {
            // Row - buttons side by side in one line instead of stacked vertically
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        shuffleEnabled = !shuffleEnabled
                        controller?.shuffleModeEnabled = shuffleEnabled
                    }
                ) {
                    Text(if (shuffleEnabled) "Shuffle: on" else "Shuffle: off")
                }
                TextButton(onClick = { showFolderPicker = true }) {
                    Text("Directories")
                }
                TextButton(
                    onClick = {
                        val next = when (repeatMode){
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                        repeatMode = next
                        controller?.repeatMode = next
                    }
                )
                {
                    Text(
                        when (repeatMode){
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
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                listState = listState,
                onTrackClick = { track ->
                    val mc = controller
                        ?: return@TrackList // marked return: escapes lambda, not entire function. without mark Kotlin won't know where to return from
                    if (track.id == currentTrackId) {
                        if (mc.isPlaying) mc.pause() else mc.play()
                    } else {
                        val index = tracks.indexOf(track)
                        // tracks.map {MediaItem.fromUri(it.uri)} turns list of tracks into list of elements for player
                        // It's same Select from LINQ, and "it" - implicit name for parameter in lambda, when it's alone. Then index tells from which element to start,
                        // 0L - from which millisecond inside of it
                        mc.setMediaItems(
                            tracks.map {
                                // mediaId - our own label for the item (MediaStore id as text), so any
                                // controller can tell which track is playing without relying on list indices
                                MediaItem.Builder()
                                    .setUri(it.uri)
                                    .setMediaId(it.id.toString())
                                    .build()
                            },
                            index,
                            0L
                        )
                        mc.prepare()
                        mc.play()
                    }
                })
        }
        // Bar is shown only when something is selected; Compose simply doesn't draw it when condition is false
        if (currentTrack != null) {
            NowPlayingBar(
                track = currentTrack,
                isPlaying = isPlaying,
                onPreviousClick = {controller?.seekToPreviousMediaItem()},
                onNextClick = {controller?.seekToNextMediaItem()},
                onPlayPauseClick = {
                    val mc = controller ?: return@NowPlayingBar
                    if (mc.isPlaying) mc.pause() else mc.play()
                },
                // Slider works with 0..1 fraction; while dragging show the finger's value, otherwise the real one.
                // duration > 0 check avoids division by zero before the track is prepared
                progress = if (isSeeking){
                    seekPosition
                } else if (duration> 0){
                    position.toFloat() / duration
                } else 0f,
                // Time label follows the thumb during drag, so the user sees where they'll land
                positionMs = if (isSeeking) (seekPosition * duration).toLong() else position,
                durationMs = duration,
                // Called on every drag step: only remember the value, don't seek yet
                onSeekChange = { fraction ->
                    isSeeking = true
                    seekPosition = fraction
                },
                // Called once when the finger is released: one real seek. position is set right away,
                // so the thumb doesn't flash back to the old spot until the next poll
                onSeekFinished = {
                    if (duration > 0){
                        controller?.seekTo((seekPosition * duration).toLong())
                        position = (seekPosition * duration).toLong()
                    }
                    isSeeking =  false
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

// Folders for the picker, as (path, track count) pairs. Plain function, not @Composable - it only computes data.
// Without subfolders: only folders that directly contain tracks, biggest first.
// With subfolders: also every parent folder (e.g. "Music/" that has only subfolders), counting all tracks below it,
// sorted alphabetically so parents appear right above their children.
fun buildFolderList(tracks: List<Track>, includeSubfolders: Boolean): List<Pair<String, Int>> {
    if (!includeSubfolders) {
        return tracks.groupBy { it.relativePath }
            // (path, list) - destructuring: unpacks Map.Entry into two variables, like tuple deconstruction in C#
            .map { (path, list) -> path to list.size }
            .sortedByDescending { it.second }
    }
    // "Music/Rock/Live/" -> "Music/", "Music/Rock/", "Music/Rock/Live/"
    // flatMap - map where each element gives a list, and all lists are merged into one (SelectMany in LINQ)
    val allFolders = tracks.flatMap { track ->
        val parts = track.relativePath.split("/").filter { it.isNotEmpty() }
        parts.indices.map { i -> parts.take(i + 1).joinToString("/") + "/" }
    }.toSet() // toSet removes duplicates
    return allFolders
        .map { folder -> folder to tracks.count { it.relativePath.startsWith(folder) } }
        .sortedBy { it.first }
}

@Composable
fun FolderPickerDialog(
    folders: List<Pair<String, Int>>,
    selected: Set<String>,
    includeSubfolders: Boolean,
    onToggleSubfolders: () -> Unit,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Directories with music") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSubfolders() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeSubfolders,
                        onCheckedChange = { onToggleSubfolders() }
                    )
                    Text("Search the subfolders as well")
                }
                HorizontalDivider()
                LazyColumn {
                    items(folders) { (path, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(path) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = path in selected,
                                onCheckedChange = { onToggle(path) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = path, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "$count tracks",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}


// Mini player at the bottom of the screen: title, artist and Play/Pause button.
// It doesn't know about the controller: it only gets data and a callback (onPlayPauseClick),
// so the parent decides what a click does. This is "state hoisting" - common Compose pattern.
@Composable
fun NowPlayingBar(
    track: Track,
    isPlaying: Boolean,
    // () -> Unit - function type: takes nothing, returns nothing (like Action in C#)
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    // 0..1 position of the slider thumb
    progress: Float,
    // Shown as text under the slider, in milliseconds
    positionMs: Long,
    durationMs: Long,
    // (Float) -> Unit - function that takes the new slider fraction (like Action<float> in C#)
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    // Convention: every reusable composable accepts a modifier, so the caller can position it (e.g. align)
    modifier: Modifier = Modifier
) {
    // Surface - container with background color, shape and elevation.
    // tonalElevation - in Material 3 "higher" surfaces are tinted slightly, instead of only casting a shadow
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp
    ) {
        // Column - slider on top, time labels under it, then the row with title and buttons
        Column{
            // Slider - draggable bar; value must be in 0..1 by default (valueRange can change that).
            // onValueChange fires on every move, onValueChangeFinished once on release
            Slider(
                value = progress,
                onValueChange = onSeekChange,
                onValueChangeFinished = onSeekFinished,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            // SpaceBetween - first child at the left edge, last at the right edge, free space between them
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(positionMs), style = MaterialTheme.typography.labelSmall)
                Text(text = formatTime(durationMs), style = MaterialTheme.typography.labelSmall)
            }
            // Row - places children horizontally; CenterVertically aligns text and buttons on one middle line
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            )
            {
                // weight(1f) - take all width left after other children (the button), pushing button to the right edge.
                // Available only inside Row/Column scope, same idea as align in Box
                Column(modifier = Modifier.weight(1f)) {
                    // maxLines = 1 + Ellipsis - long title is cut to one line and ends with "…"
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // ?: (Elvis) - if artist is null, show fallback text
                    Text(
                        text = track.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onPreviousClick){Text("◀")}
                TextButton(onClick = onPlayPauseClick){Text(if (isPlaying)"⏸" else "▶")}
                TextButton(onClick = onNextClick){Text("▶▶")}
            }
        }
    }
}

// 83500 ms -> "1:23". Plain function, not @Composable - only formats data.
// "%d:%02d" - same format specifiers as printf in C: %02d pads seconds with a leading zero ("1:05", not "1:5")
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
fun TrackList(
    tracks: List<Track>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    // State is created in parent (TrackScreen) and passed down, so parent can scroll the list - state hoisting again
    listState: LazyListState,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        // contentPadding - empty space added after the last item (inside scrollable area, unlike Modifier.padding),
        // so last track can be scrolled out from under NowPlayingBar
        // 140.dp - bar grew with the slider and time labels; re-check on device if the bar changes again
        contentPadding = PaddingValues(bottom = 140.dp),
        modifier = modifier
    ) {
        items(tracks) { track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                onClick = { onTrackClick(track) })
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