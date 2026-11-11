package io.github.ginchanyo.yorozuya

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
// MediaSessionService - ready template made by Google, that calls foreground-service and draws notification with buttons
// MediaSession - wrapper above plater, that makes it accessible from outside: in system, via notification bar, using Bluetooth control and etc
// Method onGetSession answers the question "Which session to give to this client"
class PlaybackService : MediaSessionService() {

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