# Yorozuya DevLog

What was added or changed, what it does and how it works. Newest entries at the bottom.

---

## 2026-11-09 — Project setup

### Added
- **Compose project scaffold** with package `io.github.ginchanyo.yorozuya` (Kotlin + Jetpack Compose, AGP 9, Gradle version catalog in `gradle/libs.versions.toml`).
- **DECISIONS.md** records the stack (Media3, Room, Coil 3, youtubedl-android, LRCLIB, MusicBrainz), key decisions (yt-dlp over NewPipeExtractor, ACTION_SEND intent over WebView, Room override table instead of rewriting tags, album art search order, GPLv3) and the scope of version 0.1.
- `CLAUDE.md` was briefly removed from git tracking, then re-added on 2026-11-10. It stays tracked.

---

## 2026-11-10 — Reading music from the device and showing it as a list

### Added: `Track.kt` (data model)
```kotlin
data class Track(val id: Long, val uri: Uri, val title: String,
                 val artist: String?, val album: String?, val duration: Long)
```
- **What it is:** one audio file found on the phone.
- **`data class`:** Kotlin generates `equals()`, `hashCode()`, `toString()` and `copy()` automatically, which suits a class that only holds values.
- **All fields are `val` (immutable).** To change something, create a modified copy with `track.copy(title = "...")`. This fits the plan to store corrections in Room instead of changing the original.
- **`String?`** means the value may be null (artist and album are often missing). `title` is non-null because the loader supplies a fallback.
- **`Uri`** is the address of a resource, like a URL but broader: it can point to anything inside the system. Since Android 10, apps don't get direct file paths like `/storage/.../music.mp3`. The system hands out a `Uri` instead and checks permissions whenever content is read through it.

### Added: `TrackLoader.kt`, the `loadTracks(context)` function
Queries the system's media database and returns `List<Track>`.

**How it works (analogy with SQL):**
| MediaStore query part | SQL analog | Value used |
|---|---|---|
| `collection` (Uri) | table name | `MediaStore.Audio.Media.getContentUri(VOLUME_EXTERNAL)` |
| `projection` | `SELECT` columns | `_ID, TITLE, ARTIST, ALBUM, DURATION` |
| `selection` | `WHERE` | `IS_MUSIC != 0` (filters out ringtones, alarms, notification sounds) |
| `sortOrder` | `ORDER BY` | `TITLE ASC` |

- **MediaStore** is the system's own database of media files, and it can be treated as an SQL table.
- **Context** is the app's access point to the Android system: resources, system services, permission checks, and here the `ContentResolver`. `MainActivity` is itself a Context.
- **ContentResolver** is the middleman. An app can't touch another app's files directly. It asks the ContentResolver, which forwards the request to a **ContentProvider** (MediaStore is the system one). `contentResolver.query(...)` literally means "System, run this query on the audio table and give me a cursor." The same mechanism is used for contacts, calendar, etc.
- **Cursor** iterates over the result rows. `getColumnIndexOrThrow` finds each column's index once, then `while (cursor.moveToNext())` reads row by row.
- **`?.use { }`:** `query` may return null, and `?.` skips the block in that case. `use` closes the cursor automatically afterwards, like `using` in C# or RAII in C++.
- **Track Uri** is built with `ContentUris.withAppendedId(collection, id)` and is the "address" of the specific song, later used for playback.
- **Null safety:** `cursor.getString()` comes from Java, so Kotlin can't know whether it returns null. The title uses `?: "Unknown Name"` (the Elvis operator, "if null, use this") to avoid a crash on files without a title.

### Changed: `AndroidManifest.xml`
- Added `<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />`.
- This is a *dangerous* permission. Declaring it is required but not sufficient: the user must also grant it at runtime (see `TrackScreen` below).

### Changed: `app/build.gradle.kts`
- `minSdk` changed from 24 to **33**, matching DECISIONS.md. `READ_MEDIA_AUDIO` only exists since API 33, so older versions would need different permission code.

### Changed: `MainActivity.kt` (track list UI with runtime permission request)
The template `Greeting` was replaced by real screens:

- **`MainActivity.onCreate`** enables edge-to-edge, then `setContent { YorozuyaTheme { Scaffold { TrackScreen(...) } } }`. `innerPadding` from `Scaffold` keeps content clear of the system bars.

- **`@Composable`:** a function that *describes* what the screen should look like. It doesn't build it imperatively. When data changes, Compose calls the function again; this is **recomposition**.

- **`TrackScreen`** holds the permission logic and state:
  - `LocalContext.current` gets the Context inside a composable.
  - **State that survives recomposition:** a plain `var tracks = emptyList<Track>()` would be recreated (and lost) on every recomposition. `remember { ... }` keeps the value between calls.
  - **`mutableStateOf`** makes a value observable. Assigning a new value makes Compose redraw the places that read it, with no manual `updateUI()` needed.
  - **`by` (property delegation)** lets you write `tracks` instead of `tracks.value`. Pattern to remember: `var x by remember { mutableStateOf(...) }`. It needs both `getValue` and `setValue` imported from `androidx.compose.runtime`.
  - `hasPermission` starts from `ContextCompat.checkSelfPermission(...) == PERMISSION_GRANTED`.
  - **`rememberLauncherForActivityResult(RequestPermission())`** registers the system permission dialog. Its callback `{ granted -> hasPermission = granted }` updates state, which triggers recomposition.
  - **`LaunchedEffect`** is for actions that should run once, not on every redraw. Calling `launcher.launch(...)` directly in the body would show the dialog endlessly. The argument is a key:
    - `LaunchedEffect(Unit)` runs once when the screen appears and asks for permission if it isn't granted yet.
    - `LaunchedEffect(hasPermission)` runs again every time `hasPermission` changes, so tracks are loaded right after the user taps "Allow".
  - The UI shows `TrackList` if permission is granted, otherwise the text "Access to media on device is required".

