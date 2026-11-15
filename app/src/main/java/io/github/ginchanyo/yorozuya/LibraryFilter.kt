package io.github.ginchanyo.yorozuya

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// Folder filter of the library: which folders are selected and whether their subfolders count too.
// Same idea as PlayerState: one object that holds the state and the only methods allowed to change it.
// Lives in AppScaffold, because two tabs need it - "Folders" changes it, "Queue" shows the filtered list.
// "private val prefs" in the constructor - declares a field and assigns it in one go (C# primary constructor)
@Stable
class LibraryFilter(private val prefs: SharedPreferences) {
    // Initial values are read from prefs once, when the object is created; after that the fields are the source of truth.
    // getStringSet returns Set<String>? (null if the key is missing), so "?: emptySet()" makes it non-null
    var selectedPaths by mutableStateOf(prefs.getStringSet("paths", emptySet()) ?: emptySet())
        private set // anyone can read the value, but only the class can change it
    var includeSubfolders by mutableStateOf(prefs.getBoolean("subfolders", false))
        private set

    // Set "+"/"-" return a NEW set, the old one isn't modified. Assigning a new object is what
    // mutableStateOf notices - changing the old set in place wouldn't trigger a redraw.
    // Saved on every toggle: there's no "Done" button anymore, the tab can be left at any moment
    fun togglePath(path: String){
        selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
        save()
    }

    fun toggleSubfolders(){
        includeSubfolders = !includeSubfolders
        save()
    }

    // Nothing selected - whole library. With subfolders - path starts with a selected folder ("Music/" matches
    // "Music/Rock/"). Without - exact folder match only.
    // "t ->" names the outer lambda's parameter, because the inner any { } already uses "it"
    fun filter(tracks: List<Track>) : List<Track> = when{
        selectedPaths.isEmpty() -> tracks
        includeSubfolders -> tracks.filter {t -> selectedPaths.any {t.relativePath.startsWith(it)}}
        else -> tracks.filter {it.relativePath in selectedPaths}
    }

    // Same keys as before the move into this class, so the filter saved by older versions still loads
    private fun save(){
        prefs.edit()
            .putStringSet("paths", selectedPaths)
            .putBoolean("subfolders", includeSubfolders)
            .apply()
    }
}

// Same pattern as rememberPlayerState(): create the object once and keep it across recompositions
@Composable
fun rememberLibraryFilter(): LibraryFilter {
    val context = LocalContext.current
    return remember {
        LibraryFilter(context.getSharedPreferences("library", Context.MODE_PRIVATE))
    }
}