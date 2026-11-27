package io.github.ginchanyo.yorozuya

// ".*" - wildcard import: everything from the package (remember, mutableStateOf, DisposableEffect, getValue/setValue...)
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// Holds everything the UI needs to know about playback.
// Screens read these fields and call the methods; none of them touch MediaController directly.
// @Stable - promise to Compose that changes of this object are always reported through state (mutableStateOf fields),
// so Compose can skip redrawing composables that receive it when nothing they read has changed
@Stable
class PlayerState (private val prefs: SharedPreferences){
    companion object {private const val KEY_ACTIVE_SOURCE = "active_source"}

    // "by mutableStateOf" works inside a class too, not only with remember: each field is observable on its own,
    // and a composable redraws only when a field it actually reads changes.
    // No remember here - the whole object is remembered once in rememberPlayerState()
    var controller: MediaController? by mutableStateOf(null)
    var currentTrackId: Long? by mutableStateOf(null)
    var isPlaying by mutableStateOf(false)
    var shuffleEnabled by mutableStateOf(false)
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
    // Playback position and track length in milliseconds, polled from the controller (see LaunchedEffect in rememberPlayerState below)
    var position by mutableStateOf(0L)
    var duration by mutableStateOf(0L)

    // Value is loaded from prefs on object creation, and saved in playQueue
    var activeSource: QueueSource? by mutableStateOf(queueSourceFromPrefString(prefs.getString(KEY_ACTIVE_SOURCE, null)))

    // Stores tracks id in the real player queue order
    var queueIds: List<Long> by mutableStateOf(emptyList())

    // Reads current state right after connecting, because listener only reports *future* changes:
    // without it, returning to the app showed nothing as playing until next track change
    fun syncFrom(p: Player){
        currentTrackId = p.currentMediaItem?.mediaId?.toLongOrNull()
        queueIds = readQueueIds(p)
        isPlaying = p.isPlaying
        shuffleEnabled = p.shuffleModeEnabled
        repeatMode = p.repeatMode
    }

    // Replaces the whole queue with given tracks and starts playing from startIndex
    fun playQueue(tracks: List<Track>, startIndex: Int, source: QueueSource){
        // "?: return" - not connected yet, nothing to control
        val mc = controller ?: return
        mc.setMediaItems(
            // map - LINQ Select: Track -> MediaItem
            tracks.map{
                // mediaId - our own label for the item (MediaStore id as text), so any
                // controller can tell which track is playing without relying on list indices
                MediaItem.Builder()
                    .setUri(it.uri)
                    .setMediaId(it.id.toString())
                    .build()
            },
            // which item to start from, and from which millisecond inside of it
            startIndex,
            0L
        )
        // prepare - start loading/decoding the file; play alone wouldn't start playback of a fresh queue
        mc.prepare()
        mc.play()
        activeSource = source
        prefs.edit()
            .putString(KEY_ACTIVE_SOURCE, source.toPrefString())
            .apply()
    }

    fun togglePlayPause(){
        val mc = controller ?: return
        if (mc.isPlaying) mc.pause() else mc.play()
    }

    // Tap on a track in any list: the playing one - pause/resume, another one - play queue starting from it
    // same list that is playing - jump to the track inside the current queue, so shuffled order is kept
    fun playOrToggle(track: Track, queue: List<Track>, source: QueueSource){
        val queueIndex = queueIds.indexOf(track.id)
        if (track.id == currentTrackId && source == activeSource) togglePlayPause()
        else if (source == activeSource && queueIndex != -1){
            val mc = controller ?: return
            mc.seekToDefaultPosition(queueIndex)
            mc.play()
        }
        else playQueue(queue, queue.indexOf(track), source)
    }

    // Mirrors a drag in a playlist onto the live queue, so playback follows the new order.
    // Only when the queue is exactly that playlist in its old order - otherwise it's some other
    // list (e.g. All tracks) and moving items there would scramble it
    fun moveInQueue(oldOrder: List<Track>, from: Int, to: Int){
        val mc = controller ?: return
        // == on lists compares contents element by element (like SequenceEqual in C#)
        if (queueIds == oldOrder.map { it.id }) {
            // moves the item inside the queue without interrupting the current track
            mc.moveMediaItem(from, to)
        }
    }

    // Single-expression bodies; "?." - call only if controller isn't null, otherwise do nothing
    fun next() { controller?.seekToNextMediaItem()}
    fun previous() {controller?.seekToPreviousMediaItem()}
    fun seekTo(ms: Long) {controller?.seekTo(ms)}

