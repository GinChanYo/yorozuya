# Yorozuya Project Decisions

Why this file: to record made decisions and reasons so as not to rethink in circles and forget arguments.

## What it is

An Android player for personal use. Local playback, synchronized lyrics, downloading audio from YouTube. Open source, distributed via GitHub Releases, not the Play Store.

Motivation: Namida met all needs, but updated rarely and some features broke. Want the same thing, but own and maintained.

## Stack

* Kotlin + Jetpack Compose
* Media3 (ExoPlayer) — playback, queue, shuffle, background service
* Room — favorites, playlists, metadata overrides
* kotlinx.serialization — backup file (JSON)
* MaterialKolor — color themes from one seed color
* Coil 3 — album art (requires a separate `coil-network-okhttp` artifact)
* youtubedl-android — downloading from YouTube
* LRCLIB — synchronized lyrics
* MusicBrainz + Cover Art Archive — online album art

`minSdk 33`. Reason: NewPipe/yt-dlp doesn't require desugaring on 33+, and no branching permission code is needed — `READ_MEDIA_AUDIO` exists since 33.

## Decisions

### yt-dlp instead of NewPipeExtractor

yt-dlp updates from within the app via `updateYoutubeDL()`. The extractor is fixed without rebuilding the APK — critical for a solo project.

### Keep file in original format without redecoding it, locally every format will be played. Converting to mp3 will be added as a feature "share as mp3" if necessary. 
### Intent ACTION_SEND instead of built-in WebView

Receiving links from other apps is handled by an intent filter in the manifest. In-app search uses `ytsearch20:` in yt-dlp, and data comes structured. WebView would provide an HTML page that would need scraping and would break with every YouTube redesign.

### Override table instead of rewriting tags

Bad metadata is fixed by writing to Room (track ID + corrected fields) rather than writing to the file. No write permissions needed, no tag library needed, user files are untouched, and everything is reversible. Side benefit: the same table fixes LRCLIB lyric searches.

### Album art search order

1. Embedded in the file — done in 0.1 via `ContentResolver.loadThumbnail(uri, Size, null)` instead of `MediaMetadataRetriever.embeddedPicture`: it reads the same embedded picture, but decodes it straight to the requested size (128px for a list row instead of the full cover), and needs no retriever object to open and release
2. `cover.jpg` / `folder.jpg` in the same folder
3. Network (MusicBrainz → MBID → Cover Art Archive)

MusicBrainz requires a meaningful User-Agent with the app name and repository link.

### Typography: one main typeface, one accent, one monospace

Space Grotesk for all title/body/label roles, Bogle only for display/headline, JetBrains Mono for time labels. Four typefaces were tried first and looked random together; roles differ by size and weight, as Material intends. Space Grotesk is a variable font, so one file gives real Regular/Medium/Bold instead of faked bold. Monospace for timestamps: in a proportional font "1" is narrower than "8", so a ticking label jumps in width.

### Mini player in `Scaffold`'s `bottomBar`

The mini player sits in `bottomBar` above `NavigationBar`, not on top of a screen. `Scaffold` then adds its height to `innerPadding`, so no screen needs a manual bottom padding, and the bar is on every tab except "Now" (which has the full controls). It shows only a thin progress line; seeking is done on "Now".

### Backup: what is saved and how tracks are found again

* **Content:** Favorites and playlists with their order. Theme and folder filter are not included — they take a minute to set again by hand.
* **Track identity:** MediaStore ids are assigned anew on every phone, so each track is saved as folder + file name + title/artist/duration. On restore: folder + file name → file name alone → tags (title + artist + duration in seconds). The last two accept only a single candidate: skipping a track is better than restoring the wrong one.
* **No 4th rule (same folder + exact duration).** It only covers "renamed an untagged file" (MediaStore puts the file name into `TITLE` when there is no tag, so name and "tag" change together) and adds another way to match the wrong file. The real fix is the metadata override table: with real tags the third rule works.
* **Where the file goes:** the system file dialog (SAF `CreateDocument` / `OpenDocument`), no storage permission. Not app storage — it would be deleted together with the app.
* **Format:** JSON via kotlinx.serialization (worked with AGP 9; the fallback was the built-in `org.json`). A `version` field (`BACKUP_VERSION`) at the top so older files can still be read after the format changes; `encodeDefaults = true`, otherwise a field equal to its default — the version included — isn't written at all.
* **Merge, not replace.** Nothing is deleted. Existing favorites are skipped, a playlist with the same name is extended at the end instead of duplicated, so importing twice changes nothing and a re-import repairs a playlist after a renamed file. Replace would need a confirmation and could still wipe data by mistake.

