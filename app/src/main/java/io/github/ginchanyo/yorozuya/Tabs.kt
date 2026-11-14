package io.github.ginchanyo.yorozuya


// enum class - fixed set of values, like enum in C#. Each tab knows its own label.
// Icons come later; for now labels are enough to see the structure
enum class Tab(val label: String){
    QUEUE("Queue"),
    NOW_PLAYING("Now"),
    PLAYLISTS("Lists"),
    STORAGE("Folders"),
    DOWNLOADER("Get")
}