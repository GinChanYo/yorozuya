package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Folders for the "Folders" tab, as (path, track count) pairs. Plain function, not @Composable - it only computes data.
// Without subfolders: only folders that directly contain tracks, biggest first.
// With subfolders: also every parent folder (e.g. "Music/" that has only subfolders), counting all tracks below it,
// sorted alphabetically so parents appear right above their children.
fun buildFolderList(tracks: List<Track>, includeSubfolders: Boolean): List<Pair<String, Int>> {
    if (!includeSubfolders) {
        return tracks.groupBy { it.relativePath }
            // (path, list) - destructuring: unpacks Map.Entry into two variables, like tuple deconstruction in C#
            .map { (path, list) -> path to list.size }
            .sortedByDescending { it.second }
    }
    // "Music/Rock/Live/" -> "Music/", "Music/Rock/", "Music/Rock/Live/"
    // flatMap - map where each element gives a list, and all lists are merged into one (SelectMany in LINQ)
    val allFolders = tracks.flatMap { track ->
        val parts = track.relativePath.split("/").filter { it.isNotEmpty() }
        parts.indices.map { i -> parts.take(i + 1).joinToString("/") + "/" }
    }.toSet() // toSet removes duplicates
    return allFolders
        .map { folder -> folder to tracks.count { it.relativePath.startsWith(folder) } }
        .sortedBy { it.first }
}

// "Folders" tab (was a dialog on the Queue tab): checkbox list of folders that filters the Queue.
// It doesn't own the filter - it reads and changes the LibraryFilter that AppScaffold passed in,
// so the choice survives tab switches and Queue sees it right away
@Composable
fun FoldersScreen(
    allTracks: List<Track>,
    library: LibraryFilter,
    modifier: Modifier = Modifier
){
    // Rebuilt only when the library or the subfolders option changes, not on every redraw (e.g. checkbox tap)
    val folders = remember(allTracks, library.includeSubfolders){
        buildFolderList(allTracks, library.includeSubfolders)
    }

    Column(modifier = modifier.fillMaxSize()){
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable{library.toggleSubfolders()}
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ){
            Checkbox(
                checked = library.includeSubfolders,
                onCheckedChange = {library.toggleSubfolders()}
            )
            Text("Search the subfolders as well")
        }
        HorizontalDivider()
        // weight(1f) - list takes the height left under the checkbox row, so it scrolls instead of overflowing
        LazyColumn(modifier = Modifier.weight(1f)) {
            // (path, count) - destructuring the Pair. Needs the List overload of items(); if folders' type is
            // unknown (e.g. after an unresolved reference), Kotlin picks items(count: Int) and this fails
            items(folders) {(path, count)->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable{library.togglePath(path)}
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ){
                    Checkbox(
                        checked = path in library.selectedPaths,
                        onCheckedChange = {library.togglePath(path)}
                    )
                    Column(modifier = Modifier.weight(1f)){
                        Text(text = path, style = MaterialTheme.typography.bodySmall)
                        Text(text = "$count tracks", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}