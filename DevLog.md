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
Until now the `ExoPlayer` lived inside `TrackScreen`. Playback was tied to the screen: closing the app or rotating the device released the player. Android's way to keep audio running without the UI is a **Service**, a component with no screen whose lifetime is separate from the Activity. Media3 provides a ready-made one: `MediaSessionService`.

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
  - **`?: return@TrackList`** means "if the controller is null, leave this lambda". `@TrackList` is a **label**: a bare `return` inside a lambda would try to return from `TrackScreen` itself, so you name the lambda to return from.
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
- **State hoisting:** the bar receives data plus a callback, `onPlayPauseClick: () -> Unit` (a function type, like C#'s `Action`). It doesn't know about the controller, so `TrackScreen` decides what a click does.

### Added: the list scrolls to the playing track
```kotlin
val listState = rememberLazyListState()
LaunchedEffect(currentTrackId, tracks) {
    val index = tracks.indexOfFirst { it.id == currentTrackId }
    if (index >= 0) listState.scrollToItem(index)
}
```
- **`LazyListState`** holds the list's scroll position and lets code scroll it. It's created in `TrackScreen` and passed down to `TrackList` (`LazyColumn(state = listState)`), so the parent controls the scrolling.
- **The effect has two keys.** It re-runs when the track changes *and* when the tracks finish loading. When you return to the app, `currentTrackId` can arrive before the list, and with only one key the index would be -1 and nothing would scroll.
- `scrollToItem` is a `suspend` function. A `LaunchedEffect` body is a coroutine, so it can be called there.

### Fixed
- **The bar no longer covers the last track.** `LazyColumn(contentPadding = PaddingValues(bottom = 72.dp))` adds empty space *inside* the scrollable area after the last item. `Modifier.padding` would shrink the list instead.
- **Double padding on `TrackList`:** `modifier = modifier` was removed from the call, so the Scaffold's `innerPadding` is applied once (by the `Box`).
- **Build errors along the way:**
  - The IDE's "Create parameter" quick-fix added a bogus `align` parameter to `TrackScreen` when `align` was used outside the `Box`.
  - "Optimize imports" removed `Row`'s import while the file didn't compile. That one missing import also produced "@Composable invocations can only happen…" and "Unresolved reference 'weight'".

### Known issues / next steps
- **The permission message is gone.** The `if (hasPermission) ... else Text("Access to media on device is required")` branch was removed, so if access is denied the user sees an empty list with only a Shuffle button.
- **Auto-scroll jumps when you tap a track.** `scrollToItem` always puts the item at the top of the list, so tapping a track in the middle of the screen moves it to the top. Options: scroll only when the item isn't visible (check `listState.layoutInfo.visibleItemsInfo`), and/or use `animateScrollToItem` for a smooth scroll.
- Still open from before: "Close" wipes the saved queue, the outdated comment above `DisposableEffect`, `STATE_ENDED` after the last track, `loadTracks` on the main thread, the unused `Preview` import (`Log` is gone).
