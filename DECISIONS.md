# Yorozuya Project Decisions

Why this file: to record made decisions and reasons so as not to rethink in circles and forget arguments.

## What it is

An Android player for personal use. Local playback, synchronized lyrics, downloading audio from YouTube. Open source, distributed via GitHub Releases, not the Play Store.

Motivation: Namida met all needs, but updated rarely and some features broke. Want the same thing, but own and maintained.

## Stack

* Kotlin + Jetpack Compose
* Media3 (ExoPlayer) — playback, queue, shuffle, background service
* Room — albums, playlists, metadata overrides
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

## Version 0.3 — synchronized lyrics

* LRCLIB search and synced display on the "Now" tab
* Manual lyrics offset adjustment

## Version 0.4 — metadata fixing and online cover art

* Room override table (see "Override table instead of rewriting tags"); also improves LRCLIB matches
* Online cover art: MusicBrainz → Cover Art Archive (step 3 of "Album art search order")

## Postponed

* YouTube downloading (0.5) — "Get" tab stays a placeholder until then
* Playlists ("Lists" tab) — needs Room, so not before 0.4
