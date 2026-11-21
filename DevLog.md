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
- This is a *dangerous* permission. Declaring it is required but not sufficient: the user must also grant it at runtime (see `QueueScreen` below).

### Changed: `app/build.gradle.kts`
- `minSdk` changed from 24 to **33**, matching DECISIONS.md. `READ_MEDIA_AUDIO` only exists since API 33, so older versions would need different permission code.

### Changed: `MainActivity.kt` (track list UI with runtime permission request)
The template `Greeting` was replaced by real screens:

- **`MainActivity.onCreate`** enables edge-to-edge, then `setContent { YorozuyaTheme { Scaffold { TrackScreen(...) } } }`. `innerPadding` from `Scaffold` keeps content clear of the system bars.

- **`@Composable`:** a function that *describes* what the screen should look like. It doesn't build it imperatively. When data changes, Compose calls the function again; this is **recomposition**.

- **`QueueScreen`** holds the permission logic and state:
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

**Flow:** app start → `QueueScreen` checks permission → if missing, the dialog is shown → user allows → `hasPermission = true` → recomposition → `LaunchedEffect(hasPermission)` calls `loadTracks` → `tracks` updated → recomposition → list appears.

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

**New state in `QueueScreen`:**
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
- `QueueScreen` owns the state and the player. `TrackList` and `TrackRow` only receive values (`currentTrackId`, `isPlaying`) and a callback (`onTrackClick: (Track) -> Unit`, a function type: takes a `Track`, returns nothing).
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

---

## 2026-11-11 — Queue (whole list as playlist) and shuffle

### Changed: `MainActivity.kt`, tapping a track now queues the whole list

**Before:** `setMediaItem(...)` loaded one song. When it ended, playback stopped.
**Now:** the whole track list becomes the player's queue, starting at the tapped track:
```kotlin
val index = tracks.indexOf(track)
player.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) }, index, 0L)
player.prepare()
player.play()
```
- `tracks.map { ... }` turns the list of `Track` into a list of `MediaItem`, the same as `Select` in LINQ.
- `it` is the implicit name of a lambda's parameter when it has exactly one.
- `setMediaItems(items, startIndex, startPositionMs)`: `index` says which item to start from, and `0L` is the position in milliseconds inside it (`L` makes it a `Long` literal).
- After the current song ends, ExoPlayer moves to the next item in the queue on its own.

### Added: tracking the current song via `onMediaItemTransition`
Since the player now switches songs by itself, the UI must learn about it. A second callback was added to the existing `Player.Listener`:
```kotlin
override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    currentTrackId = player.currentMediaItemIndex
        .let { tracks.getOrNull(it) }
        ?.id
}
```
- Reading the chain: take the index of the current item → get the track at that index (or `null` if out of range) → get its `id` (or `null`).
- `let { }` takes the value, passes it into the block as `it`, and returns the block's result. It's handy for continuing a chain.
- `getOrNull(i)` is a safe index: it returns `null` instead of throwing `IndexOutOfBoundsException`.
- `?.id` (safe call) reads `.id` only if the left side isn't `null`, otherwise the whole expression is `null`.
- This works with shuffle too: shuffle changes the playback *order*, not the item *indices*, so `currentMediaItemIndex` still matches the position in `tracks`.
- The listener is created once, yet still sees the newest `tracks`: because of `by`, every read of `tracks` is actually `tracks.value`, read at the moment the callback fires.
- `currentTrackId = track.id` in the click handler is now redundant (the transition callback also fires when playback starts), but harmless.

### Added: shuffle toggle
- New state `var shuffleEnabled by remember { mutableStateOf(false) }`.
- A `TextButton` above the list flips it and passes it to the player: `player.shuffleModeEnabled = shuffleEnabled`. In Kotlin, Java getter/setter pairs (`setShuffleModeEnabled`) look like properties.
- The label is computed from state: `if (shuffleEnabled) "Shuffle: on" else "Shuffle: off"`. `if` is an expression in Kotlin and returns a value, like the ternary `?:` in C++/C#.
- The button and the list are wrapped in a `Column`, so they're stacked vertically.

