package io.github.ginchanyo.yorozuya

import android.net.Uri
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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