package io.github.ginchanyo.yorozuya

import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException


@Composable
fun rememberArtwork(uri: Uri, sizePx: Int = 512): ImageBitmap?{
    val context = LocalContext.current
    // produceState - bridge between async work and Compose. Coroutine launches inside and everything that is
    // assigned to value, becomes state that screen observes. null at first, then image. Key uri like in
    // LaunchedEffect: track changed, loading restarted
    return produceState<ImageBitmap?>(initialValue = null, uri){
        // withContext(Dispatchers.IO) - core of this step. Stream is blocking file from reading,
        // and it's done in main, interface freezes during load. Dispatchers.IO is a thread pool
        // specifically designed for waiting on disk and network operations;
        // withContext moves block there and returns result back.
        value = withContext(Dispatchers.IO){
            try {
                context.contentResolver
                    .loadThumbnail(uri, Size(sizePx, sizePx), null)
                    .asImageBitmap()
            } catch (e: IOException){
                null
            }
        }
    }.value
}

// Small rounded cover used in list rows and the mini player; "♪" while loading or when the file has no cover.
// size - how big it is on screen (dp); sizePx - how big the decoded image is. Keep sizePx close to the
// on-screen size in pixels: decoding a 512px cover for a 48dp box wastes memory
@Composable
fun ArtworkThumb(uri: Uri, size: Dp, sizePx: Int, modifier: Modifier = Modifier){
    val artwork = rememberArtwork(uri, sizePx)
    Box(
        // clip before background - otherwise the background would be drawn with square corners
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ){
        if (artwork != null) {
            // contentDescription = null - decorative image, screen readers skip it (title is read instead).
            // ContentScale.Crop - fill the box, trim what doesn't fit, keep proportions
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("♪")
        }
    }
}