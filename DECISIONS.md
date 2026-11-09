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

### Try really hard to use mp3 format by default, and add support to other formats later

### Intent ACTION_SEND instead of built-in WebView

Receiving links from other apps is handled by an intent filter in the manifest. In-app search uses `ytsearch20:` in yt-dlp, and data comes structured. WebView would provide an HTML page that would need scraping and would break with every YouTube redesign.

### Override table instead of rewriting tags

Bad metadata is fixed by writing to Room (track ID + corrected fields) rather than writing to the file. No write permissions needed, no tag library needed, user files are untouched, and everything is reversible. Side benefit: the same table fixes LRCLIB lyric searches.

### Album art search order

1. Embedded in the file (`MediaMetadataRetriever.embeddedPicture`)
2. `cover.jpg` / `folder.jpg` in the same folder
3. Network (MusicBrainz → MBID → Cover Art Archive)

MusicBrainz requires a meaningful User-Agent with the app name and repository link.

### GPLv3

A consequence of using certain libraries. Matches the desired outcome: open source, forks allowed, but must remain open.

## Version 0.1

Only this, nothing extra:

* file scanning via MediaStore
* track list
* background playback with notification
* queue
* shuffle

Definition of done: I listen to music through this app myself.

## Postponed

* Synchronized lyrics (0.2)
* Metadata and cover art fixing (0.3)
* YouTube downloading (0.4)
* Manual lyrics offset adjustment