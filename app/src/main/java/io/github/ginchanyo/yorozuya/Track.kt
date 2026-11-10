package io.github.ginchanyo.yorozuya
import android.net.Uri
// Uri is the resource's address, just like URL, but more broad. It can point not only to a website, but
// rather anything inside a system. Since Android 10 app does not get direct access to /storage/.../music.mp3.
// Instead, system gives an address and uses it to give content, checking permissions.
data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Long
)