### Bug fixed during development: `Unresolved reference 'tracks'`
- The new listener used `tracks`, but `var tracks` was declared lower in the function. **A local variable exists only from its declaration line onward** (as in C++/C#). Class members can be declared in any order, local variables can't.
- The other errors ("Cannot infer type for type parameter 'R'", "Unresolved reference 'id'") were knock-on effects of the first one.
- Fix: `var tracks` moved up next to the other state, above `DisposableEffect`.

**Flow:** tap a row → queue = all tracks, start at the tapped one → song ends → the player moves to the next (or a random one if shuffle is on) → `onMediaItemTransition` → `currentTrackId` updated → recomposition → highlight moves to the new row.

### Known issues / next steps
- **Padding is applied twice:** both `Column(modifier = modifier)` and the `TrackList(..., modifier = modifier)` inside it receive the Scaffold `innerPadding`. Remove `modifier = modifier` from `TrackList`. Rule: a composable's `modifier` parameter goes only to its outermost element.
- After the **last** track in the queue ends, the player is in `STATE_ENDED`, and tapping that track does nothing (needs `seekTo(0)`).
- Rotating the screen still stops playback and resets state (fixed later by `MediaSessionService`).
- No background playback or notification yet.
- `loadTracks` still on the main thread; the unused imports `Log` and `Preview` remain.
- Next per 0.1 scope: background service with notification (`MediaSessionService`). Queue and shuffle are now done in basic form.

---

## 2026-11-11 (later) — Playback moved into `MediaSessionService`, UI controls it via `MediaController`

### Why
Until now the `ExoPlayer` lived inside `QueueScreen`. Playback was tied to the screen: closing the app or rotating the device released the player. Android's way to keep audio running without the UI is a **Service**, a component with no screen whose lifetime is separate from the Activity. Media3 provides a ready-made one: `MediaSessionService`.

New architecture:
```
MainActivity / TrackScreen  ──MediaController──▶  MediaSession  ──▶  ExoPlayer
        (UI, sends commands)                     (inside PlaybackService)
```

### Added: Media3 Session dependency
- `gradle/libs.versions.toml`: `androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }`.
- `app/build.gradle.kts`: `implementation(libs.androidx.media3.session)`.
- **Bugs fixed during development (sync error):**
  1. The alias was first `androidx-media-session`, which becomes `libs.androidx.media.session`. The build file asked for `libs.androidx.media3.session`, which didn't exist. The alias in the toml and the accessor in the build file must match (dashes become dots).
  2. The group was `androidx.media` (the old legacy library). Every Media3 module uses group `androidx.media3`. With the wrong group, Gradle can't download the library, so **every** line using its classes turns red, even if the code is correct.
  - Lesson: when nearly everything from one library is red, check the Gradle setup first. Library coordinates are `group:name:version`; compare new entries with ones that already work.
- To re-sync: "Sync Now" banner, the elephant icon, or File → Sync Project with Gradle Files (Ctrl+Shift+O).

### Added: `PlaybackService.kt`
```kotlin
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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
```
- **`MediaSessionService`** is a Service that owns a `MediaSession`. When playback starts, Media3 turns it into a *foreground service* and shows the media notification (play/pause, next) automatically.
- **`onCreate()`** runs once when the system starts the service. The `ExoPlayer` is created here now, not in the UI. `this` (the Service) is a Context, just like an Activity.
- **`MediaSession`** wraps the player and exposes it to the outside world: our UI, the notification, Bluetooth headset buttons, lock screen, Android Auto.
- **`onGetSession(controllerInfo)`** is called when a controller wants to connect. Returning the session accepts the connection; returning `null` would reject it. `controllerInfo` says who is connecting (useful later for filtering).
  - `= mediaSession` is an **expression body**: a one-line function without `{ return ... }`, like `=>` in C#.
- **`onDestroy()`** frees resources in reverse order: player first, then the session.
  - **`?.run { }`**: runs the block only if `mediaSession` isn't null. Inside the block, `this` is the session, so `player` and `release()` mean `mediaSession.player` and `mediaSession.release()`. (Compare with `let`, where the value is `it`.)
- `private var ... : MediaSession? = null`: nullable because the session doesn't exist before `onCreate` or after `onDestroy`.

**Bugs fixed during development:**
- **Name clash in imports:** both `android.media.session.MediaSession` (old framework class, added by auto-import) and `androidx.media3.session.MediaSession` were imported. Two classes with the same simple name make every `MediaSession` ambiguous. Fix: keep only the `androidx.media3` one. Watch which package you choose in the auto-import popup.
- **`super.onCreate(){ ... }`:** braces right after a call make a **trailing lambda**, a block passed as the last argument. `onCreate()` takes none, and the braces were also off by one, so `onGetSession` and `onDestroy` ended up *inside* `onCreate` ("override not allowed here"). Fix: `super.onCreate()` as a plain statement, and all overrides as siblings in the class.

### Changed: `AndroidManifest.xml`
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>

<service
    android:name=".PlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```
- Every Service must be declared in the manifest, or the system won't start it.
- **`FOREGROUND_SERVICE`** lets the app run a foreground service (one the user is aware of, through a notification). Background services get killed quickly; foreground ones survive.
- **`FOREGROUND_SERVICE_MEDIA_PLAYBACK`** + **`foregroundServiceType="mediaPlayback"`**: since Android 14, a foreground service must declare *what kind* of work it does, and each type needs its own permission. Both are "normal" permissions, granted automatically at install with no dialog.
- **`exported="true"`** + the **intent-filter** with the `MediaSessionService` action let *other* apps find and connect to the session: system media controls, Bluetooth, Android Auto, Wear OS.

### Changed: `MainActivity.kt`, the UI now talks to the service
**State:** `val player = remember { ExoPlayer.Builder(...) }` was replaced by
```kotlin
var controller by remember { mutableStateOf<MediaController?>(null) }
```
- Nullable because connecting takes time: during the first milliseconds there is no controller yet.
- It's `mutableStateOf` so that Compose recomposes when the controller arrives.

**Connecting, in `DisposableEffect(context)`:**
```kotlin
val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
val future = MediaController.Builder(context, token).buildAsync()
future.addListener({
    val mc = future.get()
    mc.addListener(object : Player.Listener { ... })
    controller = mc
}, MoreExecutors.directExecutor())
```
- **`ComponentName(context, PlaybackService::class.java)`** is the "address" of a component in your app: package + class name. `::class.java` gets the Java `Class` object of a Kotlin class (like `typeof(PlaybackService)` in C#).
- **`SessionToken`** identifies the session to connect to. If the service isn't running, connecting starts it.
- **`buildAsync()`** returns a `ListenableFuture<MediaController>`, a promise of a result like `Task<T>` in C#. The connection is asynchronous, so it doesn't block the UI thread.
- **`future.addListener(runnable, executor)`** runs the block when the future completes. Inside it, `future.get()` returns the result immediately because it's already done. (Calling `get()` earlier would block until it's ready.)
- **`MoreExecutors.directExecutor()`** (from Guava, which Media3 brings in) runs the callback on whatever thread completed the future. Media3 completes it on the main thread, so writing Compose state there is safe.
- The `Player.Listener` (`onIsPlayingChanged`, `onMediaItemTransition`) is now attached to the controller `mc` instead of the player. It works the same way.
- **`onDispose`**: `MediaController.releaseFuture(future)` releases the controller if it connected, or cancels the connection if it's still pending. Only the *connection* is closed; the player in the service keeps playing.
- The key changed from `player` to `context`: the effect restarts only if the context changes.

**Why the rest of the code barely changed:** `MediaController` implements the same `Player` interface as `ExoPlayer`. `setMediaItems`, `prepare`, `play`, `pause`, `isPlaying`, `shuffleModeEnabled` are all the same calls. The only differences:
- `player.` became `controller?.` (shuffle button) or `val mc = controller ?: return@TrackList` (track click).
  - **`?: return@TrackList`** means "if the controller is null, leave this lambda". `@TrackList` is a **label**: a bare `return` inside a lambda would try to return from `QueueScreen` itself, so you name the lambda to return from.
- Commands are no longer executed directly: the controller sends them to the session, and the session runs them on the real player. (Here the service runs in the same process as the UI, but it still communicates through the session's binder (IPC) channel, as if it were a different app.)

**Bug fixed during development:** `override fun onIsPlayingChaged` (a missing "n") was red: `override` requires an existing method with exactly that name and signature in the parent type, and `Player.Listener` has none, so it "overrides nothing". Tip: use **Ctrl+O** (Override Methods) inside `object : ... { }` to generate the correct signature.

- The unused `ExoPlayer` import was removed from `MainActivity.kt`.

**Flow:** screen appears → `DisposableEffect` builds a `SessionToken` → `buildAsync()` starts/binds `PlaybackService` → service `onCreate` creates ExoPlayer + MediaSession → `onGetSession` accepts → future completes → listener attached, `controller` set → recomposition → tap a track → `mc.setMediaItems(...)`, `play()` → session plays on ExoPlayer → notification appears → session reports back → `onIsPlayingChanged` / `onMediaItemTransition` → state updated → UI redrawn.

### Known issues / next steps
- **State isn't re-read on reconnect.** After rotation the controller reconnects, but `isPlaying` and `currentTrackId` start from their defaults until the next event. Fix: right after `future.get()`, set `isPlaying = mc.isPlaying` and compute `currentTrackId` from `mc.currentMediaItemIndex`. (`tracks`, held by `remember`, is also lost on rotation; a `ViewModel` would fix that.)
- **The service never stops on its own.** When the app is swiped away from recents, the service keeps running even if paused. Usual fix: override `onTaskRemoved` and call `stopSelf()` if the player isn't playing.
- The comment above `DisposableEffect` in `MainActivity.kt` still talks about the player's decoder. Now it's the controller *connection* that gets released there, and the decoder is released in `PlaybackService.onDestroy`.
- From before: double `modifier` padding on `TrackList`, `STATE_ENDED` after the last track, `loadTracks` on the main thread, unused `Log`/`Preview` imports.
- Next per 0.1 scope: check the notification and background playback on a device.

---

## 2026-11-11 (evening) — Audio attributes, audio focus and "becoming noisy"

### Changed: `PlaybackService.kt`, the player is now configured as a music player
```kotlin
val audioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .build()
val player = ExoPlayer.Builder(this)
    .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
    .setHandleAudioBecomingNoisy(true)
    .build()
```
- **`AudioAttributes`** (from `androidx.media3.common`) describes *what kind* of sound the app plays. `C` is Media3's class of constants.
  - `C.USAGE_MEDIA` + `C.AUDIO_CONTENT_TYPE_MUSIC` tell the system this is music. Android uses that for volume routing (the media volume stream) and to decide how other sounds, such as navigation prompts or notifications, are mixed with it.
- **Builder pattern:** each `.setX(...)` returns the builder itself, so calls can be chained, and `.build()` produces the final immutable object. ExoPlayer and MediaSession are built the same way.
- **`handleAudioFocus = true`**: *audio focus* is Android's way of letting apps take turns playing sound. With this flag, ExoPlayer requests focus when playback starts and reacts when it's lost. It pauses during a phone call, and it lowers the volume ("ducks") or pauses when another app starts playing.
- **`setHandleAudioBecomingNoisy(true)`** pauses playback when headphones are unplugged or Bluetooth disconnects. Without it, the music would suddenly come out of the phone speaker.
- **Why the `/* handleAudioFocus = */` comment:** `setAudioAttributes` is a Java method, and Kotlin doesn't allow named arguments when calling Java methods. So `setAudioAttributes(attrs, handleAudioFocus = true)` doesn't compile. An inline comment is the usual way to keep a bare `true` readable.

**How to check:** start a track, then play a video in another app (the music should pause or duck), or unplug the headphones (it should pause).

### Known issues / next steps
- Still open from before: state isn't re-read when the controller reconnects, the service never stops on its own (`onTaskRemoved`), the outdated comment above `DisposableEffect`, double `modifier` padding on `TrackList`, `STATE_ENDED` after the last track, `loadTracks` on the main thread, unused `Log`/`Preview` imports.
- Typo "plater" in the header comment of `PlaybackService.kt`.

---

## 2026-11-12 — "Close" button in the notification, opening the app from the notification, resuming playback after restart

### Added: `PlaybackService.kt`, "Close" button in the media notification
```kotlin
private val stopCommand = SessionCommand("ACTION_STOP", Bundle.EMPTY)

val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
    .setDisplayName("Close")
    .setSessionCommand(stopCommand)
    .build()
mediaSession = MediaSession.Builder(this, player)
    .setCallback(CallBack())
    .setMediaButtonPreferences(ImmutableList.of(stopButton))
    .setSessionActivity(pendingIntent)
    .build()
```
- **`SessionCommand`** is a custom command, identified by its name (`customAction`). Standard commands like play or next are built in, but for anything else you define your own.
- **`CommandButton`** is a button in the notification. It has an icon, a label and the command it sends when pressed. `setMediaButtonPreferences` passes the list of extra buttons to the session.
- **`MediaSession.Callback`** (the `CallBack` inner class) is where the session asks us what to do:
  - `onConnect` runs when a controller connects (our UI, the notification, Bluetooth). It returns the allowed commands: `DEFAULT_SESSION_COMMANDS` plus `stopCommand`. Without that addition the session would reject the button press.
  - `onCustomCommand` handles the command. It calls `stop()` and then `clearMediaItems()` (which removes the notification), followed by `stopSelf()`, which shuts down the service.
  - It must return a `ListenableFuture<SessionResult>` (an async result). `Futures.immediateFuture(...)` from Guava wraps a value that is already ready.
- **`inner class`**: a nested class that keeps a reference to the outer `PlaybackService`, so it can use `stopCommand` and `stopSelf()`. A plain nested class in Kotlin works like a `static` nested class in C#.

**Bug fixed during development:** I wrote `Future.immediateFuture` and got "Unresolved reference". The class is `Futures` (plural), from `com.google.common.util.concurrent`.

### Added: tapping the notification opens the app
```kotlin
val intent = Intent(this, MainActivity::class.java)
val pendingIntent = PendingIntent.getActivity(this, 0, intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
```
- **`Intent`** is a message that describes what to open. A **`PendingIntent`** wraps it so the system (here, the notification) can fire it later on the app's behalf. `setSessionActivity(pendingIntent)` connects it to the session.
- `FLAG_IMMUTABLE` has been mandatory since Android 12. `or` is Kotlin's bitwise OR (`|` in C#).

### `@OptIn(UnstableApi::class)` on `PlaybackService`
- Media3 marks some APIs with `@UnstableApi`, which means they work but may change in future versions. `setMediaButtonPreferences`, `DEFAULT_SESSION_COMMANDS` and `AcceptedResultBuilder` are among them.
- These are **lint** errors (red underlines in the IDE), not compiler errors. The Gradle build passed anyway.
- `@OptIn(UnstableApi::class)` on the class accepts the risk for everything inside it. The import must be `androidx.annotation.OptIn` (not `kotlin.OptIn`) because that's the one Media3's lint check looks for.
- Adding the same import twice is a compile error ("imported name is ambiguous").

### Fixed: `MainActivity.kt`, the app now shows the currently playing song when you return to it
- Every `MediaItem` now gets a **`mediaId`**, the track's MediaStore id as text: `MediaItem.Builder().setUri(...).setMediaId(it.id.toString())`. Any controller can now tell which track is playing from the item itself instead of looking it up by index in `tracks`.
- `onMediaItemTransition` reads the id directly: `mediaItem?.mediaId?.toLongOrNull()`.
- **`syncFromPlayer(mc)`** runs right after connecting and copies `currentTrackId` and `isPlaying` from the player. The listener only reports *future* changes, so without this the UI showed nothing as playing until the next event. This closes the "state isn't re-read on reconnect" issue from 2026-11-11.
- `syncFromPlayer` is a **local function**, a function declared inside another function (the same as C# local functions).

### Added: `PlaybackService.kt`, the queue and position survive a restart
- **`SharedPreferences`** is a small key-value store kept in a private XML file. `getSharedPreferences(PREFS_NAME, MODE_PRIVATE)` opens it, and `MODE_PRIVATE` means only this app can read it.
- **`saveState()`** stores the queue as comma-separated media ids (`"12,57,103"`), plus the current index and position. `prefs.edit()...apply()` writes asynchronously (`commit()` would block).
- **`restoreState(player)`** splits the string and rebuilds each `MediaItem` from `ContentUris.withAppendedId(collection, id)`. It then calls `setMediaItems(items, index, position)` and `prepare()` without `play()`, so the app starts paused at the saved spot.
- **When the state is saved:** on track change, on play/pause (both through a `Player.Listener`), and in `onDestroy` before the player is released.
- New Kotlin constructs:
  - `lateinit var` promises the compiler that the value will be set later (in `onCreate`) instead of in the constructor.
  - `companion object` holds class-level ("static") members. `const val` is a compile-time constant.
  - `?: return` (Elvis operator) exits the function when the left side is null.
  - `0 until n` is the range 0..n-1. `mapNotNull` is a `map` that drops nulls. `toLongOrNull()` returns null instead of throwing on bad input.

### Known issues / next steps
- **"Close" wipes the saved queue.** `clearMediaItems()` fires `onMediaItemTransition`, which calls `saveState()` with an empty queue, and `onDestroy` saves the empty state again. If Close should still allow resuming later, skip saving while closing (e.g. a `closing` flag) or don't clear the queue.
- **A deleted file breaks the restore.** If a track in the saved queue was deleted, its URI no longer resolves and the player errors on that item.
- The service still keeps running when the app is swiped away while paused (`onTaskRemoved`). The Close button gives a manual way out.
- Still open: the outdated comment above `DisposableEffect`, double `modifier` padding on `TrackList`, `STATE_ENDED` after the last track, `loadTracks` on the main thread, unused `Log`/`Preview` imports in `MainActivity.kt`. (The "plater" typo is already gone.)

---

## 2026-11-12 (later) — Now-playing bar, auto-scroll to the current track

### Added: `MainActivity.kt`, `NowPlayingBar` at the bottom of the screen
- It shows the current track's title and artist and has a Play/Pause button. It only appears while a track is selected (`if (currentTrack != null)`).
- `currentTrack = tracks.firstOrNull { it.id == currentTrackId }` finds the track that matches the id, or gives null.
- **Layout:**
  - **`Box`** stacks its children on top of each other. The list is drawn first and the bar on top of it.
  - `Modifier.align(Alignment.BottomCenter)` pins the bar to the bottom. `align` only exists inside `Box { }` (it's an extension available in `BoxScope`), which is why calling it outside the `Box` gave "Unresolved reference 'align'".
  - Inside the bar, a **`Row`** places items horizontally, and `Modifier.weight(1f)` on the text column takes the remaining width, which pushes the button to the right edge. `weight` only exists inside `Row`/`Column`, for the same reason as `align`.
  - **`Surface`** + `tonalElevation` gives the bar a background, slightly tinted the way Material 3 shows a raised surface.
  - `maxLines = 1` + `TextOverflow.Ellipsis` cuts long titles to one line ending in "…".
- **State hoisting:** the bar receives data plus a callback, `onPlayPauseClick: () -> Unit` (a function type, like C#'s `Action`). It doesn't know about the controller, so `QueueScreen` decides what a click does.

### Added: the list scrolls to the playing track
```kotlin
val listState = rememberLazyListState()
LaunchedEffect(currentTrackId, tracks) {
    val index = tracks.indexOfFirst { it.id == currentTrackId }
    if (index >= 0) listState.scrollToItem(index)
}
```
- **`LazyListState`** holds the list's scroll position and lets code scroll it. It's created in `QueueScreen` and passed down to `TrackList` (`LazyColumn(state = listState)`), so the parent controls the scrolling.
- **The effect has two keys.** It re-runs when the track changes *and* when the tracks finish loading. When you return to the app, `currentTrackId` can arrive before the list, and with only one key the index would be -1 and nothing would scroll.
- `scrollToItem` is a `suspend` function. A `LaunchedEffect` body is a coroutine, so it can be called there.

### Fixed
- **The bar no longer covers the last track.** `LazyColumn(contentPadding = PaddingValues(bottom = 72.dp))` adds empty space *inside* the scrollable area after the last item. `Modifier.padding` would shrink the list instead.
- **Double padding on `TrackList`:** `modifier = modifier` was removed from the call, so the Scaffold's `innerPadding` is applied once (by the `Box`).
- **Build errors along the way:**
  - The IDE's "Create parameter" quick-fix added a bogus `align` parameter to `QueueScreen` when `align` was used outside the `Box`.
  - "Optimize imports" removed `Row`'s import while the file didn't compile. That one missing import also produced "@Composable invocations can only happen…" and "Unresolved reference 'weight'".

### Known issues / next steps
- **The permission message is gone.** The `if (hasPermission) ... else Text("Access to media on device is required")` branch was removed, so if access is denied the user sees an empty list with only a Shuffle button.
- **Auto-scroll jumps when you tap a track.** `scrollToItem` always puts the item at the top of the list, so tapping a track in the middle of the screen moves it to the top. Options: scroll only when the item isn't visible (check `listState.layoutInfo.visibleItemsInfo`), and/or use `animateScrollToItem` for a smooth scroll.
- Still open from before: "Close" wipes the saved queue, the outdated comment above `DisposableEffect`, `STATE_ENDED` after the last track, `loadTracks` on the main thread, the unused `Preview` import (`Log` is gone).

---

## 2026-11-13 — Library filter by folder, layout fixes

### Added: `Track.kt` / `TrackLoader.kt`, each track's folder
- `Track` has a new field, `relativePath: String`. `loadTracks` now also reads the `MediaStore.Audio.Media.RELATIVE_PATH` column, for example `"Music/Rock/"`: the folder relative to the storage root, always ending in `/`. If the column is null, the track gets `""`.

### Added: `MainActivity.kt`, a "Directories" button and folder picker dialog (IDEAS.md #2)
- New state:
  - `allTracks` is everything MediaStore returned.
  - `selectedPaths: Set<String>` holds the folders the user ticked.
  - `includeSubfolders` holds the "Search the subfolders as well" checkbox.
  - `showFolderPicker` opens and closes the dialog.
- **`tracks` is now derived:** `remember(allTracks, selectedPaths, includeSubfolders) { ... }` recomputes the filtered list only when one of its keys changes:
  - no folders selected → all tracks
  - subfolders on → `relativePath.startsWith(folder)`
  - subfolders off → an exact folder match
- **`FolderPickerDialog`** is an `AlertDialog` with the subfolders checkbox, a `HorizontalDivider`, then a `LazyColumn` of folders, each with a `Checkbox` and its track count. It only gets data and callbacks (state hoisting again).
- **`buildFolderList(allTracks, includeSubfolders)`** is a plain (non-`@Composable`) function that only computes data:
  - With subfolders off, it lists the folders that directly contain tracks, biggest first.
  - With subfolders on, it also lists every parent folder. For example, `"Music/Rock/Live/"` produces `"Music/"`, `"Music/Rock/"` and `"Music/Rock/Live/"`, so a folder that only has subfolders can be picked too. Each one counts all tracks below it, and the list is sorted alphabetically so parents sit right above their children.
  - Kotlin used here:
    - `flatMap` maps each element to a list and merges the lists (LINQ `SelectMany`), and `toSet()` removes duplicates.
    - `(path, list) ->` is destructuring, which unpacks a map entry into two variables, like tuple deconstruction in C#.
    - `a to b` creates a `Pair`.
- **Persistence:** the dialog's `onDismiss` saves `"paths"` (`putStringSet`) and `"subfolders"` (`putBoolean`) to a `"library"` SharedPreferences file. Both are read back in `LaunchedEffect(hasPermission)` after the tracks load.

**Bugs fixed during development:**
- **"No parameter with name 'includeSubfolders'":** the dialog was called with, and used, parameters that weren't in its signature. A Kotlin function only sees its own parameters, not the variables at the place where it's called.
- **"Unexpected tokens":** a leftover `},` closed `text = { }` too early when the `LazyColumn` was wrapped in a new `Column`.
- **The divider was inside the checkbox `Row`:** a misplaced `}`. It compiled fine but drew a short line *next to* the text. The compiler can't catch layout mistakes like this one.

### Fixed: layout
- **The Shuffle and Directories buttons are side by side.** Before, they were stacked vertically, and there was a leftover empty `Row {}`. They're now inside one `Row(Modifier.fillMaxWidth())`.
- **The list fills the rest of the screen.** The `Column` got `fillMaxSize()` and the list got `Modifier.weight(1f)`, so it takes exactly the height left under the buttons.
- **Auto-scroll no longer jumps on tap.** The effect only scrolls if the playing track is off screen (`index !in listState.layoutInfo.visibleItemsInfo.map { it.index }`), and it uses `animateScrollToItem` for a smooth scroll. `return@LaunchedEffect` is a labeled return that exits only the effect's lambda.
- **The now-playing bar stays when the playing song is filtered out.** `currentTrack` is searched in `allTracks` instead of the filtered `tracks`.
- **The permission message is back:** if access is denied, the screen shows "Access to media on device is required" instead of the list.
- **Unused imports removed:** `Log`, `Preview`, and `isLiveLiteralsEnabled` (probably added by accident by auto-import).

### Known issues / next steps
- **Empty filter result:** if the selected folders no longer exist or contain no tracks, the list is just empty. It should show a hint like "No tracks in selected folders".
- **Two different "queue" ideas:** tapping a track now queues only the *filtered* list, while the service restores whatever queue was saved. That's fine for now, but keep it in mind for IDEAS.md #3 (play a directory as its own queue).
- **Folder counts are computed on every recomposition while the dialog is open.** `buildFolderList` loops over all tracks for each folder. That's fine for a few thousand songs; wrap it in `remember(allTracks, includeSubfolders)` if it gets slow.
- Still open: "Close" wipes the saved queue, the outdated "decoder" comment above `DisposableEffect`, `STATE_ENDED` after the last track, `loadTracks` on the main thread.

---

## 2026-11-13 (later) — Previous/Next, repeat mode, seek bar with time, centered auto-scroll

### Added: `MainActivity.kt`, Previous / Play-Pause / Next in `NowPlayingBar`
- The bar now has three buttons, `◀`, `▶`/`⏸` and `▶▶`. They call `seekToPreviousMediaItem()` and `seekToNextMediaItem()` on the controller.
- The callbacks are created in `QueueScreen` and passed in as parameters (state hoisting). The bar's buttons can only use the bar's own parameters. When the buttons were first placed in `QueueScreen`, `onPreviousClick` etc. were unresolved because those names only exist inside `NowPlayingBar`.

### Added: a repeat mode button (Off → All → One)
- `repeatMode` holds a `Player.REPEAT_MODE_*` constant. A `when` picks the next mode and the button's label, and the mode goes to `controller?.repeatMode`.
- `syncFromPlayer` reads `p.repeatMode` on connect, so the button shows the right mode when you return to the app.

### Added: a seek bar with current time / track length
- **Polling the position:** `Player` has no "position changed" callback, so a `LaunchedEffect(controller, isPlaying)` loop reads `currentPosition` and `duration` every 500 ms.
  - `delay(500)` comes from `kotlinx.coroutines` and needs an import. It suspends only this coroutine, like `await Task.Delay` in C#, so the UI keeps drawing.
  - The loop stops by itself when the effect is cancelled (a key changes or the screen leaves composition).
  - `duration` is negative (`C.TIME_UNSET`) until the track is prepared, so `coerceAtLeast(0L)` clamps it to 0.
- **`Slider`** works with a 0..1 fraction: `position / duration`, guarded with `duration > 0` against division by zero.
- **Dragging without jumping:**
  - `onValueChange` fires on every drag step and only stores `seekPosition` and sets `isSeeking = true`.
  - `onValueChangeFinished` fires once on release and does the real `seekTo`.
  - While `isSeeking` is true, the poll loop doesn't overwrite `position`. Without this, the thumb snapped back under the finger and the controller got flooded with seek calls.
- **Time labels:** a `Row` with `Arrangement.SpaceBetween` puts the current time on the left and the length on the right. While you drag, the left label follows the thumb.
- `formatTime(ms)` is a plain (non-`@Composable`) function that turns `83500` into `"1:23"`. `"%d:%02d".format(...)` uses printf-style specifiers, and `%02d` adds the leading zero (`1:05`).

### Changed
- **Auto-scroll centers the playing track** instead of putting it at the top. The offset is `-(viewportHeight / 2 - itemHeight / 2)`, read from `listState.layoutInfo`.
- **The folder list is cached:** `remember(allTracks, includeSubfolders) { buildFolderList(...) }`. This resolves the "computed on every recomposition" note from the previous entry.
- **`contentPadding` went from 72.dp to 140.dp**, because the bar grew with the slider and time labels.

### Bugs fixed during development
- **`index` underlined in the scroll effect:** the `val index = ...` line was deleted during a rewrite, and the IDE auto-imported Guava's `Multimaps.index` function to "resolve" the name. If the IDE suggests an import for something that should be a local variable, the variable is missing.
- **Buttons inside a button:** Previous/Next were first placed inside the Repeat button's content lambda, which is that button's *label*.
- **The Slider was inside the `Row`**, competing for width with the title and buttons. It was moved into the outer `Column`, above the row.

### Known issues / next steps
- The poll loop keeps running while paused (`while (true)`). A `do { ... } while (isPlaying)` would stop it after one last update.
- Still open: empty filter result hint, "Close" wipes the saved queue, the outdated comment above `DisposableEffect`, `STATE_ENDED` after the last track, `loadTracks` on the main thread.

---

## 2026-11-14 — Restore fixes, code split into files, `PlayerState`, bottom navigation, Now Playing tab

### Fixed: `PlaybackService.kt`, "Close" no longer forgets the current song
- **Cause:** the Close handler calls `stop()` and then `clearMediaItems()`. Clearing fires `onMediaItemTransition(null)` → `saveState()`, which saved an **empty** queue over the good one (and `onDestroy` saved it again).
- **Fix:** `if (player.mediaItemCount == 0) return` at the top of `saveState()`. The `stop()` just before already triggers `onIsPlayingChanged(false)`, which saves the full queue and position.

### Fixed: shuffle survives a full restart
- New key `KEY_SHUFFLE`: `saveState()` writes `putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)`.
- `restoreState()` sets `player.shuffleModeEnabled` **before** the empty-queue early return, so shuffle is restored even without a saved queue.
- The service listener overrides `onShuffleModeEnabledChanged` → `saveState()`. Without it, pressing Shuffle only got saved by accident on the next track change or play/pause.
- The UI needs nothing extra: `syncFrom` reads `shuffleModeEnabled` when the controller connects.

### Changed: code split into files
`MainActivity.kt` had grown to ~600 lines. It now holds only `MainActivity`, `QueueScreen`, `AppScaffold` and `Placeholder`. The rest moved out:
- `NowPlayingBar.kt` — mini player + `formatTime`.
- `TrackList.kt` — `TrackList` and `TrackRow`.
- `FolderPicker.kt` — `buildFolderList` and `FolderPickerDialog`.
- `PlayerConnection.kt` — `PlayerState` and `rememberPlayerState()` (below).
- All files share the package `io.github.ginchanyo.yorozuya`, so they see each other's top-level functions without imports.

### Added: `PlayerConnection.kt`, `PlayerState` + `rememberPlayerState()`
- **`PlayerState`** is a class that holds all playback UI state (`controller`, `currentTrackId`, `isPlaying`, `shuffleEnabled`, `repeatMode`, `position`, `duration`) as `by mutableStateOf` fields, plus methods: `playQueue`, `togglePlayPause`, `next`, `previous`, `seekTo`, `toggleShuffle`, `cycleRepeat`. Screens call these instead of touching `MediaController` directly.
  - `by mutableStateOf` works inside a class too: each field is observable on its own, so a composable redraws only when a field it reads changes.
  - **`@Stable`** promises Compose that every change goes through state fields, so it can skip redrawing composables that got this object when nothing they read changed.
- **`rememberPlayerState()`** follows the Compose naming convention for "create once, keep across recompositions" composables (like `rememberLazyListState()`). It returns a value, so its name starts lowercase.
  - It contains the `DisposableEffect` that connects the `MediaController` (moved from `QueueScreen`) and a listener that now also handles `onShuffleModeEnabledChanged` and `onRepeatModeChanged`.
  - The 500 ms position/duration polling loop moved here too, so it keeps running on any tab. `delay(500.milliseconds)` uses `kotlin.time.Duration` instead of a bare number.
  - The `isSeeking` check was dropped from the loop: both screens show `seekPosition` while dragging, so it doesn't matter if `position` updates underneath.

### Added: `Tabs.kt` + bottom navigation (`AppScaffold`)
- `enum class Tab(val label: String)` with `QUEUE`, `NOW_PLAYING`, `PLAYLISTS`, `STORAGE`, `DOWNLOADER`. An enum in Kotlin can carry constructor properties (`label`).
- `AppScaffold` draws a `Scaffold` with a `NavigationBar`. `Tab.entries.forEach` creates one `NavigationBarItem` per tab, and a `when (currentTab)` picks the screen. `when` over an enum is exhaustive, so no `else` is needed.
- Unimplemented tabs show `Placeholder("... - in development")`.

### Added: `NowPlayingScreen.kt`, the "Now" tab
- A square artwork placeholder (`aspectRatio(1f)`), title (2 lines, centered), artist, album, then a `Spacer(Modifier.weight(1f))` that pushes the slider, time labels and controls to the bottom.
- Controls: shuffle, previous, play/pause, next, repeat, all calling `PlayerState` methods. Emoji icons are written as surrogate pairs (`"🔀"`), because one emoji doesn't fit in a single UTF-16 `Char`.
- `track: Track?` — when nothing is playing, the screen shows "Nothing is playing" and `return`s early. After that check Kotlin **smart-casts** `track` to non-null.
- It has its own `isSeeking`/`seekPosition`: that's UI-only drag state of this screen, so it isn't in `PlayerState`.

### Changed: state hoisting into `AppScaffold`
- **Problem:** both tabs need the track list (`currentTrack` comes from it), but it lived inside `QueueScreen`. A tab's screen leaves composition when you switch away, so its `remember` values are lost — the list was reloaded from MediaStore on every return to Queue, and the polling loop stopped on the "Now" tab.
- **Fix:** `allTracks`, `hasPermission`, the permission `launcher` and `loadTracks` moved up to `AppScaffold`, and are passed down as parameters: `TrackScreen(player, allTracks, hasPermission)`, `NowPlayingScreen(player, currentTrack)`. Rule: **state goes down, events go up**.
- Parameters are read-only (`val`), so `QueueScreen` can't reassign `allTracks` — only the owner can.
- The folder filter stays in `QueueScreen` (only Queue needs it). It is read from prefs right in `remember { mutableStateOf(prefs.get...) }`; the `remember` block runs once each time the screen appears. `prefs` must be declared above those lines.

### Bugs fixed during development
- **`enabled` / `mode` unresolved in listener overrides:** Android Studio generated the overrides with the interface's parameter names (`shuffleModeEnabled`, `repeatMode`), but the bodies used other names. `state.repeatMode = repeatMode` is fine: field of `state` on the left, parameter on the right (like `this.x = x` in C#).
- **A second `PlayerState`:** `QueueScreen` received `player` as a parameter but still had `val player = rememberPlayerState()`, which hid the parameter and opened a second controller connection. Kotlin only warns about shadowing.
- **Strange `align` parameter added to `QueueScreen`:** broken braces put `Modifier.align(...)` outside the `Box`, and an IDE quick-fix "created" it as a parameter. Fixing the braces made it unnecessary. If a quick-fix invents a parameter, look for a scope error first.
- **`tracks.indexOf` without `(track)`:** a function reference is not a call.
- **NowPlayingScreen didn't compile:** `modifier: Modifier = modifier` (default pointed at itself, must be `= Modifier`), a missing comma after `.padding(24.dp)`, and missing imports (Alt+Enter → Import).
- **`remember` inside `LaunchedEffect`:** "@Composable invocations can only happen from the context of a @Composable function". A `LaunchedEffect` body is a coroutine, not a composable. And the `var` inside it would have been a new local variable that disappears when the block ends.
- **Placeholder comments pasted as code** (`var hasPermission by remember { /* moved ... */ }`) gave "Property delegate must have a getValue method": an empty `remember {}` returns `Unit`, which can't be a delegate.
- **Slider frozen:** when the polling loop moved, `state.position = mc.currentPosition` got deleted with the `isSeeking` check.

### Known issues / next steps
- **Notification shows "Yorozuya is running" for some songs:** `MediaItem`s have no `MediaMetadata`, so the notification relies on file tags. Untagged files (often the long-named ones) get no title. Fix: `.setMediaMetadata(MediaMetadata.Builder().setTitle(...).setArtist(...).build())` in `PlayerState.playQueue`, and look titles up in `restoreState` too. A scrolling ("marquee") title isn't possible — the system draws media notifications.
- **Repeat mode isn't saved across restarts** — same pattern as shuffle (`putInt` + `onRepeatModeChanged`).
- **Repeat icons on the "Now" tab:** Off shows 🔂 and One shows 🔁 — check that's intended.
- Still open: empty filter result hint, poll loop runs while paused, `STATE_ENDED` after the last track, `loadTracks` on the main thread.

---

## 2026-11-15 — Repeat mode survives a restart, comment cleanup

### Fixed: `PlaybackService.kt`, repeat mode is saved like shuffle
This resolves the "Repeat mode isn't saved" note from the previous entry. Same four steps as shuffle:
- New key `KEY_REPEAT = "repeat"`.
- `saveState()` writes `putInt(KEY_REPEAT, player.repeatMode)`.
- `restoreState()` sets `player.repeatMode = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)` before the empty-queue early return.
- The service listener overrides `onRepeatModeChanged` → `saveState()`.
- **Restore order is safe:** setting `repeatMode` (or shuffle) in `restoreState` fires the listener → `saveState()`, but the queue is still empty at that moment, so the `mediaItemCount == 0` guard returns early and nothing good gets overwritten.

### Bug fixed during development
- **"Null cannot be a value of a non-null type 'Int'":** `prefs.getInt(KEY_REPEAT, null)`. The second argument is the default for a missing key, and its type is `Int`, which can't be null in Kotlin (only `Int?` can, like `int` vs `int?` in C#). `getString(key, null)` works only because its default is `String?`. `getInt`/`getLong`/`getBoolean` always need a real value. `Player.REPEAT_MODE_OFF` (= 0) is used instead of a bare `0` so the meaning is clear.

### Cleanup
- Comments moved to the code they describe (mini-player description was on `TrackList`, the `DisposableEffect` and polling notes were left in `MainActivity`), placeholder comments replaced, new comments for `AppScaffold`, `Scaffold`, `Tab.entries`, `NowPlayingScreen`. Indentation fixed in `MainActivity.onCreate` and `QueueScreen`.

### Fixed: `MainActivity.kt`, Queue list no longer jumps when returning from another tab
- **Symptom:** going from "Now" back to Queue, the list started at the top and then animated down to the playing song, which looked like a reload.
- **Cause:** a tab's screen leaves composition when switched away, so `QueueScreen`'s `rememberLazyListState()` was recreated at position 0 with no knowledge of visible rows. The auto-scroll `LaunchedEffect` also starts again on every appearance, saw the current track as "not visible" and scrolled to it. (`allTracks` was already hoisted, so MediaStore wasn't actually re-queried.)
- **Fix:** same hoisting as `allTracks`. `val listState = rememberLazyListState()` moved to `AppScaffold` and is passed to `QueueScreen` as a `listState: LazyListState` parameter.
- **Why it's enough:** the `LazyListState` object keeps both the scroll position and its last `layoutInfo` (the rows that were visible). On return the list opens where it was, the effect runs but `index !in visibleItemsInfo` is false, so it doesn't scroll. If the song changed while on another tab, the new one isn't visible, so it still scrolls to it.
- If it still jumps on the device, the fallback is a "last scrolled-to track id" check inside the effect.

### Known issues / next steps
- Still open: notification title for untagged files (`MediaMetadata`), repeat icons on the "Now" tab, empty filter result hint, poll loop runs while paused, `STATE_ENDED` after the last track, `loadTracks` on the main thread.

---

## 2026-11-15 (later) — Embedded artwork: Now tab, track list, mini player

### Added: `Artwork.kt`
- `rememberArtwork(uri, sizePx = 512): ImageBitmap?` loads the cover embedded in the audio file with `contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)` and converts it with `asImageBitmap()`.
- **`produceState`** connects async work to Compose. It starts a coroutine, and whatever is assigned to `value` becomes state the screen observes: `null` first, then the image. `uri` is the key, so a track change restarts the load (same idea as `LaunchedEffect` keys).
- **`withContext(Dispatchers.IO)`** runs the blocking file read on a background thread pool made for disk/network waiting, so the UI doesn't freeze, and returns the result back.
- No cover (or an unreadable file) → `IOException` → `null`, and the caller draws a "♪" placeholder.
- `sizePx` asks for a small decode where a small picture is enough: 512 on the Now tab, 128 in list rows, 96 in the mini player. Decoding a full-size cover for a 48dp box would waste memory.
- `ArtworkThumb(uri, size, sizePx, modifier)` is a reusable small cover: a `Box` with `size`, `clip(RoundedCornerShape(4.dp))` and a `surfaceVariant` background, holding either the `Image` or "♪".

### Changed
- **`NowPlayingScreen`**: the square placeholder now shows the artwork. `Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, Modifier.fillMaxSize())`. `Crop` fills the square and trims the overflow instead of stretching.
- **`TrackRow`** (`TrackList.kt`): `Column` → `Row` of [48dp cover, 12dp `Spacer`, text `Column(Modifier.weight(1f))`]. `clickable` and padding stay on the outer `Row`, so the whole row is one tap target.
- **`NowPlayingBar`**: `ArtworkThumb(size = 40.dp, sizePx = 96)` before the title.
- Now tab buttons made consistent (`a249072`).

### Bugs fixed during development
- **`if/else` inside `Box(...)`:** the `if` was written inside the parentheses (parameters), and the `else` ended up after `)`. Content goes in the trailing lambda `Box(params) { content }`. The `modifier`/`contentAlignment` were lost in the same edit and restored.
- **`TrackRow` didn't compile:** a missing `}` for the new `Row`, missing imports (`Row`, `size`, `clip`, `background`, `Image`), and the inner `Column` still had the old outer modifiers (`fillMaxWidth`, `clickable`, `padding`), which would have taken the whole width and created a second tap target. It was replaced by `Modifier.weight(1f)`.
- `weight` exists only inside `Row { }`/`Column { }`: the content lambda has `RowScope` as receiver (lambda with receiver).

### Known issues / next steps
- **`TrackRow` duplicates `ArtworkThumb`:** its `Box`/`Image`/"♪" block is the same code. It can be replaced by `ArtworkThumb(track.uri, size = 48.dp, sizePx = 128)`, and then the unused imports can be removed.
- **`ArtworkThumb` uses `modifier.run { size(size)... }`:** it works, but `modifier.size(size).clip(...)` is the same thing, simpler.
- **"<unknown>" artist:** MediaStore returns the literal string `"<unknown>"` for untagged files, not `null`, so the `?: "Unknown Artist"` fallback never fires. It could be mapped to `null` in `loadTracks`.
- **No artwork cache:** a row that scrolls out and back reloads its thumbnail from disk. Coil (planned in DECISIONS.md) would cache it.
- Mini player buttons: `◀` vs `▶▶` — the Now tab uses `◀◀`.
- Still open: notification title for untagged files (`MediaMetadata`), empty filter result hint, poll loop runs while paused, `STATE_ENDED` after the last track, `loadTracks` on the main thread.

---

## 2026-11-15 (evening) — Cleanup after artwork, shared seek bar, CheatSheet

This resolves most of the "Known issues" from the previous entry (all except the artwork cache, which is planned with Coil).

### Added: `CheatSheet.md`
- A quick reference of everything used in the code so far, one line per item, grouped as Kotlin language → stdlib → coroutines → Android platform → Compose runtime/layout/modifiers → Material 3 → Media3/Guava → a map of the project's own files and functions.
- C# analogies where they help. To be extended whenever new APIs appear.

### Added: `SeekBar.kt` — `PlayerSeekBar(player, modifier)`
- **Problem:** the slider logic (`isSeeking`/`seekPosition`, progress fraction, "time label follows the thumb", one real seek on release) was copied in `QueueScreen` (for the mini player) and `NowPlayingScreen`, ~30 lines each. A fix like the "frozen slider" one would have had to be done twice.
- **Fix:** one composable that owns its drag state and draws `Slider` + time `Row`. Both screens call it.
- `progress` uses **`when` without a subject** instead of an `if / else if / else` chain.
- It has no padding of its own, the caller decides: the mini player passes `Modifier.padding(horizontal = 16.dp)`, the "Now" tab is already inside a padded `Column`.
- Each call site gets its own drag state, because `remember` belongs to that call. That's fine, since the two are never on screen together.

### Changed
- **`NowPlayingBar`**: five seek parameters (`progress`, `positionMs`, `durationMs`, `onSeekChange`, `onSeekFinished`) replaced by `player: PlayerState`, passed through to `PlayerSeekBar`. The buttons still use callbacks. "◀" → "◀◀" to match the "Now" tab.
- **`QueueScreen`**: its own `isSeeking`/`seekPosition` and the seek arguments are gone. It now gets `currentTrack: Track?` from `AppScaffold` instead of computing it again. The old local copy is deleted, otherwise it would **shadow** the parameter (Kotlin only warns).
- **`TrackRow`** uses `ArtworkThumb(uri, 48.dp, 128)` instead of a copy of its code. The leftover `val artwork = rememberArtwork(...)` was removed too: it was unused but still ran, so every row decoded its cover twice.
- **`ArtworkThumb`**: `modifier.run { size(size)... }` → `modifier.size(size)...`. `run` made `modifier` the `this` of the block, so it was a detour to the same call.
- **`loadTracks`**: `artist`/`album` go through `?.takeIf { it != MediaStore.UNKNOWN_STRING }`. MediaStore returns the text `"<unknown>"` for untagged files, not `null`. Now it becomes `null` once, at the source, and the `?: "Unknown Artist"` fallbacks in the screens finally work.
- Comments added or updated for the above.

### Bug fixed during development
- **Gradle broken by an IDE quick-fix:** an "add dependency" fix added `androidx.compose.runtime` with a hard-coded version `1.12.1`, and the edits also removed `compose-bom` from `libs.versions.toml` and both `platform(libs.androidx.compose.bom)` lines from `app/build.gradle.kts`. The Compose libraries are listed **without versions** because the **BOM (Bill of Materials)** supplies a matching set. Without it Gradle has no version to fetch, so the build fails. Both files were reverted with `git checkout --`, then Gradle sync.
- **Lesson:** if a quick-fix offers to *add a library* for something that worked before, the real fix is usually a missing `import`.

### Known issues / next steps
- `PlaybackService.kt:172`: `AcceptedResultBuilder(session)` is deprecated (compiler warning).
- `NowPlayingBar` takes both `player` and separate button callbacks. That works, but it's mixed. It could take only `track` + `player` later.
- Still open: artwork cache (Coil), notification title for untagged files (`MediaMetadata`), empty filter result hint, poll loop runs while paused, `STATE_ENDED` after the last track, `loadTracks` on the main thread.

---

## 2026-11-15 (night) — Folder filter moved to its own tab, `LibraryFilter`

### Added: `LibraryFilter.kt`
- `class LibraryFilter(private val prefs: SharedPreferences)` holds the folder filter: `selectedPaths` and `includeSubfolders`. Same idea as `PlayerState`: one object owns the state and the only methods allowed to change it (`togglePath`, `toggleSubfolders`).
- **`private val` in the constructor** declares the field and assigns it in one step (like a C# primary constructor).
- **`private set`**: anyone can read `selectedPaths`, but only the class can assign it. Screens can't bypass `save()`.
- Initial values are read from prefs `library` (keys `paths`, `subfolders`, same as before, so a filter saved earlier still loads).
- **Saved on every toggle** instead of on "Done", because the dialog is gone and the tab can be left at any moment.
- Set `+`/`-` return a **new** set. `mutableStateOf` notices a new object being assigned. Changing the old set in place wouldn't trigger a redraw.
- `filter(tracks)` is the old `when` from `QueueScreen`: nothing selected → all tracks; subfolders → `startsWith`; otherwise an exact folder match. The outer lambda names its parameter `t`, because the inner `any { }` already uses `it`.
- `rememberLibraryFilter()` follows the same pattern as `rememberPlayerState()`.

### Changed: the "Folders" tab replaces the dialog
- `FolderPickerDialog` (an `AlertDialog` opened by the "Directories" button on Queue) → `FoldersScreen(allTracks, library)` on the `Tab.STORAGE` tab. Same contents: the "subfolders" checkbox row, a divider, and a `LazyColumn(Modifier.weight(1f))` of folder checkboxes with track counts.
- `FoldersScreen` doesn't own the filter. It reads and changes the `LibraryFilter` it receives.
- **Hoisting again:** the filter is changed on one tab and used on another, so `library` and the filtered `tracks` live in `AppScaffold`. `tracks = remember(allTracks, library.selectedPaths, library.includeSubfolders) { library.filter(allTracks) }` is recomputed only when a key changes, not on every position poll.
- **`QueueScreen`** gets `tracks` (already filtered) instead of `allTracks`, and knows nothing about folders. Its prefs, filter state, `folders`, "Directories" button and dialog are all gone. `currentTrack` is still looked up in `allTracks`, so the bar stays when the playing song is outside the selected folders.
- Comments added or updated in `LibraryFilter.kt`, `FolderPicker.kt` and `MainActivity.kt`. The unused `import android.content.Context` was removed from `MainActivity.kt`.

### Bugs fixed during development
- **`empty()`** doesn't exist. An empty set is `emptySet()`.
- **`getSharedPreference`** → `getSharedPreferences` (plural).
- **`FolderPicker.kt`: "Unresolved reference 'remember'" + "Destructuring of type 'Int' requires operator function 'component1()'".** Only the first error was real: a missing `import androidx.compose.runtime.remember`. Because `remember` was unknown, the type of `folders` was unknown too, so Kotlin picked the other overload `items(count: Int)` instead of `items(list)`, and `{ (path, count) -> }` tried to unpack an `Int`. **Fix the first error and recompile: later ones are often side effects.**

### Known issues / next steps
- `FolderPicker.kt` now holds `FoldersScreen`, not a picker. Rename it to `FoldersScreen.kt` (Shift+F6).
- Still open: `AcceptedResultBuilder(session)` deprecation, artwork cache (Coil), notification title for untagged files (`MediaMetadata`), empty filter result hint, poll loop runs while paused, `STATE_ENDED` after the last track, `loadTracks` on the main thread.

---

## 2026-11-15 (late night) — Tab icons, 0.1 closed in DECISIONS.md

### Changed: `DECISIONS.md` / `IDEAS.md`
- **Version 0.1 marked done.** All five planned items are ticked, plus a "Done beyond the plan" list: IDEAS #2, #4, #5 and the features that came up along the way.
- **Plan revised, because the order shifted.** Cover art was started right after 0.1, so:
  - **0.2** = library and cover art (in progress)
  - **0.3** = synced lyrics (+ manual offset)
  - **0.4** = metadata override table + online cover art
  - YouTube downloading moved to **0.5**; playlists are postponed until Room arrives.
- "Album art search order" step 1 now matches the code: `loadThumbnail` instead of `MediaMetadataRetriever.embeddedPicture`. It decodes straight to the needed size and needs no retriever object.
- In `IDEAS.md` the done ideas are replaced by *"Done in 0.1 → DECISIONS.md"* instead of being deleted. DevLog refers to ideas by number, and deleting lines would renumber the list.

### Added: icons in the bottom navigation
- Five vector drawables in `res/drawable/` (`ic_queue`, `ic_playing`, `ic_playlists`, `ic_folder`, `ic_download`): Material icons added via Vector Asset, Apache 2.0 (compatible with GPLv3).
- `enum class Tab(val label: String, @param:DrawableRes val icon: Int)`. Each tab now carries its icon.
- **`R.drawable.ic_queue` is an `Int`.** Android generates an ID number for every resource in `res/`. The ID is not the image: `painterResource(id)` loads it, and `Icon(painter, contentDescription)` draws it, tinted with the theme color (selected/unselected colors come from `NavigationBarItem`).
- **`@DrawableRes`** marks the `Int` as a drawable ID, so Android Studio flags a wrong kind of ID (for example a string ID).
- `label = null` stays, so only icons are shown. `tab.label` becomes `contentDescription`, which screen readers announce.

### Bugs fixed during development
- **"Too many arguments for 'constructor(label: String): Tab'":** the enum values got a second argument (`R.drawable...`), but the constructor still had only `label`. Enum values are constructor calls, so the parameter has to be declared.
- **"Unresolved reference 'Icon'":** missing `import androidx.compose.material3.Icon`. The second error, "Unresolved reference 'icon'", came from the enum, not from the import.
- **Warning "This annotation is currently applied to the value parameter only, but in the future it will also be applied to field":** a `val` in a constructor is both a parameter and a property, so the annotation's target is ambiguous. The **use-site target** `@param:DrawableRes` states it explicitly.

### Known issues / next steps
- Four of the five icon files are untracked in git (only `ic_download.xml` was staged): `git add app/src/main/res/drawable/` before committing.
- Still open: see DECISIONS.md, version 0.2.

---

## 2026-11-15 (late night, 2) — Message bubble for shuffle/repeat changes

### Added: Snackbar "Shuffle: on" / "Repeat: All" on both tabs
- **`AppScaffold`** owns the bubble:
  - `val snackbarHostState = remember { SnackbarHostState() }` holds the currently shown message.
  - `val scope = rememberCoroutineScope()`: `showSnackbar` is a `suspend` function (it waits until the message disappears), so it needs a coroutine. **`rememberCoroutineScope` vs `LaunchedEffect`:** an effect starts its coroutine by itself when the screen appears; a scope lets *you* start coroutines later, for example from a click. Both are cancelled when the composable leaves.
  - `val showMessage: (String) -> Unit = { text -> ... }` is a lambda stored in a variable and passed down to `QueueScreen` and `NowPlayingScreen` as a parameter. State goes down, events go up.
  - `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) { data -> ... } })`. Scaffold places it above the bottom bar, centered.
- **Small pill instead of the default full-width Snackbar:** `SnackbarHost`'s trailing lambda draws each message (`data.visuals.message`) as `Surface(shape = RoundedCornerShape(50), color = inverseSurface, contentColor = inverseOnSurface) { Text(bodySmall, padding 16/8 dp) }`.
  - No width modifier, so the pill is only as wide as its text.
  - `RoundedCornerShape(50)` = 50% of the height, so the ends are fully round.
  - `contentColor` colors the `Text` inside automatically.
- **1.5 s instead of ~4 s:** `SnackbarDuration` has only `Short`/`Long`/`Indefinite`. So `showSnackbar(text, SnackbarDuration.Indefinite)` runs in a child `launch`, the parent waits `delay(1500)` and then calls `job.cancel()`. Cancelling the coroutine that waits in `showSnackbar` removes the bubble.
- **Fast taps:** `currentSnackbarData?.dismiss()` before each new message replaces the bubble instead of queuing. A stale coroutine's `job.cancel()` only affects its own, already finished job, so it can't close the newer bubble.
- **`repeatModeName(mode)`** in `PlayerConnection.kt` is one place for "Off"/"All"/"One". It's used by the Queue repeat button text (replacing its `when`) and by both messages.
- On the Now tab the buttons keep their emoji, while the message uses words.
- `import androidx.media3.common.Player` was removed from `MainActivity.kt` (no longer used there).

### Bugs fixed during development
- **Typo `showSnackBar` (capital B) + an IDE quick-fix:** "Create parameter" added `showSnackBar: SnackbarHostState.(String) -> Unit` to `AppScaffold()`, which broke the call in `onCreate`. That type is a **function type with a receiver** (called *on* a `SnackbarHostState`, like `RowScope` in `Row { }`). Fix: restore `AppScaffold()` and spell it `showSnackbar`. Same trap as the invented `align` parameter earlier: **if a quick-fix creates a parameter for something that should already exist, look for a typo.**
- **`showMessage(...)` in the button's content lambda instead of `onClick`:** it compiled (the content lambda accepts any code), but the buttons would have lost their labels and the bubble would fire on every recomposition. **`onClick` = what happens (once, on tap); trailing `{ }` = what is drawn (on every recomposition).**
- **Message shown twice:** the old `snackbarHostState.showSnackbar(text)` stayed after the new delay/cancel code. Code in a coroutine runs top to bottom, so after the 1.5 s pill was cancelled, the same text appeared again for ~4 s.

### Known issues / next steps
- On Queue the pill briefly covers the mini player (both at the bottom).
- Still open: see DECISIONS.md, version 0.2.

---

## 2026-11-16 — Room database, favorites, `QueueScreen` split

### Added: Room 3 (database)
- **Gradle:** `room3-runtime`, `room3-compiler` (through **KSP**, a compiler plugin that generates code from annotations at build time), and `sqlite-bundled` in `libs.versions.toml`. The plugins `com.google.devtools.ksp` and `androidx.room3` are declared in both build files.
- `room3 { schemaDirectory("$projectDir/schemas") }` makes Room export the table layout to `app/schemas/.../1.json`. That file is committed: future migrations (version 2+) are checked against it.
- **`FavoriteEntity.kt`:** `@Entity(tableName = "favorites") data class FavoriteEntity(@PrimaryKey val trackId: Long, val addedAt: Long = System.currentTimeMillis())`. One row = one favorite track. The MediaStore id is the key, so a track can't be added twice.
- **`FavoriteDao.kt`:** `@Dao interface` with functions that have **no body**. Room reads the annotation + signature and KSP generates the implementation.
  - `observeIds(): Flow<List<Long>>`. A `Flow` is re-emitted by Room every time the table changes, so it isn't `suspend`.
  - `suspend fun add(...)` with `@Insert(onConflict = IGNORE)`, `suspend fun remove(trackId)` with `@Query("DELETE ...")`. One-off calls are `suspend` and run in a coroutine.
  - `observeIdsNewestFirst()` (`ORDER BY addedAt DESC`) has been added but isn't used yet.
- **`YorozuyaDatabase.kt`:** `@Database(entities = [FavoriteEntity::class], version = 1) abstract class ... : RoomDatabase()`.
  - **Singleton:** `get(context)` uses `@Volatile` + `synchronized` (double-checked locking, the same as in C#), so the whole app shares one database object.
  - `Room.databaseBuilder<...>(context, name).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()`. The bundled driver ships its own SQLite instead of using the device's copy, and queries run on the I/O thread pool.

### Added: favorite button on the "Now" tab
- `NowPlayingScreen` got `isFavorite: Boolean` and `onToggleFavorite: () -> Unit`, and shows an `IconButton { Icon(painterResource(ic_favorite / ic_favorite_border)) }` above the seek bar. The screen doesn't know about the database: data comes down, the event goes up.
- **`AppScaffold`:**
  - `favoriteDao` is created once with `remember`.
  - `favoriteIds by remember { favoriteDao.observeIds() }.collectAsState(initial = emptyList())`. **`collectAsState`** turns a `Flow` into Compose `State`, so a database change recomposes the UI. The `remember` around the `Flow` is needed so that a new query isn't started on every recomposition.
  - `toggleFavorite` uses `scope.launch { }` to call `add`/`remove` (they are `suspend`), then `showMessage(...)`.
- Two new Material icons in `res/drawable/`: `ic_favorite.xml`, `ic_favorite_border.xml`.

### Added: `FavoriteScreen.kt` (temporary, on the Playlists tab)
- `favoriteTracks = remember(allTracks, favoriteIds) { val byId = allTracks.associateBy { it.id }; favoriteIds.mapNotNull { byId[it] } }`.
  - **`associateBy`** builds a `Map` id → track (`ToDictionary`). This avoids searching the whole list for every id.
  - **`mapNotNull`** drops ids whose file is gone from the device.
- `FavoriteScreen(player, tracks, listState)` shows a hint when the list is empty, otherwise a `TrackList`. It has its own `favoriteListState`, so its scroll position is independent of Queue.
- Later the Playlists tab will hold real playlists, and favorites will become one of them.

### Changed: refactoring
- **`TrackScreen` → `QueueScreen`**, moved from `MainActivity.kt` to its own file. `MainActivity.kt` keeps only `MainActivity`, `AppScaffold` and `Placeholder`. Unused imports were removed.
- **`PlayerState.playOrToggle(track, queue)`:** the "tap the playing track → pause, another one → play the list from it" logic. It's shared by Queue and Favorites instead of being duplicated in two lambdas.
- **`TrackList(bottomPadding: Dp = 0.dp)`:** the 140.dp space under the last item was hard-coded. Now only `QueueScreen` passes it, because only Queue has the mini player overlay.
- Imports in `PlaybackService.kt` and `PlayerConnection.kt` are sorted, and the wildcard `androidx.compose.runtime.*` is replaced with explicit imports.

### Bugs fixed during development
- **"Could not find androidx.room:room3-compiler:3.0.3":** wrong **group** in the catalog. Room 3 is published as `androidx.room3:room3-*`. Gradle turns `group:artifact:version` into a folder path on the repository, so one wrong word means a 404. The "Configuration cache state could not be cached" line above the error was just a side effect.
- **`isFavorite = isCurrentFavorite` in the parameter list:** that's call syntax (a named argument). A declaration needs `name: Type`. The values are passed at the call site in `AppScaffold`.
- **"Unresolved reference 'IconButton'/'Icon'" + "@Composable invocations can only happen from...":** missing imports. The second error was a side effect of the first.
- **`YorozuyaDatabas`** typo, and a missing `import androidx.compose.runtime.collectAsState`.
- **"Syntax error: Expecting member declaration" in `FavoriteDao.kt`:** a `@Query` without a function under it. An annotation must be attached to a declaration.

### Known issues / next steps
- `MainActivity` still collects `observeIds()` (unordered), so `observeIdsNewestFirst()` isn't used yet. Switch to it, or put `ORDER BY` into `observeIds()`.
- Empty Favorites hint says "Song tab"; the tab is called "Now".
- `toggleFavorite` decides add/remove by `favoriteIds`, which updates only after Room emits. Two very fast taps can both see the old value.
- Stale comments: `PlayerConnection.kt:3` (wildcard import, now gone), `TrackList.kt:40` (140.dp now comes from the caller), `MainActivity.kt:127` (Scaffold comment is now above the favorites code), `MainActivity.kt:69` ("suspended" → the keyword is `suspend`).


---

## 2026-11-17 — Background auto-scroll, playlists

### Changed: auto-scroll to the playing track now works from any tab
- **Problem:** with shuffle on, after a track change on another tab, returning to Queue showed the list visibly scrolling to the new song. `when (currentTab)` keeps only the selected tab in composition, so the `LaunchedEffect` inside `QueueScreen` didn't exist on other tabs. It started only on return, and then ran `animateScrollToItem`.
- **Fix:** the effect moved from `QueueScreen` to `AppScaffold`, which is always composed. `listState` already lived there, so it survives tab switches.
  - Keys are still `(player.currentTrackId, tracks)`. `currentTab` is **not** a key (otherwise every tab switch would re-run the effect), it is only read inside. Because it is declared with `by remember { mutableStateOf(...) }`, the lambda captures the `State` object and always reads the current value.
  - `if (index in visibleItemsInfo...) return@LaunchedEffect` - skip if the track is already on screen, so the list doesn't jump on every change.
  - On Queue: `animateScrollToItem(index, offset)` (the user sees the list, so it scrolls smoothly).
  - On other tabs: **`listState.requestScrollToItem(index, offset)`**. It's an ordinary (not `suspend`) function that only remembers "start from this item on the next measure". When `LazyColumn` appears again, it is drawn at that position right away, with no animation.
  - `offset` uses the last `layoutInfo`. If Queue was never shown yet, heights are 0, so the track ends up at the top instead of the center.
- `QueueScreen.kt`: the effect and the unused `LaunchedEffect` import were removed.

### Added: playlists (Room database version 2)
- **`PlaylistEntity.kt`:** table `playlists` (`id` with `@PrimaryKey(autoGenerate = true)`, so the database assigns the id and the default `0` is just a placeholder; `name`; `createdAt`).
- **`PlaylistTrackEntity.kt`:** table `playlist_tracks`. Composite primary key `primaryKeys = ["playlistId", "trackId"]`, so one track can be in a playlist only once. `position` keeps the order.
  - `ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = CASCADE)`: deleting a playlist deletes its rows automatically.
- **`PlaylistDao.kt`:**
  - `observePlaylists()` and `observeTrackIds(playlistId)` return `Flow`.
  - `createPlaylist(...)` returns `Long`, the new row id.
  - `deletePlaylist(id)` and `removeTrack(playlistId, trackId)`.
  - `nextPosition(...)` uses `SELECT COALESCE(MAX(position), -1) + 1`: `COALESCE` returns the first non-null value, so an empty playlist gets position 0.
  - `addTrack(...)` is a function **with a body** in the DAO interface, marked `@Transaction`, so `topPosition` + `insertTrack` run as one atomic step.
- **`YorozuyaDatabase.kt`:** two new entities, `version = 2`, `autoMigrations = [AutoMigration(from = 1, to = 2)]`. Room compares `schemas/.../1.json` with `2.json` and generates the migration itself (new tables only, so no manual SQL is needed). `2.json` must be committed too.
- **`PlaylistTarget.kt`:** `sealed interface PlaylistTarget { data object Favorites; data class Custom(val id: Long) }`. A closed set of variants, so `when` over it is exhaustive without `else`, and `is Custom` smart-casts to get `id`.
- **`PlaylistsScreen.kt`:** a `LazyColumn` with a fixed "Favorites" row (track count), one row per playlist (`items(playlists, key = { it.id })`), and a "+ New Playlist" button. `CreatePlaylistDialog` is a Compose `AlertDialog` with an `OutlinedTextField`. "Create" is disabled while the name is blank, and the name is `trim()`ed.
- **`PlaylistDetailScreen.kt`:** a header row (back button, title with ellipsis, delete button only when `onDelete != null`), "Nothing here yet" or a `TrackList`. `BackHandler(onBack)` makes the system back gesture close the playlist instead of the app. Delete asks for confirmation in an `AlertDialog`.
- **`AppScaffold`:**
  - `openedList: PlaylistTarget?`. `null` shows the list of playlists, otherwise the detail screen (`Tab.PLAYLISTS -> when (val target = openedList)`).
  - `openedTrackIds` uses `remember(openedList)` to pick a `Flow`: `favoriteDao.observeIds()`, `playlistDao.observeTrackIds(id)`, or `flowOf(emptyList())` (a `Flow` with one fixed value) for `null`.
  - `tracksById` (`associateBy`) is shared to turn ids into `Track`s.
  - `detailListState = remember(openedList) { LazyListState() }`: a fresh scroll position for each opened playlist.
  - Creating a playlist opens it right away (`createPlaylist` returns the id). Deleting it returns to the list.
- New icons: `ic_arrow_back.xml`, `ic_delete.xml`.

### Bugs fixed during development
- **`PlaylistDetailScreen.kt` had no imports at all.** It produced dozens of "Unresolved reference" errors plus "@Composable invocations can only happen from..." as a side effect.
- **`import android.app.AlertDialog` in `PlaylistsScreen.kt`:** that's the old View-system dialog. Compose needs `androidx.compose.material3.AlertDialog`. When the IDE offers several imports with the same name, in Compose files pick the one from `androidx.compose`.
- **Missing `items` import:** `items(list) { }` is an extension on `LazyListScope`, so it needs its own import even though it's called inside `LazyColumn`.
- **`{ playlists -> ... playlist.name }`:** the lambda parameter name didn't match the body.
- **Missing comma after `onDismissRequest = onDismiss`:** it gave a cascade of "Syntax error" / "Call has no callee".
- **Typo `deletePlaylisr` in the DAO** while `MainActivity` called `deletePlaylist`.
- **Missing imports `flowOf` and `LazyListState` in `MainActivity.kt`.**
- **While moving the auto-scroll effect:**
  - The `{` of the `QueueScreen` body was deleted together with the effect.
  - `LaunchedEffect` had no closing `}`.
  - It was first placed above `val tracks`, but a local `val` can't be used before its declaration.
  - The visibility check was first dropped (the list re-centered on every track change). Then it was kept as a separate `if` above the tab branch, which scrolled twice. Then it was turned into an early return with `!in` instead of `in`, which is inverted. **When rewriting `if (cond) { action }` as an early exit, negate the condition: `if (!cond) return`.**

### Known issues / next steps
- ~~No UI to add a track to a playlist yet (`addTrack` / `removeTrack` are unused).~~ Done in the next entry.
- `FavoriteScreen.kt`, `favoriteTracks` and `favoriteListState` in `AppScaffold` are no longer used, since favorites open through `PlaylistDetailScreen`.
- `PlaylistDetailScreen` shows no mini player and uses no `bottomPadding`.
- The comment "Scaffold - Material screen skeleton..." in `AppScaffold` is still far above `Scaffold(`.
- Formatting in `QueueScreen.kt`: `)` and `{` of the function body are on separate lines.

---

## 2026-11-17 (2) — Track actions: long press, add to / remove from playlists

### Added: long press on a track
- **`TrackList.kt`:** `TrackRow` uses `Modifier.combinedClickable(onClick, onLongClick)` instead of `clickable`. That's one modifier for tap + long press; `onLongClick = null` means no long press.
  - `TrackList` got `onTrackLongClick: ((Track) -> Unit)? = null`, a **nullable function type** with a default, so screens that don't need it pass nothing.
  - It's passed to the row as `if (onTrackLongClick != null) { { onTrackLongClick(track) } } else null`. The outer `{ }` is the `if` branch, the inner `{ }` is the lambda. This wraps "call with this track" into a `() -> Unit`.
- **`QueueScreen`:** `onTrackLongClick: (Track) -> Unit` is passed down to `TrackList`.

### Added: `TrackActionsDialog.kt`
- `AlertDialog` with the track title (2 lines, ellipsis) and a `LazyColumn` of actions:
  - "Add to favorites" / "Remove from favorites", depending on `isFavorite`;
  - a `HorizontalDivider`, only if there are playlists;
  - one "Add to <name>" row per playlist (`items(playlists, key = { it.id })`).
- `ActionRow(text, onClick)` is a private composable: a full-width clickable `Text`.
- The dialog knows nothing about the database. It gets data and callbacks (`onToggleFavorite`, `onAddTo(playlist)`, `onDismiss`).
- **`AppScaffold`:**
  - `var actionTrack by remember { mutableStateOf<Track?>(null) }`. A non-null value means the dialog is open for that track.
  - `actionTrack?.let { track -> TrackActionsDialog(...) }` is drawn after the `when (currentTab)` inside the `Box`, so it doesn't depend on which tab opened it.
  - `onAddTo` closes the dialog, then calls `playlistDao.addTrack(...)` in `scope.launch` and shows "Added to X" or "Already in X".

### Changed: `toggleFavorite` takes the track id
- Was `() -> Unit` and always used `currentTrack`. Now it is `(Long) -> Unit` (`{ id -> ... }`), so the same code works for the playing track and for any long-pressed track.
- Now tab: `onToggleFavorite = { currentTrack?.let { toggleFavorite(it.id) } }`.

### Added: remove a track from a playlist
- **`PlaylistDetailScreen`:** `onRemoveTrack: (Track) -> Unit`. A long press sets `trackToRemove`, and an `AlertDialog` asks "Remove from "<title>"?" before calling it.
- **`AppScaffold`:** `when (target)` picks `favoriteDao.remove(id)` or `playlistDao.removeTrack(playlistId, id)`, then shows `Removed "<title>"`.

### Changed: `PlaylistDao.addTrack` returns `Boolean`
- `insertTrack` now returns `Long`. `@Insert` with `IGNORE` returns `-1` when the row already exists, because the key `(playlistId, trackId)` is taken.
- `suspend fun addTrack(...): Boolean = insertTrack(...) != -1L` uses an **expression body**: `=` instead of `{ }`, and the value of the expression is returned (like `=>` in C#).

### Bugs fixed during development
- **Missing comma after `} else null` before `onRemoveTrack = ...`:** one missing comma gave "No value passed for parameter", "Expecting an element", "Unresolved reference 'id'" on the next lines.
- **`addTrack` with `{ insertTrack(...) != -1L }`:** a block body without `return` throws the result away and returns `Unit` → "inferred type is 'Unit' but 'Boolean' was expected" at the call site.
- **Parameter `padding: Modifier.Companion.(Any) -> Modifier` in `TrackActionsDialog`:** created by the IDE quick-fix "Create parameter" while the `padding` import was missing. It's the same trap as `showSnackBar` and `align` before.
- **Missing `import androidx.compose.foundation.lazy.items`:** without it `items(playlists)` matched `items(count: Int)` → "actual type is 'List<PlaylistEntity>', but 'Int' was expected".
- **`QueueScreen` accepted `onTrackLongClick` but didn't pass it to `TrackList`:** no compile error, because `TrackList`'s parameter defaults to `null`. Long press on Queue silently did nothing.
- **Parameter order:** required `onTrackLongClick` / `onRemoveTrack` were placed after `modifier: Modifier = Modifier`. The Compose convention is required parameters → `modifier` → optional parameters.
- Message text `"Remove"` → `Removed "<title>"`.

### Known issues / next steps
- No way to reorder tracks inside a playlist (`position` is only appended).
- After removing a track from Favorites in the detail screen, the Now tab heart updates (same `Flow`), but no undo.
- `FavoriteScreen.kt`, `favoriteTracks` and `favoriteListState` are still unused.
- `TrackActionsDialog` is only reachable from Queue (the detail screen uses long press for "Remove").
