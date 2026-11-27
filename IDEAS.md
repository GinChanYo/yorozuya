# Yorozuya Ideas

1. Add a bunch of queue filtering methods.
2. *Done in 0.1 (library filter by folder) → DECISIONS.md*
3. Play a directory as a separate song queue.
4. *Done in 0.1 (notification tap opens the playing song) → DECISIONS.md*
5. *Done in 0.1 ("Close" button in the notification) → DECISIONS.md*
6. One the first app setup app should ask user to point to the songs' directory. If user does not have any dedicated song directory on the phone, or hasn't stored them locally before, suggest creating custom "yorozuya_songs/" or something similar.
7. In addition to simple lyrics scraping, provide feature to translate/transliterate any non-latin song lyrics. For example for japanese songs use romaji. One option is to use Kuromoji, but if anything better appears, swap.
8. For lyrics there should be option to either create .lrc file, or embed them to the song directly in the app. For lyrics there will be dedicated directory. 
9. Add customizable navigation bar
10. Implement offline/online lyrics translation and save it locally if needed
11. Implement Achievement system
12. *Done in 0.2 (backup and restore of Favorites and playlists with their order, JSON file via the system file dialog) → DECISIONS.md*
13. Listening statistics: hours listened, most played tracks/artists. Needs a new table and events from the service on every track change; the base for the achievement system (#11). Library statistics (already loaded data) go to Settings instead.
