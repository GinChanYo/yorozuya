package io.github.ginchanyo.yorozuya

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
            .setDisplayName("Close")
            .setSessionCommand(stopCommand)
            .build()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(CallBack())
            .setMediaButtonPreferences(ImmutableList.of(stopButton))
            .setSessionActivity(pendingIntent)
            .build()
    }

    private inner class CallBack : MediaSession.Callback{
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) : MediaSession.ConnectionResult{
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(stopCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand : SessionCommand,
            args : Bundle
        ) : ListenableFuture<SessionResult> {
            if (customCommand.customAction == stopCommand.customAction){
                session.player.stop()
                session.player.clearMediaItems()
                stopSelf()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
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