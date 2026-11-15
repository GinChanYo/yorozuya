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

@Composable
fun ArtworkThumb(uri: Uri, size: Dp, sizePx: Int, modifier: Modifier = Modifier){
    val artwork = rememberArtwork(uri, sizePx)
    Box(
        modifier = modifier.run {
            size(size)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        },
        contentAlignment = Alignment.Center
    ){
        if (artwork != null) {
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