package io.github.ginchanyo.yorozuya

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

// MediaSessionService - ready template made by Google, that calls foreground-service and draws notification with buttons
// MediaSession - wrapper above player, that makes it accessible from outside: in system, via notification bar, using Bluetooth control and etc
// Method onGetSession answers the question "Which session to give to this client"
// Media3 marks some APIs as @UnstableApi (may change in future versions); opt in for the whole class
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    // lateinit - promise to compiler that value will exist, but not now, later in onCreate
    private lateinit var prefs: SharedPreferences
    // companion object - analog to "static" from C#
    companion object{
        // const val - compile-time constant (like C# const); only primitives and String allowed
        // PREFS_NAME is the name of the SharedPreferences file, KEY_* are keys inside it
        private const val PREFS_NAME = "playback_state"
        private const val KEY_QUEUE = "queue"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
    }

    // Saves queue (as MediaStore ids), current track index and position, so playback can resume after restart
    private fun saveState(){
        // "?: return" (Elvis operator) - if session is already null, there is nothing to save, exit the function
        val player = mediaSession?.player ?: return
        // 0 until n - range 0..n-1 (n excluded); map -> LINQ Select; result e.g. "12,57,103"
        val ids = (0 until player.mediaItemCount)
            .map {player.getMediaItemAt(it).mediaId}
            .joinToString(",")

        // edit() opens a batch of changes; apply() writes them to disk asynchronously (commit() would block)
        prefs.edit()
            .putString(KEY_QUEUE, ids)
            .putInt(KEY_INDEX, player.currentMediaItemIndex)
            .putLong(KEY_POSITION, player.currentPosition)
            .apply()
    }

    // Rebuilds the saved queue and puts player at saved track/position, paused (prepare without play)
    private fun restoreState(player : Player){
        // null is the default value if key doesn't exist yet (first launch)
        val saved = prefs.getString(KEY_QUEUE, null)
        if (saved.isNullOrEmpty()) return

        // Base URI of all audio files; id appended to it gives URI of one concrete track
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        // mapNotNull - map, but drops nulls; toLongOrNull() returns null for broken text instead of throwing,
        // and ?.let runs the block only when the value isn't null
        val items = saved.split(",").mapNotNull{
            idText-> idText.toLongOrNull()?.let{
                id -> MediaItem.Builder()
                    .setUri(ContentUris.withAppendedId(collection,id))
                    .setMediaId(id.toString())
                    .build()
            }
        }
        if (items.isEmpty()) return

        player.setMediaItems(items, prefs.getInt(KEY_INDEX, 0), prefs.getLong(KEY_POSITION, 0L))
        player.prepare()
    }

    // Custom command for the "Close" button. Its name (customAction) is how we recognize it in onCustomCommand
    private val stopCommand = SessionCommand("ACTION_STOP", Bundle.EMPTY)
    private var mediaSession: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            //  C.USAGE_MEDIA + AUDIO_CONTENT_TYPE_MUSIC tell the system this is music.
            //  Android uses that for volume routing and for how other audio
            //  (navigation prompts, notifications) gets mixed with yours.
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /*  handleAudioFocus = */ true)
            //  handleAudioFocus = true makes ExoPlayer manage audio focus for you.
            // It pauses on a phone call and lowers or pauses your music when another app plays.
            .setHandleAudioBecomingNoisy(true)
            // setHandleAudioBecomingNoisy(true) pauses playback when headphones are unplugged or Bluetooth disconnects.
            // Without it, the music would suddenly come out of the speaker.
            .build()
        // Intent - "message" describing what to open: here MainActivity
        val intent = Intent(this, MainActivity::class.java)
        // PendingIntent - Intent wrapped so the system (notification) can fire it later on our behalf
        // FLAG_IMMUTABLE is required since Android 12; FLAG_UPDATE_CURRENT reuses existing one, updating its data
        // "or" - bitwise OR in Kotlin (| in C#)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Notification button: stop icon + label; pressing it sends stopCommand to the session
        val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
            .setDisplayName("Close")
            .setSessionCommand(stopCommand)
            .build()
        mediaSession = MediaSession.Builder(this, player)
            // Callback decides which commands controllers may use and handles custom ones
            .setCallback(CallBack())
            // Extra buttons shown in the media notification
            .setMediaButtonPreferences(ImmutableList.of(stopButton))
            // What opens when user taps the notification
            .setSessionActivity(pendingIntent)
            .build()

        // SharedPreferences - small key-value storage in a private XML file; MODE_PRIVATE - only our app can read it
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // object : Player.Listener { } - anonymous object implementing interface (like anonymous class in Java)
        // Save on track change and on play/pause, so position is fresh even if process gets killed
        player.addListener(object : Player.Listener{
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                saveState()
            }
        })
        restoreState(player)
    }

    // inner class - nested class that holds reference to outer PlaybackService, so it can use stopCommand and stopSelf()
    // (plain nested class in Kotlin is like static nested class in C#, without access to outer instance)
    private inner class CallBack : MediaSession.Callback{
        // Called when a controller (our UI, notification, Bluetooth) connects: decide what it's allowed to do
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) : MediaSession.ConnectionResult{
            // Default commands + our stopCommand; without adding it, session would reject the button press
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(stopCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }
        // Called when a custom (non-standard) command arrives, e.g. our "Close" button
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand : SessionCommand,
            args : Bundle
        ) : ListenableFuture<SessionResult> {
            if (customCommand.customAction == stopCommand.customAction){
                // Stop playback, empty the queue (notification disappears), then shut down the service
                session.player.stop()
                session.player.clearMediaItems()
                stopSelf()
                // Result must be a ListenableFuture; Futures.immediateFuture wraps an already-ready value
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            // Unknown command - let default implementation handle it (it answers "not supported")
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        // Last save before the player is released (after release it can't be read anymore)
        saveState()
        // "run" yet another scope function like "let" but in it object becomes "this", that's why I'm writing plain
        // player and release() without prefix
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}