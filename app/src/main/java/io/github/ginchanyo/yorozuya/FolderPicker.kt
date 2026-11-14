package io.github.ginchanyo.yorozuya

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Folders for the picker, as (path, track count) pairs. Plain function, not @Composable - it only computes data.
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

@Composable
fun FolderPickerDialog(
    folders: List<Pair<String, Int>>,
    selected: Set<String>,
    includeSubfolders: Boolean,
    onToggleSubfolders: () -> Unit,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Directories with music") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSubfolders() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeSubfolders,
                        onCheckedChange = { onToggleSubfolders() }
                    )
                    Text("Search the subfolders as well")
                }
                HorizontalDivider()
                LazyColumn {
                    items(folders) { (path, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(path) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = path in selected,
                                onCheckedChange = { onToggle(path) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = path, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "$count tracks",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}