# Yorozuya Cheat Sheet

Everything used in the code so far, one short line each. C# analogies where they help.
Sections: Kotlin language → Kotlin stdlib → Coroutines → Android platform → Compose runtime → Compose layout → Modifiers → Compose UI elements → Material 3 → Media3 → Room (+ kotlinx.serialization) → Gradle → Project code.

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
| `abstract class` / `abstract fun` | Can't be instantiated / no body, subclass implements it (same as C#). |
| `interface` with bodiless `fun` | Contract; Room generates the implementing class for a `@Dao`. |
| `@Volatile` | Field always read from memory, visible to all threads (C# `volatile`). |
| `synchronized(lock) { }` | Only one thread at a time inside the block (C# `lock`). |
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
| `when (val t = x) { ... }` | Bind the subject to a name usable in the branches. |
| `sealed interface X { data object A : X; data class B(val id: Long) : X }` | Closed set of variants (like a discriminated union): `when` is exhaustive without `else`. `data object` = singleton with a readable `toString`. |
| `is B ->` / `if (x is B)` | Type check + smart cast: inside, `x.id` is available without a cast. |
| `when { cond -> ... }` | `when` without subject = if/else-if chain. |
| `if` as expression | `val c = if (a) x else y` (no ternary operator in Kotlin). `else` is required; Python's `x if a else y` doesn't exist. |
| `if (x = true)` | Doesn't compile: assignment isn't an expression (in C++ it would be always true). A `Boolean` is used as is: `if (x)`. |
| Parameters are read-only | Function parameters are always `val`; assign to a local copy instead. |
| Early return | `if (!cond) return a` first, the main case below without nesting (like `?: return`). |
| `val ok = a == b` | A comparison is already a `Boolean`; no `if` needed. |
| Java method parameters | No default values from Kotlin: `prefs.getString(key, null)`, the default must be passed. |
| `return@LaunchedEffect` | Labeled return: exit only the lambda, not the outer function. |
| `return@rememberLauncherForActivityResult` | Same labeled return; the label is the name of the function the lambda was passed to. |
| Extension function `fun Track.toRef() = ...` | Declared outside the class, called like its method: `track.toRef()`; inside, `this` is the `Track` (C# extension method without `static`/`this`). |
| `suspend fun LazyListState.centerOn(...)` | Extension functions can be `suspend` too. |
| `class A(x: List<T>)` (no `val`) | Constructor parameter usable only while initializing fields (`private val index = x.groupBy { }`), not stored. |
| `for (x in list) { }` | Plain loop (`foreach`); clearer than `map` when the body changes counters. |
| `try { } catch (e: A) { } catch (e: B) { }` | Checked top to bottom: the more specific type goes first. |
| Lambda `{ x -> ... }` | Anonymous function; single param can be `it`. |
| Trailing lambda `f(a) { ... }` | Last lambda parameter goes outside `()`. |
| Lambda with receiver | Inside `Row { }`, `this` is `RowScope` → `Modifier.weight` available. |
| Function types `() -> Unit`, `(Float) -> Unit` | Like `Action`, `Action<float>`. A lambda can be stored in a `val` and passed down: `val showMessage: (String) -> Unit = { ... }`. |
| Nullable function type `((Track) -> Unit)? = null` | Optional callback; check `!= null` before calling. |
| `onDragEnd = { f(x) }` vs `onDragEnd = f(x)` | With braces: a lambda, runs later. Without: `f` is called **now** and its result (`Unit`) is passed → type mismatch with `() -> Unit`. |
| `if (f != null) { { f(x) } } else null` | Outer `{ }` = `if` branch, inner `{ }` = lambda: wraps "call `f` with `x`" into `() -> Unit`. |
| Expression body `fun f(): Boolean = a != b` | `=` instead of `{ }`: the expression is returned (C# `=>`). A `{ }` body needs `return`, otherwise it returns `Unit`. |
| `"Removed \"${t.title}\""` | `\"` = quote inside a string, combined with a template. |
| Function type with receiver `A.(String) -> Unit` | Function called *on* an `A` (inside it `this` is `A`), e.g. `RowScope.() -> Unit` of `Row { }`. |
| `Unit` | "No value" type (`void`). |
| `by` (delegation) | `var x by mutableStateOf(0)` — reads/writes go through the delegate's `getValue`/`setValue`. |
| String templates `"$x"`, `"${a.b}"` | String interpolation (`$"{x}"`). |
| `"\${x}"` | `\$` escapes the dollar: the text `${x}` is kept literally, no interpolation (and no compile error). |
| `16.dp + 8.dp` | `Dp` has `operator fun plus`, so dp values add like numbers. |
| `"🔀"` | Emoji as UTF-16 surrogate pair. |
| `or` | Bitwise OR (`|` in C#), e.g. combining flags. |
| `0 until n` | Range 0..n-1. |
| `list1 == list2` | Compares contents element by element (`SequenceEqual`), not references. |
| `==` on `data class` / `data object` | Compares contents: `Playlist(5) == Playlist(5)` is `true`. A plain `class` compares references. |
| Local used before declaration | Not allowed inside a function: a `val` must be declared above the code using it (move the user down). |
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
| `associateBy { it.id }` | `Map` key → item (`ToDictionary`); `map[key]` returns `T?`. |
| `associate { it.a to it.b }` | `Map` from a pair per item (key and value both chosen). |
| `distinctBy { it.name }` | Keep the first item for each key, drop later duplicates (`DistinctBy`). |
| `toMutableMap()` | Mutable copy of a map. |
| `map.getOrPut(key) { create() }` | Existing value, or run the block, store its result under `key` and return it (`TryGetValue` + `Add`). |
| `apply { }` | Run block with the value as `this`, return the value: `list.toMutableList().apply { add(to, removeAt(from)) }`. |
| `toMutableList()` | Mutable copy of a list (the original is unchanged). |
| `removeAt(i)`, `add(i, x)` | Remove by index (returns the item) / insert at index. Together = move an item. |
| `forEachIndexed { i, x -> }` | Loop with index (`for` + counter). |
| `also { }` | Do something with the value (`it`), return the value itself: `build().also { instance = it }`. |
| `flatMap { }` | Each item → list, merged (`SelectMany`). |
| `filter { }` | Keep matching (`Where`). |
| `groupBy { }` | Map key → list of items (`GroupBy`). |
| `groupingBy { it.key }.eachCount()` | `Map<key, count>` (SQL `GROUP BY ... COUNT(*)`). |
| `singleOrNull()` | The only element, or `null` if the list has 0 **or several** (`SingleOrDefault` without the exception). |
| `count { }`, `any { }` | Count matching / at least one matches. |
| `firstOrNull { }` | First match or null (`FirstOrDefault`). |
| `indexOf(x)`, `indexOfFirst { }` | Index or -1. |
| `sortedBy`, `sortedByDescending` | Sorted copy (`OrderBy`). |
| `take(n)`, `joinToString(sep)` | First n items / join to string. |
| `toSet()` | Remove duplicates. |
| `.indices` | Range of valid indices; `i in list.indices` = "is `i` a valid index". |
| `shuffled()` | Shuffled copy of a list. |
| `filterIndexed { i, x -> }` | `filter` with the index: `filterIndexed { i, _ -> i != skip }`; `_` = unused parameter. |
| `listA + listB` | New list, `listA` followed by `listB` (`Concat`). |
| `subList(from, to)` | View of `[from, to)`; throws if the range is outside the list. |
| `toList()` | Own copy of a list, so nobody else can change what you store. |
| `isEmpty()`, `isNotEmpty()`, `isNullOrEmpty()` | Emptiness checks (last one also accepts null). |
| `split(",")` | Split string. |
| `lowercase()` | Lower-case copy of a string. |
| `text.toByteArray()` | String → bytes, UTF-8 by default. |
| `stream.bufferedReader().use { it.readText() }` | Read a whole `InputStream` as text and close it. |
| `error("msg")` | Throw `IllegalStateException(msg)`; handy after `?:`: `x ?: error("...")`. |
| `toLongOrNull()` | Parse, null on failure (`long.TryParse`). |
| `toLong()`, `toFloat()` | Numeric conversion. |
| `coerceAtLeast(0L)` | Clamp to minimum. |
| `"%d:%02d".format(a, b)` | printf-style formatting. |
| `?.let { }` | Run block only if not null; value is `it`. |
| `x?.let { v -> Composable(v) }` | Draw something only while `x` is not null (e.g. a dialog for `actionTrack`). |
| `trim()`, `isNotBlank()` | Strip surrounding whitespace / has a non-space character. |
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
| `withContext(Dispatchers.IO) { }` | Run block on the I/O thread pool, then continue where we were; returns the block's last value: `val text = withContext(IO) { read() }` (`await Task.Run`). |
| `Dispatchers.IO` | Thread pool for disk/network waiting. |
| `suspend fun` | Function that can pause a coroutine; callable only from a coroutine (`async Task`). |
| `Flow<T>` | Stream of values over time (`IAsyncEnumerable`/`IObservable`); Room emits a new value on every table change. |
| `flowOf(x)` | `Flow` that emits one fixed value (e.g. `flowOf(emptyList())` as a "nothing" branch). |
| `flow.first { cond }` | Suspend until the flow emits a matching value, return it. |
| `CancellationException` | Thrown inside a coroutine when it is cancelled. A general `catch (e: Exception)` would swallow it: put `catch (e: CancellationException) { throw e }` before it. |

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
| `MediaStore.Audio.Media` | System audio table: `_ID`, `TITLE`, `ARTIST`, `ALBUM`, `DURATION`, `RELATIVE_PATH`, `DISPLAY_NAME` (file name with extension), `IS_MUSIC`. Without a title tag, `TITLE` = file name without extension. |
| `MediaStore.Audio.Media.getContentUri(VOLUME_EXTERNAL)` | Uri of the whole audio table. |
| `android.util.Size(w, h)` | Width/height pair. |
| `SharedPreferences` | Small private key-value storage (XML file). |
| `getSharedPreferences(name, MODE_PRIVATE)` | Open prefs file only our app can read. |
| `prefs.getString/getInt/getLong/getBoolean/getStringSet(key, default)` | Read with default for missing key. |
| `prefs.edit().putX(...).apply()` | Batch write, saved asynchronously (`commit()` blocks). |
| `Manifest.permission.READ_MEDIA_AUDIO` | Runtime permission to read audio files. |
| `ContextCompat.checkSelfPermission(...)` | Is the permission granted? Compare with `PackageManager.PERMISSION_GRANTED`. |
| `ActivityResultContracts.RequestPermission()` | Contract for the system permission dialog. |
| SAF (Storage Access Framework) | System file dialogs: the user picks the file or place, the app gets a `Uri` to it. No storage permission needed. |
| `ActivityResultContracts.CreateDocument("application/json")` | "Save as" dialog; `launch("name.json")` = suggested name; result `Uri?` (`null` = cancelled). |
| `ActivityResultContracts.OpenDocument()` | "Open file" dialog; `launch(arrayOf("application/json"))` = allowed MIME types; result `Uri?`. |
| `contentResolver.openOutputStream(uri)` / `openInputStream(uri)` | Write / read stream for a `content://` Uri; may return `null`. Blocking, so inside `withContext(Dispatchers.IO)`. |
| `R.drawable.name` | Generated `Int` id of a file in `res/drawable/`; not the image itself. |
| `res/font/x.ttf` → `R.font.x` | Font resource; file names lowercase with underscores. |
| Variable font | One file holding all weights on a `wght` axis (e.g. 300–700); static fonts are one weight per file. |
| `colorResource(android.R.color.system_accent1_500)` | System wallpaper accent color (Android 12+). |
| `@DrawableRes` | Marks an `Int` as a drawable id, so Android Studio flags wrong ids. |
| Vector Asset | Android Studio: right-click `res` → New → Vector Asset, adds Material icons as XML. |
| `Build.VERSION.SDK_INT` | Android version of the device (theme uses it). |
| `IOException` | I/O error (missing cover, unreadable file). |
| `Handler(Looper.getMainLooper())` | Posts work to the main thread's message queue. `handler.post { }` runs the block a bit later, after the current work (e.g. listener dispatch) finishes (`Dispatcher.BeginInvoke`). |
| `adb logcat -d -b crash` | Print the crash log buffer of the connected device (stack trace of the last crashes). |
| `enableEdgeToEdge()` | Draw behind status/navigation bars. |
| `setContent { }` | Put a Compose UI into the activity. |

## 5. Compose runtime and state

| Name | Meaning |
|---|---|
| `@Composable` | Function that describes UI; re-run ("recomposed") when the state it reads changes. |
| Recomposition | Compose calling the composable again with new state. |
| `remember { }` | Keep a value across recompositions (lost when leaving composition). |
| `remember(key1, key2) { }` | Recompute when a key changes. |
| `var copy by remember(src) { mutableStateOf(src) }` | Local editable copy of a parameter, reset whenever a new `src` arrives (e.g. list reordered while dragging, reset by the database). |
| `mutableStateOf(x)` | Observable value; writing it triggers redraw of readers. |
| `var x by remember { mutableStateOf(...) }` | Standard pattern for local UI state. |
| `mutableStateOf<Track?>(null)` | Explicit type when the initial value is `null` (otherwise the type would be `Nothing?`). |
| `mutableStateOf<QueueSource>(QueueSource.Library)` | Same for a sealed type: without `<QueueSource>` the type would be `Library` only and other variants couldn't be assigned. |
| Composable calls only in composables | `remember` etc. can't be called inside `LaunchedEffect { }` (a coroutine) or `onClick` — "@Composable invocations can only happen from the context of a @Composable function". |
| `getValue` / `setValue` imports | Needed for `by` with state. |
| `@Stable` | Promise to Compose that changes are reported through state. |
| `LaunchedEffect(keys) { }` | Run a coroutine when entering composition / when keys change; cancelled on leave. `Unit` key = once. |
| `snapshotFlow { state.x }` | Turn Compose state reads into a `Flow`: the block re-runs and emits when the state it read changes. |
| `rememberCoroutineScope()` | Scope for starting coroutines yourself, e.g. from `onClick`; cancelled when the composable leaves. |
| `flow.collectAsState(initial)` | `Flow` → Compose `State`; recomposes on each new value. Wrap the flow in `remember { }` so it isn't recreated. |
| `DisposableEffect(key) { ... onDispose { } }` | Effect with cleanup when leaving composition. |
| `produceState(initial, key) { value = ... }` | Coroutine that produces state; returns `State`, read `.value`. |
| `LocalContext.current` | Current `Context` inside a composable. |
| `rememberLauncherForActivityResult(contract) { result -> }` | Launcher for system dialogs (permission, file save/open). `launcher.launch(...)`. Must be declared after everything its lambda uses. |
| `rememberLazyListState()` | Scroll state of a `LazyColumn`. |
| `remember(key) { LazyListState() }` | New scroll state each time `key` changes (e.g. per opened playlist). |
| Effect placement | Effects live as long as their composable. A tab screen leaves composition when switched away, so an effect that must always run belongs in a parent that's always composed. |
| `BackHandler(onBack = ...)` | Intercept system back (gesture/button) while this composable is shown (`androidx.activity.compose`). |
| State hoisting | State goes down as parameters, events go up as callbacks. |
| Slot parameter `dragHandle: (@Composable () -> Unit)? = null` | Caller passes a piece of UI to draw inside the component; `null` = nothing. Same idea as `title = { Text(...) }` in `AlertDialog`. |
| Parameter order convention | Required parameters → `modifier: Modifier = Modifier` → optional parameters. |
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
| `LazyListState` | `layoutInfo.visibleItemsInfo` (index, size), `viewportStartOffset/EndOffset`, `animateScrollToItem(i, offset)` (suspend, animated). |
| `listState.requestScrollToItem(i, offset)` | Not suspend: "start from item `i` on next measure". Works while the list isn't shown, with no animation. |
| `layoutInfo.totalItemsCount` | `0` until the list has been measured with items (a fresh `LazyListState` has no layout). |
| Item fully visible | `item.offset >= viewportStartOffset && item.offset + item.size <= viewportEndOffset`; `visibleItemsInfo` also lists items cut by the edge. |
| `items(list, key = { it.id }) { }` | Stable key per row, so rows keep their state when the list changes. Needs `import androidx.compose.foundation.lazy.items`. |
| `PaddingValues(bottom = 140.dp)` | Padding description (used for `contentPadding`, Scaffold `innerPadding`). |
| `Alignment.Center`, `CenterVertically`, `CenterHorizontally`, `BottomCenter` | Alignment options. |
| `Arrangement.SpaceBetween` | First at start, last at end, space between. |
| `Arrangement.SpaceEvenly` | Equal gaps everywhere, including edges. |
| `Dp`, `.dp` | Density-independent pixels: `16.dp`. |
| `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` | Height of the system navigation/gesture bar as `Dp`. |

### Reorderable (`sh.calvin.reorderable`)
| Name | Meaning |
|---|---|
| `rememberReorderableLazyListState(listState) { from, to -> }` | Wraps a `LazyListState`; the lambda runs each time the dragged item passes another (several times per drag). `from.index` / `to.index`. |
| `ReorderableItem(state, key) { isDragging -> }` | Makes a `LazyColumn` row movable; `key` must match the `items(key = ...)` key. |
| `Modifier.draggableHandle(onDragStarted, onDragStopped)` | Drag starts only from this element (inside `ReorderableItem`); `onDragStopped` = finger released, the place to save. |

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
| `combinedClickable(onClick, onLongClick)` | Tap + long press in one modifier; `onLongClick = null` = no long press. |
| `background(color)` | Fill background. |
| `clip(shape)` | Cut content to a shape. |
| `RoundedCornerShape(4.dp)` | Rounded rectangle shape. |
| `alpha(0.5f)` | Opacity of the whole element, `0f`–`1f` (WPF `Opacity`). `import androidx.compose.ui.draw.alpha`. `f` = `Float`; `0.5` alone is a `Double`. |

## 8. Compose UI elements

| Name | Meaning |
|---|---|
| `Text(text, style, color, maxLines, overflow, textAlign)` | Text label. |
| `TextOverflow.Ellipsis` | Cut overflow with "…". |
| `TextAlign.Center` | Center text lines. |
| `Font(R.font.x, FontWeight.Medium, FontStyle.Italic)` | One font file and the weight/style it covers. |
| `FontFamily(Font(...), Font(...))` | One typeface in several variants; Compose picks a `Font` by weight + style. Different typefaces = different families. A variable font is listed once per weight. |
| `TextStyle.copy(fontFamily = ...)` | Same style with one field changed (data class `copy`). |
| `Image(bitmap, contentDescription, contentScale, modifier)` | Draw an `ImageBitmap`. `contentDescription = null` = decorative. |
| `Icon(painter, contentDescription, tint)` | Small tinted icon (Material 3); color comes from the surrounding component unless `tint` is set, so the XML `android:tint` doesn't matter. `contentDescription` = what TalkBack reads. |
| `painterResource(R.drawable.x)` | Load a drawable resource (e.g. vector XML) as a `Painter`. |
| `ContentScale.Crop` | Fill the box, trim overflow, keep proportions. |
| `ImageBitmap`, `Bitmap.asImageBitmap()` | Compose bitmap type / conversion from Android `Bitmap`. |

## 9. Material 3

| Name | Meaning |
|---|---|
| `MaterialTheme` | Provides `colorScheme` and `typography` to children. |
| `MaterialTheme.colorScheme.primary/onSurface/surfaceVariant` | Theme colors. |
| `MaterialTheme.typography.displayLarge/headlineMedium/headlineSmall/bodyLarge/bodyMedium/bodySmall/labelSmall` | Text styles. |
| `Typography()` | Material 3 default text styles (15 roles: display/headline/title/body/label × Large/Medium/Small); `Typography(titleLarge = ...)` overrides chosen ones. |
| `dynamicDarkColorScheme(context)` / `dynamicLightColorScheme` | Color scheme from the wallpaper (Android 12+). |
| `rememberDynamicColorScheme(seedColor, isDark, isAmoled)` | MaterialKolor: full scheme from one seed color. |
| `YorozuyaTheme(theme, mode, amoled) { }` | Our theme (`ui/theme`): wallpaper colors or a MaterialKolor seed scheme, light/dark/system, optional pure black; `typography = Typography` from `Type.kt`. |
| `Scaffold(bottomBar = { }, snackbarHost = { }) { innerPadding -> }` | Screen skeleton with slots; `innerPadding` = space bars take. |
| `SnackbarHostState` | Holds the shown message: `showSnackbar(text, duration)` (suspend), `currentSnackbarData?.dismiss()`. |
| `SnackbarHost(state) { data -> }` | Draws messages; optional lambda = custom look, `data.visuals.message` = text. |
| `SnackbarDuration.Short/Long/Indefinite` | How long a message stays (Short ≈ 4 s). |
| `NavigationBar { NavigationBarItem(selected, onClick, icon, label) }` | Bottom tab bar (80dp by default, system inset included in its height). `label = null` → icon only. |
| `Surface(color, contentColor, shape, tonalElevation)` | Background container; `contentColor` colors text inside; elevation tints color. |
| `Surface(onClick = { }, ...)` | Tappable surface overload: ripple + "button" semantics. Children's own buttons still get their taps. |
| `LinearProgressIndicator(progress = { 0.5f }, modifier)` | Read-only progress line; `progress` is a lambda read at draw time. |
| `RoundedCornerShape(50)` (Int) | Corner radius in percent of the size: 50 = fully round ends (pill). With `.dp` it's a fixed radius. |
| `colorScheme.inverseSurface/inverseOnSurface` | Contrasting colors for bubbles (dark in light theme). |
| `TextButton(onClick = { action }) { Text(...) }` | `onClick` runs once on tap; trailing `{ }` is what's drawn, runs on every recomposition - no actions there. |
| `TextButton(onClick) { }` | Flat button with content. |
| `IconButton(onClick, modifier = Modifier.size(64.dp)) { Icon(..., modifier = Modifier.size(48.dp)) }` | Round tappable area (48dp by default) for an icon; size on the button = touch area, on the icon = picture. |
| `AlertDialog(onDismissRequest, title, text, confirmButton, dismissButton)` | Compose dialog (`androidx.compose.material3`, **not** `android.app.AlertDialog`). |
| `OutlinedTextField(value, onValueChange, singleLine, placeholder)` | Text input with outline; `value` comes from state, `onValueChange = { name = it }`. |
| `TextButton(onClick, enabled = cond)` | Disabled (grey, not tappable) when `enabled` is false. |
| `Slider(value, onValueChange, onValueChangeFinished)` | Draggable 0..1 bar. |
| `Checkbox(checked, onCheckedChange)` | Checkbox. |
| `RadioButton(selected, onClick)` | One-of-many choice. |
| `Switch(checked, onCheckedChange)` | On/off toggle. |
| `SingleChoiceSegmentedButtonRow { SegmentedButton(selected, onClick, shape = SegmentedButtonDefaults.itemShape(i, n)) { } }` | Connected row of options, one selected. |
| `HorizontalDivider()` | Horizontal line. |
| `Box { TextButton(onClick = { expanded = true }) { }; DropdownMenu(expanded, onDismissRequest = { expanded = false }) { DropdownMenuItem(text = { Text(..) }, onClick = { }) } }` | Popup menu. It opens next to its **parent**, so the button and the menu share one `Box`. `onDismissRequest` = tap outside. |

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
| `moveMediaItem(from, to)` | Move an item inside the queue; the current track keeps playing. With shuffle on, the moved item gets a new random shuffle spot. |
| `shuffleModeEnabled`, `repeatMode` (`REPEAT_MODE_OFF/ALL/ONE`) | Modes. |
| `C.TIME_UNSET` | Negative "unknown" duration before prepare. |
| `Player.Listener` | `onIsPlayingChanged`, `onMediaItemTransition`, `onShuffleModeEnabledChanged`, `onRepeatModeChanged`, `onTimelineChanged` (queue set/added/removed/moved). |
| Changing the queue inside a listener callback | Don't: events raised during dispatch are queued, `MediaSession` sees a half-updated state and crashes. Defer with `Handler.post { }`. |
| `addMediaItems(index, items)`, `addMediaItems(items)`, `removeMediaItems(from, to)` | Insert at a position / at the end, remove `[from, to)`. Empty lists and ranges are fine. |
| `seekToDefaultPosition(index)` | Jump to another queue item, from its start. |
| `C.INDEX_UNSET` | `-1`: "no index given". |
| `player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))` | ExoPlayer-only: shuffle mode plays in list order (we reorder the queue ourselves). |
| `MediaItem.localConfiguration` | URI etc.; present in the service's player, stripped on the `MediaController` side. |
| `Callback.onSetMediaItems(session, controller, items, startIndex, startPositionMs)` | Runs when a controller calls `setMediaItems`, **before** the player gets the items; return other items/index via `super.onSetMediaItems(...)` or `Futures.immediateFuture(MediaItemsWithStartPosition(items, index, posMs))`. |
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

## 11. Room 3 (database, `androidx.room3`)

| Name | Meaning |
|---|---|
| `@Entity(tableName = "x") data class` | One table; each property = column. |
| `@PrimaryKey` | Unique key column; duplicates are rejected. |
| `@Dao interface` | Database access functions; Room generates the implementation. |
| `@Query("SELECT ... WHERE id = :id")` | SQL for the function below it; `:id` = function parameter. Checked at build time. |
| `@Insert(onConflict = OnConflictStrategy.IGNORE)` | Insert a row; IGNORE = skip if the key exists. |
| `@Insert suspend fun x(row): Long` | Returns the new row id. |
| `@Insert(onConflict = IGNORE)` returning `Long` | `-1` if the row was skipped because the key exists. |
| `@PrimaryKey(autoGenerate = true) val id: Long = 0` | Database assigns the id on insert; `0` is a placeholder. |
| `@Entity(primaryKeys = ["a", "b"])` | Composite key: the pair must be unique. |
| `ForeignKey(entity, parentColumns, childColumns, onDelete = ForeignKey.CASCADE)` | Link to another table; deleting the parent deletes child rows. |
| `@Transaction` on a DAO function with a body | Several queries run as one atomic step. |
| `COALESCE(a, b)` (SQL) | First non-null value. `COALESCE(MAX(position), -1) + 1` → next position at the end; `COALESCE(MIN(position), 1) - 1` → position above the top (negatives are fine). Both give 0 for an empty table. |
| `ORDER BY position, addedAt DESC` (SQL) | Sort by the first column; equal values are sorted by the second (`DESC` = newest first). |
| `@ColumnInfo(defaultValue = "0")` | SQL `DEFAULT` for the column; needed when adding a non-null column to an existing table (rows that already exist get it). The Kotlin `= 0` only applies to new objects. |
| `UPDATE t SET col = :v WHERE ...` in `@Query` | Write query; `suspend fun` returning nothing. |
| `@Database(entities = [...], version = 2, autoMigrations = [AutoMigration(from = 1, to = 2)])` | Database class: `abstract class X : RoomDatabase()`, `abstract fun xDao()`. Bump `version` on schema change; `AutoMigration` diffs the exported JSON schemas (both must exist). Keep **all** steps in the list (`1→2`, `2→3`, ...): an old install is upgraded step by step. |
| `Room.databaseBuilder<X>(context, name)` | Create the database; `.setDriver(BundledSQLiteDriver())`, `.setQueryCoroutineContext(Dispatchers.IO)`, `.build()`. |
| `room3 { schemaDirectory(...) }` | Export table layout to JSON (commit it; used for migrations). |
| Function returning `Flow<T>` | Observable query, not `suspend`. One-off reads/writes are `suspend`. |

### kotlinx.serialization (JSON)
| Name | Meaning |
|---|---|
| `@Serializable data class X(...)` | Compiler plugin generates conversion to/from JSON for this class. |
| `Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = true }` | Configured instance. **By default a field equal to its default value is not written**; `ignoreUnknownKeys` = don't fail on fields from a newer format. |
| `json.encodeToString(obj)` / `json.decodeFromString<X>(text)` | Object → JSON text / back. |
| `import kotlinx.serialization.json.Json` | `Json` is in its own package (JSON is one of several formats). |

## 12. Gradle

| Name | Meaning |
|---|---|
| `gradle/libs.versions.toml` | Version catalog: `[versions]` and `[libraries]` aliases, used as `libs.xxx` in build files. |
| `app/build.gradle.kts` | App module build script; `dependencies { implementation(...) }`. |
| `platform(libs.androidx.compose.bom)` | Compose BOM: supplies one matching set of versions, so Compose libraries are listed without versions. Never remove it. |
| Gradle sync | Android Studio re-reads build files (elephant icon / "Sync Now"). Needed after every build file change: Gradle in the terminal may build fine while the IDE still shows "Unresolved reference". |
| `group:artifact:version` | Maven coordinate; group becomes the folder path on the repository (`androidx.room3` → `androidx/room3/`). |
| `[plugins]` + `alias(libs.plugins.x)` | Gradle plugin from the catalog; `apply false` in the root file, applied in `app`. |
| KSP, `ksp(libs.x)` | Kotlin Symbol Processing: generates code from annotations at build time (Room). |
| `org.jetbrains.kotlin.plugin.serialization` | kotlinx.serialization compiler plugin; `version.ref = "kotlin"` (must match Kotlin, like `kotlin-compose`). |

## 13. Project code

| File | Item | Purpose |
|---|---|---|
| `Track.kt` | `data class Track(id, uri, title, artist?, album?, duration, relativePath, fileName)` | One song from MediaStore; `relativePath` + `fileName` identify the file across devices. |
| `TrackLoader.kt` | `loadTracks(context): List<Track>` | Query MediaStore for music. |
| `Tabs.kt` | `enum class Tab(label, icon)` | `QUEUE`, `NOW_PLAYING`, `PLAYLISTS`, `STORAGE`, `DOWNLOADER`; icon = `R.drawable.ic_*`. |
| `MainActivity.kt` | `MainActivity` | Entry point; `rememberThemeSettings()` + `YorozuyaTheme(theme, mode, amoled) { AppScaffold(themeSettings) }`. |
| | `AppScaffold(themeSettings)` | Root UI: tabs, permission, `allTracks`, `player`, `listState` + auto-scroll effect (also for `detailListState`), `library`, filtered `tracks`, snackbar + `showMessage`, `favoriteIds`, `toggleFavorite(id)`, `actionTrack` + `TrackActionsDialog`, `playlists`, `countsById` / `favoritesCount` (only tracks in the library), `openedList`, `openedTracks`, `queueTracks` (real queue), `queueTabSource` + `queueSourceTracks` + `queueTabTracks` + `queueSources` (Queue tab dropdown), `importLauncher` / `exportLauncher`; `bottomBar` = mini player + `NavigationBar`. |
| `QueueScreen.kt` | `QueueScreen(player, tracks, source, sources, onSourceChange, hasPermission, listState, showMessage, onTrackLongClick)` | Queue tab: list dropdown + shuffle/repeat buttons, the chosen list (real queue if it's playing, dimmed if not). Auto-scroll lives in `AppScaffold`. |
| `QueueSource.kt` | `sealed interface QueueSource { Library, Favorites, Playlist(id) }` | Which list the queue was started from / is picked in the Queue dropdown. |
| `FavoriteScreen.kt` | `FavoriteScreen(player, tracks, listState)` | Old favorites list, no longer used (replaced by `PlaylistDetailScreen`). |
| | `Placeholder(name)` | Stub screen for unfinished tabs. |
| `TrackList.kt` | `TrackList(tracks, currentTrackId, isPlaying, listState, onTrackClick, onTrackLongClick?, bottomPadding)` | Scrollable track list; `bottomPadding` is unused now. |
| | `TrackRow(track, isCurrent, isPlaying, onClick, onLongClick?, dragHandle?)` | One row: cover + title/artist (+ optional drag handle at the end); tap + long press. |
| | `LazyListState.centerOn(index, animate)` | Scroll so the item is centered; no-op if fully visible. Used for Queue and the opened playlist. |
| `ReorderableTrackList.kt` | `ReorderableTrackList(tracks, currentTrackId, isPlaying, listState, onTrackClick, onTrackLongClick, onMove, onDragEnd)` | Track list with drag handles; reports moves, owns no order itself. |
| `NowPlayingBar.kt` | `NowPlayingBar(track, isPlaying, onPlayPauseClick, onPreviousClick, onNextClick, onClick, player)` | Mini player in `bottomBar`: thin progress line, cover, title, prev/play/next icons; tap opens "Now". Hidden on the "Now" tab. |
| | `formatTime(ms)` | `83500` → `"1:23"`. |
| `SeekBar.kt` | `PlayerSeekBar(player, modifier)` | Slider + monospace time labels with own drag state; "Now" tab only. |
| `NowPlayingScreen.kt` | `NowPlayingScreen(player, track?, showMessage, isFavorite, onToggleFavorite)` | "Now" tab: big cover, info, favorite button, slider, all controls. |
| `Artwork.kt` | `rememberArtwork(uri, sizePx): ImageBitmap?` | Load embedded cover off the main thread. |
| | `ArtworkThumb(uri, size, sizePx, modifier)` | Small rounded cover with "♪" fallback. |
| `FolderPicker.kt` | `buildFolderList(tracks, includeSubfolders)` | `(path, count)` pairs for the Folders tab. |
| | `FoldersScreen(allTracks, library)` | "Folders" tab: subfolders checkbox + folder checkbox list. |
| `LibraryFilter.kt` | `class LibraryFilter(prefs)` | Folder filter state: `selectedPaths`, `includeSubfolders` (both `private set`). |
| | `togglePath(path)`, `toggleSubfolders()` | Change the filter and save to prefs immediately. |
| | `filter(tracks)` | Apply the filter to a track list. |
| | `rememberLibraryFilter()` | Create once, keep across recompositions. |
| `PlayerConnection.kt` | `class PlayerState` | UI-side playback state: `controller`, `currentTrackId`, `isPlaying`, `shuffleEnabled`, `repeatMode`, `position`, `duration`, `queueIds` (real queue order), `activeSource` (in memory only). |
| | `syncFrom(p)` | Copy current state after connecting. |
| | `playQueue(tracks, startIndex, source)` | Replace queue, play, remember `activeSource`. |
| | `playOrToggle(track, queue, source)` | List tap: same track + same source → pause/resume; same source → seek inside the queue; otherwise play the list from it. |
| | `moveInQueue(oldOrder, from, to)` | Mirror a playlist drag onto the live queue, only if the queue is exactly that playlist. |
| | `togglePlayPause()`, `next()`, `previous()`, `seekTo(ms)`, `toggleShuffle()`, `cycleRepeat()` | Controls. |
| | `rememberPlayerState()` | Connect to service, listen, poll position every 500 ms. |
| | `repeatModeName(mode)` | `"Off"` / `"All"` / `"One"` for button text and messages. |
| | `readQueueIds(p)` | Queue → list of MediaStore ids. |
| `PlaybackService.kt` | `PlaybackService : MediaSessionService` | Owns `ExoPlayer` and `MediaSession`, notification, "Close" button. |
| | `saveState()` / `restoreState(player)` | Persist queue, index, position, shuffle, repeat, original order in prefs `playback_state`. `restoreState` runs before `addListener`. |
| | `enableShuffle(p)` / `disableShuffle(p)` | Reorder the real queue / restore `originalOrder`; called via `mainHandler.post` from `onShuffleModeEnabledChanged`. |
| | `replaceAround(p, newOrder, newIndex)` | Make the queue equal to `newOrder` without touching the playing item. |
| | `shuffledWithFirst(items, firstIndex)`, `readItems(p)`, `idsToItems(text)` | Helpers: chosen item first + rest shuffled / queue items / `"12,57"` → items. |
| | `CallBack` | Session callback: allows and handles `stopCommand`; `onSetMediaItems` shuffles a new list when shuffle is on. |
| `FavoriteEntity.kt` | `FavoriteEntity(trackId, addedAt, position)` | Row of table `favorites`. |
| `FavoriteDao.kt` | `observeIds()` (by position), `observeIdsNewestFirst()` (unused), `add(fav)`, `remove(id)`, `topPosition()`, `setPosition()`, `getAll()` (for backup), `reorder(ids)` | Favorites queries. |
| `YorozuyaDatabase.kt` | `YorozuyaDatabase.get(context)` | Singleton Room database `yorozuya.db` (version 3). |
| `PlaylistEntity.kt` | `PlaylistEntity(id, name, createdAt)` | Row of table `playlists`. |
| `PlaylistTrackEntity.kt` | `PlaylistTrackEntity(playlistId, trackId, position)` | Row of table `playlist_tracks`; cascade-deleted with its playlist. |
| `PlaylistDao.kt` | `observePlaylists()`, `createPlaylist()`, `deletePlaylist(id)`, `observeTrackIds(id)`, `topPosition(id)`, `bottomPosition(id)`, `insertTrack(row)`, `addTrack(): Boolean`, `removeTrack()`, `observeAllTracks()`, `rename()`, `getAllPlaylists()`, `getTrackIds(id)`, `setPosition()`, `reorder(id, ids)` | Playlist queries. |
| `PlaylistTarget.kt` | `sealed interface PlaylistTarget { Favorites, Custom(id) }` | Which list is opened on the Playlists tab. |
| `PlaylistsScreen.kt` | `PlaylistsScreen(playlists, favoritesCount, onOpen, onCreate, counts, onImport, onExport)` | Playlists tab: header with import/export buttons, Favorites + playlists with track counts + "New Playlist" dialog. |
| | `PlaylistNameDialog(title, confirmLabel, initialName, onConfirm, onDismiss)` | Name input dialog, shared by create and rename. |
| `PlaylistDetailScreen.kt` | `PlaylistDetailScreen(title, tracks, player, listState, onBack, onDelete?, onRemoveTrack, onRename?, onReorder?)` | Opened playlist: header with back/rename/delete, reorderable track list, `BackHandler`, long press → remove dialog. |
| `TrackActionsDialog.kt` | `TrackActionsDialog(track, isFavorite, playlists, onToggleFavorite, onAddTo, onDismiss)` | Long-press menu: favorite toggle + "Add to <playlist>". |
| `Backup.kt` | `BackupFile`, `BackupTrackRef`, `BackupPlaylist`, `BACKUP_VERSION`, `backupJson` | Backup file format (JSON) and its `Json` settings. |
| | `Track.toRef()`, `buildBackup(favoriteDao, playlistDao, tracksById)` | Database → `BackupFile`; ids missing from the library are skipped. |
| | `TrackMatcher(library).find(ref)` | Backup entry → current `Track?`: folder + name, then name alone, then tags (unique match only). |
| | `restoreBackup(backup, favoriteDao, playlistDao, library): RestoreResult(matched, missing, playlists)` | Merge a backup in: favorites on top, same-named playlists extended at the end. |
| `ui/theme/AppTheme.kt` | `enum AppTheme(label, seed?)`, `enum ThemeMode(label)` | Palettes (Wallpaper, Silver, Crimson, Forest) and light/dark/system. |
| `ui/theme/ThemeSettings.kt` | `ThemeSettings(prefs)`: `theme`, `mode`, `amoled`, `updateTheme()`, `updateMode()`, `toggleAmoled()`; `rememberThemeSettings()` | Theme choice saved in prefs `theme`. |
| `ui/theme/ThemePickerScreen.kt` | `ThemePickerScreen(settings)` | Theme/mode/AMOLED picker; temporarily on the "Get" tab. |
| `ui/theme/Type.kt` | `SpaceGroteskFamily`, `BogleFamily`, `JetBrainsMonoFamily`, `Typography` | Fonts: Bogle for display/headline, Space Grotesk for the rest, JetBrains Mono for time labels. |
| Prefs `playback_state` | `queue`, `index`, `position`, `shuffle`, `repeat`, `original` (ids before shuffle, empty when off) | Written by `PlaybackService`. |
| Prefs `library` | `paths` (string set), `subfolders` (bool) | Folder filter, read/written by `LibraryFilter`. |
| Prefs `theme` | `theme`, `mode` (enum names), `amoled` (bool) | Written by `ThemeSettings`. |