### Rows for missing tracks are kept

When a file disappears from the library (renamed, SD card removed), its rows in `favorites` / `playlist_tracks` stay. Put the card back or rename the file back, and they work again. Only the display filters them: lists and track counts show tracks present in the library.

### Shuffle reorders the real queue (in the service)

ExoPlayer's built-in shuffle changes only the play order, not the queue, so the UI can't show it, and a custom `ShuffleOrder` can't be set through `MediaController`. Instead, ExoPlayer's shuffle is neutralized (`UnshuffledShuffleOrder`) and `PlaybackService` reorders the queue itself:

* On: current track first, the rest shuffled; a new order on every toggle.
* Off: the original order comes back without interrupting the current track. The original order is saved in prefs, so it survives a restart.
* A new list started while shuffle is on is shuffled at once, the tapped track first (`MediaSession.Callback.onSetMediaItems`).
* The queue is changed with `Handler.post`, never inside a player listener callback: `MediaSession` would see an inconsistent state and crash.

### Queue tab: one list at a time, picked from a dropdown

A dropdown at the top picks "All songs", Favorites or a playlist. The list that is playing (`QueueSource`, remembered in `PlayerState`) shows the real, possibly shuffled, queue; any other list shows its own order and is dimmed. Tapping a track in the playing list jumps to it inside the queue instead of restarting the list, so the shuffled order is kept.

### GPLv3

A consequence of using certain libraries. Matches the desired outcome: open source, forks allowed, but must remain open.

## Version 0.1 — done (2026-11-15)

Planned:

* file scanning via MediaStore ✅
* track list ✅
* background playback with notification ✅
* queue ✅
* shuffle ✅

Definition of done: I listen to music through this app myself. ✅

### Done beyond the plan

From IDEAS.md:

* Library filter by folder, with optional subfolders — now its own "Folders" tab (IDEAS #2)
* Tapping the notification opens the app and shows the playing song (IDEAS #4)
* "Close" button in the notification that stops playback and the service (IDEAS #5)

Not in any list, came up along the way:

* Previous / Next, repeat mode (Off → All → One)
* Seek bar with position and duration
* Queue, current track, position, shuffle and repeat restored after a restart
* Audio focus and pause on headphones unplug
* Mini player, auto-scroll to the playing track
* Bottom navigation with five tabs; "Now" tab with full controls
* Embedded cover art in the list, mini player and "Now" tab (step 1 of "Album art search order")

## Version 0.2 — library and cover art (in progress)

The order shifted: cover art (planned for 0.3) was started right after 0.1, so it is finished first, together with what the library still lacks.

* ✅ Embedded cover art
* ✅ "Folders" tab
* `cover.jpg` / `folder.jpg` fallback (step 2 of "Album art search order")
* Coil 3 for loading and caching artwork (now every row re-reads its cover after scrolling back)
* Title/artist/artwork in the notification for untagged files (`MediaMetadata` on `MediaItem`)
* Open issues from DevLog: empty filter result hint, poll loop runs while paused, `STATE_ENDED` after the last track, `loadTracks` on the main thread

### Done beyond the plan

From "Postponed" and IDEAS.md:

* Favorites and playlists on the "Lists" tab (Room came earlier than 0.4): create, rename, delete, add/remove tracks, track counts, drag-and-drop order
* Backup and restore of Favorites and playlists (IDEAS #12, see "Backup: what is saved and how tracks are found again")

Not in any list:

* Theme system: wallpaper or seed palettes, light/dark/system, AMOLED (temporary picker on the "Get" tab until a Settings screen exists)
* Custom fonts (see "Typography")
* Mini player on every tab, tap opens "Now"; auto-centering on the playing track in playlists too
* Vector icons instead of emoji in player controls
* Shuffle that reorders the visible queue, original order restored on "off" and after a restart (see "Shuffle reorders the real queue")
* Queue tab dropdown: All songs / Favorites / playlists, playing list shows the real queue, others dimmed (see "Queue tab: one list at a time")

## Version 0.3 — synchronized lyrics

* LRCLIB search and synced display on the "Now" tab
* Manual lyrics offset adjustment

## Version 0.4 — metadata fixing and online cover art

* Room override table (see "Override table instead of rewriting tags"); also improves LRCLIB matches
* Online cover art: MusicBrainz → Cover Art Archive (step 3 of "Album art search order")

## Postponed

* YouTube downloading (0.5) — "Get" tab stays a placeholder until then
* Settings screen — theme picker and backup buttons move there
