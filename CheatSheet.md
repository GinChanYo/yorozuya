# Yorozuya Cheat Sheet

Everything used in the code so far, one short line each. C# analogies where they help.
Sections: Kotlin language → Kotlin stdlib → Coroutines → Android platform → Compose runtime → Compose layout → Modifiers → Compose UI elements → Material 3 → Media3 → Gradle → Project code.

---

## 1. Kotlin language

| Construct | Meaning |
|---|---|
| `val` / `var` | Read-only / mutable variable (`readonly` / normal field in C#). |
| `fun name(a: Int): String` | Function declaration; return type after `:`. |
| `fun f() = expr` | Single-expression function, no `{ return }` needed. |
| Default args `x: Int = 512` | Parameter with default value; callers may skip it. |
| Named args `f(size = 40.dp)` | Pass arguments by name, any order. |
| `T?` | Nullable type. `Int` can't be null, `Int?` can (like `int` vs `int?`). |
| `?.` | Safe call: call only if not null, otherwise result is null. |
| `?:` (Elvis) | Fallback if left side is null: `artist ?: "Unknown"`, `controller ?: return`. |
| Smart cast | After `if (x == null) return`, compiler treats `x` as non-null. |
| `data class` | Class with auto `equals`/`hashCode`/`toString`/`copy` (like C# `record`). |
| `class A : B()` | Inheritance; `()` calls base constructor. |
| `enum class Tab(val label: String, val icon: Int)` | Enum whose values carry properties; each value is a constructor call `QUEUE("Queue", R.drawable.ic_queue)`. |
| `Tab.entries` | List of all enum values (`Enum.GetValues`). |
| `companion object` | Holder for "static" members. |
| `const val` | Compile-time constant (C# `const`), primitives/String only. |
| `private` | Visible only inside the class/file. |
| `class A(private val x: T)` | Constructor parameter that is also a field (C# primary constructor). |
| `var x ... private set` | Public getter, private setter: anyone reads, only the class assigns. |
| `lateinit var` | Non-null `var` assigned later (e.g. in `onCreate`), not in constructor. |
| `inner class` | Nested class with access to the outer instance (plain nested = C# static nested). |
| `object : Interface { }` | Anonymous object implementing an interface. |
| `override fun` | Override a base/interface member (explicit keyword required). |
| Shadowing | A local with the same name as a parameter hides it; Kotlin only warns ("Name shadowed"). |
| `@Annotation` | Metadata, e.g. `@Composable`, `@Stable`, `@OptIn`. |
| `@param:Annotation` | Use-site target: apply to the constructor parameter (not the property/field) of a `val` in a constructor. |
| `when (x) { A -> ...; else -> ... }` | Switch expression; returns a value. Exhaustive over enums → no `else`. |
| `when { cond -> ... }` | `when` without subject = if/else-if chain. |
| `if` as expression | `val c = if (a) x else y` (no ternary operator in Kotlin). |
| `return@LaunchedEffect` | Labeled return: exit only the lambda, not the outer function. |
| Lambda `{ x -> ... }` | Anonymous function; single param can be `it`. |
| Trailing lambda `f(a) { ... }` | Last lambda parameter goes outside `()`. |
| Lambda with receiver | Inside `Row { }`, `this` is `RowScope` → `Modifier.weight` available. |
| Function types `() -> Unit`, `(Float) -> Unit` | Like `Action`, `Action<float>`. A lambda can be stored in a `val` and passed down: `val showMessage: (String) -> Unit = { ... }`. |
| Function type with receiver `A.(String) -> Unit` | Function called *on* an `A` (inside it `this` is `A`), e.g. `RowScope.() -> Unit` of `Row { }`. |
| `Unit` | "No value" type (`void`). |
| `by` (delegation) | `var x by mutableStateOf(0)` — reads/writes go through the delegate's `getValue`/`setValue`. |
| String templates `"$x"`, `"${a.b}"` | String interpolation (`$"{x}"`). |
| `"🔀"` | Emoji as UTF-16 surrogate pair. |
| `or` | Bitwise OR (`|` in C#), e.g. combining flags. |
| `0 until n` | Range 0..n-1. |
| `x in coll` / `!in` | Membership test (`Contains`). |
| `a to b` | Creates a `Pair(a, b)`. |
| `(path, list) ->` | Destructuring in lambda params (tuple deconstruction). |
| `Class::class.java` | Java `Class` object of a Kotlin class (`typeof`). |
| `import a.b.*` | Wildcard import. |

## 2. Kotlin standard library

| Function | Meaning |
|---|---|
| `listOf`, `emptyList()`, `emptySet()` | Immutable collections. |
| `mutableListOf<T>()`, `+=` | Mutable list, add element. |
| `set + x`, `set - x` | New set with/without element (the old set is unchanged; a new object is what `mutableStateOf` notices). |
| `map { }` | Transform each item (`Select`). |
| `mapNotNull { }` | `map` dropping nulls. |
| `flatMap { }` | Each item → list, merged (`SelectMany`). |
| `filter { }` | Keep matching (`Where`). |
| `groupBy { }` | Map key → list of items (`GroupBy`). |
| `count { }`, `any { }` | Count matching / at least one matches. |
| `firstOrNull { }` | First match or null (`FirstOrDefault`). |
| `indexOf(x)`, `indexOfFirst { }` | Index or -1. |
| `sortedBy`, `sortedByDescending` | Sorted copy (`OrderBy`). |
| `take(n)`, `joinToString(sep)` | First n items / join to string. |
| `toSet()` | Remove duplicates. |
| `.indices` | Range of valid indices. |
| `isEmpty()`, `isNotEmpty()`, `isNullOrEmpty()` | Emptiness checks (last one also accepts null). |
| `split(",")` | Split string. |
| `toLongOrNull()` | Parse, null on failure (`long.TryParse`). |
| `toLong()`, `toFloat()` | Numeric conversion. |
| `coerceAtLeast(0L)` | Clamp to minimum. |
| `"%d:%02d".format(a, b)` | printf-style formatting. |
| `?.let { }` | Run block only if not null; value is `it`. |
| `takeIf { cond }` | The value if `cond` is true, otherwise `null`. With `?.`: `s?.takeIf { it != "x" }`. |
| `run { }` | Run block with object as `this`. |
| `use { }` | Run block and close resource after (`using`). |
| `500.milliseconds` | `kotlin.time.Duration` from a number. |

## 3. Coroutines (`kotlinx.coroutines`)

| Name | Meaning |
|---|---|
| Coroutine | Lightweight async task; `suspend` functions can pause it without blocking a thread (like `async/await`). |
| `delay(d)` | Suspend for a while (`await Task.Delay`). |
| `scope.launch { }` | Start a coroutine; returns a `Job`. Inside a coroutine, `launch` starts a child. |
| `job.cancel()` | Cancel a coroutine (e.g. one waiting in `showSnackbar` → the bubble disappears). No effect if it already finished. |
| `withContext(Dispatchers.IO) { }` | Run block on the I/O thread pool, return result to caller. |
| `Dispatchers.IO` | Thread pool for disk/network waiting. |

## 4. Android platform

| Name | Meaning |
|---|---|
| `Context` | App's handle to the system: resources, services, prefs, `contentResolver`. |
| `ComponentActivity` | Base class of a screen (activity); `MainActivity` extends it. |
| `onCreate(savedInstanceState: Bundle?)` | Lifecycle callback: activity/service created. |
| `onDestroy()` | Lifecycle callback: about to be destroyed. |
| `super.onCreate(...)` | Call base implementation (`base.` in C#). |
| `Bundle` | Key-value container passed between components; `Bundle.EMPTY`. |
| `Intent` | Message describing what to open/do. |
| `PendingIntent.getActivity(...)` | Intent the system may fire later for us (notification tap). |
| `FLAG_IMMUTABLE`, `FLAG_UPDATE_CURRENT` | PendingIntent flags: required immutability / reuse and update existing. |
| `ComponentName(context, Cls::class.java)` | Address of an app component (our service). |
| `Uri` | Address of a resource (`content://...`). |
| `ContentResolver` / `contentResolver` | Middleman to content providers (MediaStore, contacts...). |
| `contentResolver.query(uri, projection, selection, args, sort)` | SQL-like query → `Cursor`. |
| `contentResolver.loadThumbnail(uri, Size, null)` | Decode a small preview (embedded cover) → `Bitmap`. |
| `Cursor` | Result rows: `moveToNext()`, `getColumnIndexOrThrow()`, `getLong()`, `getString()`. |
| `ContentUris.withAppendedId(base, id)` | Uri of one row (one track). |
| `MediaStore.UNKNOWN_STRING` | `"<unknown>"`, what MediaStore returns for missing tags (not `null`). |
| `MediaStore.Audio.Media` | System audio table: `_ID`, `TITLE`, `ARTIST`, `ALBUM`, `DURATION`, `RELATIVE_PATH`, `IS_MUSIC`. |
| `MediaStore.Audio.Media.getContentUri(VOLUME_EXTERNAL)` | Uri of the whole audio table. |
| `android.util.Size(w, h)` | Width/height pair. |
| `SharedPreferences` | Small private key-value storage (XML file). |
| `getSharedPreferences(name, MODE_PRIVATE)` | Open prefs file only our app can read. |
| `prefs.getString/getInt/getLong/getBoolean/getStringSet(key, default)` | Read with default for missing key. |
| `prefs.edit().putX(...).apply()` | Batch write, saved asynchronously (`commit()` blocks). |
| `Manifest.permission.READ_MEDIA_AUDIO` | Runtime permission to read audio files. |
| `ContextCompat.checkSelfPermission(...)` | Is the permission granted? Compare with `PackageManager.PERMISSION_GRANTED`. |
| `ActivityResultContracts.RequestPermission()` | Contract for the system permission dialog. |
| `R.drawable.name` | Generated `Int` id of a file in `res/drawable/`; not the image itself. |
| `@DrawableRes` | Marks an `Int` as a drawable id, so Android Studio flags wrong ids. |
| Vector Asset | Android Studio: right-click `res` → New → Vector Asset, adds Material icons as XML. |
| `Build.VERSION.SDK_INT` | Android version of the device (theme uses it). |
| `IOException` | I/O error (missing cover, unreadable file). |
| `enableEdgeToEdge()` | Draw behind status/navigation bars. |
| `setContent { }` | Put a Compose UI into the activity. |

## 5. Compose runtime and state

| Name | Meaning |
|---|---|
| `@Composable` | Function that describes UI; re-run ("recomposed") when the state it reads changes. |
| Recomposition | Compose calling the composable again with new state. |
| `remember { }` | Keep a value across recompositions (lost when leaving composition). |
| `remember(key1, key2) { }` | Recompute when a key changes. |
| `mutableStateOf(x)` | Observable value; writing it triggers redraw of readers. |
| `var x by remember { mutableStateOf(...) }` | Standard pattern for local UI state. |
| `getValue` / `setValue` imports | Needed for `by` with state. |
| `@Stable` | Promise to Compose that changes are reported through state. |
| `LaunchedEffect(keys) { }` | Run a coroutine when entering composition / when keys change; cancelled on leave. `Unit` key = once. |
| `rememberCoroutineScope()` | Scope for starting coroutines yourself, e.g. from `onClick`; cancelled when the composable leaves. |
| `DisposableEffect(key) { ... onDispose { } }` | Effect with cleanup when leaving composition. |
| `produceState(initial, key) { value = ... }` | Coroutine that produces state; returns `State`, read `.value`. |
| `LocalContext.current` | Current `Context` inside a composable. |
| `rememberLauncherForActivityResult(contract) { result -> }` | Launcher for system dialogs (permission). `launcher.launch(...)`. |
| `rememberLazyListState()` | Scroll state of a `LazyColumn`. |
| State hoisting | State goes down as parameters, events go up as callbacks. |
| `rememberXxx()` naming | Composable that creates and keeps an object; lowercase because it returns a value. |

## 6. Compose layout

| Name | Meaning |
|---|---|
| `Column { }` | Children vertically. `horizontalAlignment`, `verticalArrangement`. |
| `Row { }` | Children horizontally. `verticalAlignment`, `horizontalArrangement`. |
| `Box { }` | Children stacked on top of each other. `contentAlignment`. |
| `Spacer(Modifier...)` | Empty gap. |
| `LazyColumn(state, contentPadding) { items(list) { } }` | Scrollable list that only builds visible rows (RecyclerView). |
| `items(list) { item -> }` | One row per list element inside `LazyColumn`. |
| `LazyListState` | `layoutInfo.visibleItemsInfo` (index, size), `viewportStartOffset/EndOffset`, `animateScrollToItem(i, offset)`. |
| `PaddingValues(bottom = 140.dp)` | Padding description (used for `contentPadding`, Scaffold `innerPadding`). |
| `Alignment.Center`, `CenterVertically`, `CenterHorizontally`, `BottomCenter` | Alignment options. |
| `Arrangement.SpaceBetween` | First at start, last at end, space between. |
| `Arrangement.SpaceEvenly` | Equal gaps everywhere, including edges. |
| `Dp`, `.dp` | Density-independent pixels: `16.dp`. |

## 7. Modifiers (chained, applied in order)

| Modifier | Meaning |
|---|---|
| `Modifier` | Empty start of a chain; convention: every composable takes `modifier: Modifier = Modifier`. |
| `fillMaxSize()`, `fillMaxWidth()` | Take all available space / width. |
| `size(48.dp)`, `width()`, `height()` | Fixed size. |
| `aspectRatio(1f)` | Height = width × ratio (square). |
| `padding(...)` | Space around content; order matters: `.padding().clickable()` leaves the padding outside the tap area, `.clickable().padding()` includes it. |
| `weight(1f)` | Share leftover space in `Row`/`Column` (only in `RowScope`/`ColumnScope`). |
| `align(...)` | Position a child inside `Box` (only in `BoxScope`). |
| `clickable(onClick = ...)` | Make tappable. |
| `background(color)` | Fill background. |
| `clip(shape)` | Cut content to a shape. |
| `RoundedCornerShape(4.dp)` | Rounded rectangle shape. |

## 8. Compose UI elements

| Name | Meaning |
|---|---|
| `Text(text, style, color, maxLines, overflow, textAlign)` | Text label. |
| `TextOverflow.Ellipsis` | Cut overflow with "…". |
| `TextAlign.Center` | Center text lines. |
| `Image(bitmap, contentDescription, contentScale, modifier)` | Draw an `ImageBitmap`. `contentDescription = null` = decorative. |
| `Icon(painter, contentDescription)` | Small tinted icon (Material 3); color comes from the surrounding component. |
| `painterResource(R.drawable.x)` | Load a drawable resource (e.g. vector XML) as a `Painter`. |
| `ContentScale.Crop` | Fill the box, trim overflow, keep proportions. |
| `ImageBitmap`, `Bitmap.asImageBitmap()` | Compose bitmap type / conversion from Android `Bitmap`. |

## 9. Material 3

| Name | Meaning |
|---|---|
| `MaterialTheme` | Provides `colorScheme` and `typography` to children. |
| `MaterialTheme.colorScheme.primary/onSurface/surfaceVariant` | Theme colors. |
| `MaterialTheme.typography.displayLarge/headlineMedium/headlineSmall/bodyLarge/bodyMedium/bodySmall/labelSmall` | Text styles. |
| `YorozuyaTheme { }` | Our theme (`ui/theme`): dynamic colors on Android 12+, dark/light by system. |
| `Scaffold(bottomBar = { }, snackbarHost = { }) { innerPadding -> }` | Screen skeleton with slots; `innerPadding` = space bars take. |
| `SnackbarHostState` | Holds the shown message: `showSnackbar(text, duration)` (suspend), `currentSnackbarData?.dismiss()`. |
| `SnackbarHost(state) { data -> }` | Draws messages; optional lambda = custom look, `data.visuals.message` = text. |
| `SnackbarDuration.Short/Long/Indefinite` | How long a message stays (Short ≈ 4 s). |
| `NavigationBar { NavigationBarItem(selected, onClick, icon, label) }` | Bottom tab bar. `label = null` → icon only. |
| `Surface(color, contentColor, shape, tonalElevation)` | Background container; `contentColor` colors text inside; elevation tints color. |
| `RoundedCornerShape(50)` (Int) | Corner radius in percent of the size: 50 = fully round ends (pill). With `.dp` it's a fixed radius. |
| `colorScheme.inverseSurface/inverseOnSurface` | Contrasting colors for bubbles (dark in light theme). |
| `TextButton(onClick = { action }) { Text(...) }` | `onClick` runs once on tap; trailing `{ }` is what's drawn, runs on every recomposition - no actions there. |
| `TextButton(onClick) { }` | Flat button with content. |
| `Slider(value, onValueChange, onValueChangeFinished)` | Draggable 0..1 bar. |
| `Checkbox(checked, onCheckedChange)` | Checkbox. |
| `HorizontalDivider()` | Horizontal line. |

## 10. Media3 (playback)

| Name | Meaning |
|---|---|
| `Player` | Common interface of `ExoPlayer` and `MediaController`. |
| `ExoPlayer.Builder(ctx).setAudioAttributes(a, true).setHandleAudioBecomingNoisy(true).build()` | Actual player; handles audio focus and headphone unplug. |
| `AudioAttributes` + `C.USAGE_MEDIA`, `C.AUDIO_CONTENT_TYPE_MUSIC` | Tell the system this is music. |
| `MediaItem.Builder().setUri().setMediaId().build()` | One queue entry; `mediaId` = our MediaStore id as text. |
| `setMediaItems(items, index, posMs)`, `prepare()`, `play()`, `pause()`, `stop()`, `release()` | Queue and transport control. |
| `seekTo(ms)`, `seekToNextMediaItem()`, `seekToPreviousMediaItem()` | Seeking. |
| `isPlaying`, `currentPosition`, `duration`, `currentMediaItem`, `currentMediaItemIndex`, `mediaItemCount`, `getMediaItemAt(i)`, `clearMediaItems()` | Player state / queue access. |
| `shuffleModeEnabled`, `repeatMode` (`REPEAT_MODE_OFF/ALL/ONE`) | Modes. |
| `C.TIME_UNSET` | Negative "unknown" duration before prepare. |
| `Player.Listener` | `onIsPlayingChanged`, `onMediaItemTransition`, `onShuffleModeEnabledChanged`, `onRepeatModeChanged`. |
| `MediaSessionService` | Foreground service base that shows the media notification; `onGetSession`. |
| `MediaSession.Builder(ctx, player).setCallback().setMediaButtonPreferences().setSessionActivity().build()` | Makes the player controllable from outside (notification, Bluetooth, our UI). |
| `MediaSession.Callback` | `onConnect` (allowed commands), `onCustomCommand`. |
| `ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(...)` | Allowed commands + our own. |
| `SessionCommand("ACTION_STOP", Bundle.EMPTY)`, `customAction` | Custom command and its name. |
| `CommandButton.Builder(ICON_STOP).setDisplayName().setSessionCommand()` | Extra notification button. |
| `SessionResult(RESULT_SUCCESS)` | Result of a custom command. |
| `SessionToken(ctx, ComponentName)` | Address of the session for a controller. |
| `MediaController.Builder(ctx, token).buildAsync()` | Connect UI to the service → `ListenableFuture`. |
| `MediaController.releaseFuture(future)` | Disconnect. |
| `@OptIn(UnstableApi::class)` | Allow APIs marked unstable. |

### Guava (comes with Media3)
| Name | Meaning |
|---|---|
| `ListenableFuture` | Future with completion callback (`Task`). `addListener(cb, executor)`, `get()`. |
| `MoreExecutors.directExecutor()` | Run callback on the completing thread. |
| `Futures.immediateFuture(x)` | Already-completed future. |
| `ImmutableList.of(...)` | Immutable list. |

## 11. Gradle

| Name | Meaning |
|---|---|
| `gradle/libs.versions.toml` | Version catalog: `[versions]` and `[libraries]` aliases, used as `libs.xxx` in build files. |
| `app/build.gradle.kts` | App module build script; `dependencies { implementation(...) }`. |
| `platform(libs.androidx.compose.bom)` | Compose BOM: supplies one matching set of versions, so Compose libraries are listed without versions. Never remove it. |
| Gradle sync | Android Studio re-reads build files (elephant icon / "Sync Now"). |

## 12. Project code

| File | Item | Purpose |
|---|---|---|
| `Track.kt` | `data class Track(id, uri, title, artist?, album?, duration, relativePath)` | One song from MediaStore. |
| `TrackLoader.kt` | `loadTracks(context): List<Track>` | Query MediaStore for music. |
| `Tabs.kt` | `enum class Tab(label, icon)` | `QUEUE`, `NOW_PLAYING`, `PLAYLISTS`, `STORAGE`, `DOWNLOADER`; icon = `R.drawable.ic_*`. |
| `MainActivity.kt` | `MainActivity` | Entry point; `setContent { YorozuyaTheme { AppScaffold() } }`. |
| | `AppScaffold()` | Root UI: tabs, permission, `allTracks`, `player`, `listState`, `library`, filtered `tracks`, snackbar + `showMessage`. |
| | `TrackScreen(player, tracks, currentTrack, hasPermission, listState, showMessage)` | Queue tab: buttons, (already filtered) list, mini player, auto-scroll. |
| | `Placeholder(name)` | Stub screen for unfinished tabs. |
| `TrackList.kt` | `TrackList(tracks, currentTrackId, isPlaying, listState, onTrackClick)` | Scrollable track list. |
| | `TrackRow(track, isCurrent, isPlaying, onClick)` | One row: cover + title/artist. |
| `NowPlayingBar.kt` | `NowPlayingBar(track, isPlaying, on...Click, player)` | Mini player: seek bar, cover, title, prev/play/next. |
| | `formatTime(ms)` | `83500` → `"1:23"`. |
| `SeekBar.kt` | `PlayerSeekBar(player, modifier)` | Slider + time labels with own drag state; used by mini player and "Now" tab. |
| `NowPlayingScreen.kt` | `NowPlayingScreen(player, track?, showMessage)` | "Now" tab: big cover, info, slider, all controls. |
| `Artwork.kt` | `rememberArtwork(uri, sizePx): ImageBitmap?` | Load embedded cover off the main thread. |
| | `ArtworkThumb(uri, size, sizePx, modifier)` | Small rounded cover with "♪" fallback. |
| `FolderPicker.kt` | `buildFolderList(tracks, includeSubfolders)` | `(path, count)` pairs for the Folders tab. |
| | `FoldersScreen(allTracks, library)` | "Folders" tab: subfolders checkbox + folder checkbox list. |
| `LibraryFilter.kt` | `class LibraryFilter(prefs)` | Folder filter state: `selectedPaths`, `includeSubfolders` (both `private set`). |
| | `togglePath(path)`, `toggleSubfolders()` | Change the filter and save to prefs immediately. |
| | `filter(tracks)` | Apply the filter to a track list. |
| | `rememberLibraryFilter()` | Create once, keep across recompositions. |
| `PlayerConnection.kt` | `class PlayerState` | UI-side playback state: `controller`, `currentTrackId`, `isPlaying`, `shuffleEnabled`, `repeatMode`, `position`, `duration`. |
| | `syncFrom(p)` | Copy current state after connecting. |
| | `playQueue(tracks, startIndex)` | Replace queue and play. |
| | `togglePlayPause()`, `next()`, `previous()`, `seekTo(ms)`, `toggleShuffle()`, `cycleRepeat()` | Controls. |
| | `rememberPlayerState()` | Connect to service, listen, poll position every 500 ms. |
| | `repeatModeName(mode)` | `"Off"` / `"All"` / `"One"` for button text and messages. |
| `PlaybackService.kt` | `PlaybackService : MediaSessionService` | Owns `ExoPlayer` and `MediaSession`, notification, "Close" button. |
| | `saveState()` / `restoreState(player)` | Persist queue, index, position, shuffle, repeat in prefs `playback_state`. |
| | `CallBack` | Session callback: allows and handles `stopCommand`. |
| Prefs `library` | `paths` (string set), `subfolders` (bool) | Folder filter, read/written by `LibraryFilter`. |