    // UI field is flipped right away so the button reacts instantly; the listener will confirm it later
    fun toggleShuffle(){
        shuffleEnabled = !shuffleEnabled
        controller?.shuffleModeEnabled = shuffleEnabled
    }

    // Off -> All -> One -> Off
    fun cycleRepeat(){
        // when - like C# switch expression, returns a value
        val next = when (repeatMode){
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        repeatMode = next
        controller?.repeatMode = next
    }
}

// Compose convention: "rememberXxx()" - a composable that creates an object once, keeps it across recompositions
// and ties its lifetime to the screen (same idea as rememberLazyListState()).
// It returns a value, so its name starts lowercase, unlike composables that draw UI
@Composable
fun rememberPlayerState(): PlayerState{
    val context = LocalContext.current
    val state = remember { PlayerState(context.getSharedPreferences("ui_state", Context.MODE_PRIVATE)) }

    // Player has no "position changed" callback (it would fire constantly), so position is polled twice a second.
    // Keys: restart when controller connects and on play/pause, so the value is fresh right after the change.
    // The loop ends by itself when the effect is cancelled (key change or screen leaves composition).
    LaunchedEffect(state.controller, state.isPlaying) {
        val mc = state.controller ?: return@LaunchedEffect
        while (true){
            state.position = mc.currentPosition
            // duration is C.TIME_UNSET (negative) until the track is prepared; coerceAtLeast clamps it to 0
            state.duration = mc.duration.coerceAtLeast(0L)
            // delay - suspends this coroutine only, the main thread keeps drawing (like await Task.Delay in C#).
            // 500.milliseconds - extension property turning Int into kotlin.time.Duration; clearer than bare 500
            delay(500.milliseconds)
        }
    }
    // DisposableEffect - is like a brother of LaunchedEffect, but with cleaning.
    // onDispose block executed when screen disappears. Controller holds a connection to the service - if not
    // released, it leaks. Anything that is required to close, is closed with DisposableEffect
    DisposableEffect(context){
        // Session token - services' address, thanks to which connection is available
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        // Establishes the connection async-ly: service may yet not start, that's why returns ListenableFuture -
        // promise of result, like Task in C#. That's why controller is MediaController? - at first few milliseconds it does not exist yet
        val future = MediaController.Builder(context, token).buildAsync()

        // addListener(callback, executor) - run callback when future is ready.
        // directExecutor - run it on whatever thread completed the future (here main thread), no thread switch
        future.addListener(
            {
                val mc = future.get()
                // object : Player.Listener { } - anonymous object implementing interface; only overridden methods react
                mc.addListener(object : Player.Listener{
                    override fun onIsPlayingChanged(playing: Boolean){
                        state.isPlaying = playing
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int){
                        state.currentTrackId = mediaItem?.mediaId?.toLongOrNull()
                    }

                    // Also fire when changed from outside the UI (e.g. restored by the service on start)
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        state.shuffleEnabled = shuffleModeEnabled
                    }

                    // state.repeatMode - field of state; plain repeatMode - the parameter (like this.x = x in C#)
                    override fun onRepeatModeChanged(repeatMode: Int) {
                        state.repeatMode = repeatMode
                    }
                    // Timeline is the whole queue, it activates at set, add, remove or move
                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        state.queueIds = readQueueIds(mc)
                    }
                })
                state.syncFrom(mc)
                state.controller = mc
            },
            MoreExecutors.directExecutor()
        )
        onDispose {
            MediaController.releaseFuture(future)
            state.controller = null
        }
        // MediaController implements same interface Player, that ExoPlayer does. That's why controller code - setMediaItems, play, pause, shuffleModeEnabled stays the same.
        // The only change is that commands go to different process, and do not execute locally. For that exact reason Media3 is set through shared interface
    }
    return state
}

// Human-readable name of a repeat mode, for button text and messages
fun repeatModeName(mode: Int): String = when (mode){
    Player.REPEAT_MODE_ALL -> "All"
    Player.REPEAT_MODE_ONE -> "One"
    else -> "Off"
}

// "0 until n" - range 0..n-1; mapNotNull turns each index into the mediaId stored in playQueue. Elements with non-numeric mediaId are skipped
fun readQueueIds(p: Player): List<Long> =  (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).mediaId.toLongOrNull() }