- **`TrackList`** is a `LazyColumn` with `items(tracks)`. Like `RecyclerView`, it only composes the rows currently visible on screen, so large libraries stay fast.

- **`TrackRow`** is a `Column` with the title (`bodyLarge`, one line) and the artist (`bodySmall`, fallback "Unknown artist").

**Flow:** app start → `TrackScreen` checks permission → if missing, the dialog is shown → user allows → `hasPermission = true` → recomposition → `LaunchedEffect(hasPermission)` calls `loadTracks` → `tracks` updated → recomposition → list appears.

### Known issues / next steps
- `loadTracks` runs on the main thread (`LaunchedEffect` runs on the main dispatcher). Fine for now; for large libraries, move it to `Dispatchers.IO` (`withContext(Dispatchers.IO) { loadTracks(context) }`).
- Unused imports `Log` and `Preview` are left in `MainActivity.kt`.
- If the user denies permission, there's no way to ask again from the UI yet (e.g. a "Grant access" button).
- Next per 0.1 scope: playback with Media3, background service with notification, queue, shuffle.

---

## 2026-11-10 (later) — Playback on tap with ExoPlayer

### Added: Media3 ExoPlayer dependency
- `gradle/libs.versions.toml`: version `media3 = "1.11.0"`, library `androidx-media3-exoplayer`.
- `app/build.gradle.kts`: `implementation(libs.androidx.media3.exoplayer)`.
- In the version catalog, dashes in the alias become dots in Gradle code: `androidx-media3-exoplayer` → `libs.androidx.media3.exoplayer`.

### Changed: `MainActivity.kt`, tap a track to play / pause it

**New state in `TrackScreen`:**
- `val player = remember { ExoPlayer.Builder(context).build() }` creates the player once. `remember` keeps the same instance across recompositions, so a new player isn't built on every redraw.
- `var currentTrackId by remember { mutableStateOf<Long?>(null) }` is the id of the loaded track, or `null` if nothing has been played yet.
- `var isPlaying by remember { mutableStateOf(false) }` mirrors the player's play/pause state for the UI.

**`DisposableEffect(player)`, the sibling of `LaunchedEffect` with cleanup:**
- The body runs once and registers a `Player.Listener`. Its `onIsPlayingChanged(playing)` writes into `isPlaying`, so the UI updates whenever the player starts, pauses or finishes.
- `object : Player.Listener { override fun ... }` is an *object expression*: an anonymous class implementing an interface, like an anonymous class in Java or an inline interface implementation in C#.
- The `onDispose { }` block runs when the screen leaves composition. It removes the listener and calls `player.release()`. The player holds an audio decoder, which is a limited system resource. Without releasing it, sound keeps playing after the screen closes and the decoders eventually run out (a leak). Rule: anything that must be closed is closed in `DisposableEffect`.

**Click logic (`onTrackClick` passed to `TrackList`):**
```kotlin
if (track.id == currentTrackId) {
    if (player.isPlaying) player.pause() else player.play()   // same track: toggle
} else {
    player.setMediaItem(MediaItem.fromUri(track.uri))          // new track
    player.prepare()
    player.play()
    currentTrackId = track.id
}
```
- `MediaItem.fromUri(track.uri)` hands the player the content Uri from MediaStore. ExoPlayer reads it through the ContentResolver, so the permission check still applies.
- `prepare()` makes the player start loading and decoding. `play()` starts as soon as it's ready.
- **Bug fixed during development:** `currentTrackId = track.id` was missing at first. The id stayed `null`, so the comparison was always `false`: a second tap restarted the song instead of pausing it, and the highlight never appeared. Lesson: the player's internal state and the Compose state are separate. The UI only knows what you put into state yourself.

**State hoisting (passing state down, events up):**
- `TrackScreen` owns the state and the player. `TrackList` and `TrackRow` only receive values (`currentTrackId`, `isPlaying`) and a callback (`onTrackClick: (Track) -> Unit`, a function type: takes a `Track`, returns nothing).
- `TrackList` turns it into a per-row `onClick = { onTrackClick(track) }` and computes `isCurrent = track.id == currentTrackId`.

**`TrackRow` visuals:**
- `Modifier.fillMaxWidth().clickable(onClick).padding(...)`. Modifier order matters: `clickable` is placed before `padding`, so the whole row including the padding is tappable.
- The current track's title uses `MaterialTheme.colorScheme.primary`, the others `onSurface`.
- The title gets a `▶ ` prefix while the current track is playing.

**Flow:** tap a row → `onTrackClick(track)` → new track: set item, prepare, play, `currentTrackId` updated / same track: pause or resume → the player fires `onIsPlayingChanged` → `isPlaying` updated → recomposition → highlight and ▶ redrawn.

### Known issues / next steps
- **Tapping a track that finished playing does nothing.** After the end, the player is in `STATE_ENDED`, and `play()` doesn't restart from the beginning. Fix: `player.seekTo(0)` before `play()` when `player.playbackState == Player.STATE_ENDED`.
- **Rotating the screen stops playback.** Rotation recreates the Activity, `onDispose` releases the player, and `remember` state (`tracks`, `currentTrackId`) is lost. This is solved properly by moving the player into a Media3 `MediaSessionService`, which is also what background playback needs.
- No background playback or notification yet: playback is tied to the screen.
- From before: `loadTracks` still runs on the main thread, and the unused imports `Log` and `Preview` are still there.
- Next per 0.1 scope: background service with notification (`MediaSessionService`), queue, shuffle.
