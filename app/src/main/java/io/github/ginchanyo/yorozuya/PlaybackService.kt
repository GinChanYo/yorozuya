package io.github.ginchanyo.yorozuya

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// MediaSessionService - ready template made by Google, that calls foreground-service and draws notification with buttons
// MediaSession - wrapper above plater, that makes it accessible from outside: in system, via notification bar, using Bluetooth control and etc
// Method onGetSession answers the question "Which session to give to this client"
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
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