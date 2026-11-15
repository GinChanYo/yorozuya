package io.github.ginchanyo.yorozuya

import androidx.annotation.DrawableRes

// enum class - fixed set of values, like enum in C#. Each tab knows its own label and icon.
// icon - id of a vector drawable in res/drawable. R.drawable.ic_queue is a generated Int constant,
// not the image itself; painterResource() turns it into something Icon can draw.
// @DrawableRes - tells Android Studio this Int must be a drawable id, so passing e.g. a string id is flagged.
// "param:" - use-site target: "val" in a constructor is both a parameter and a property, so Kotlin asks
// where the annotation goes; on the parameter is enough, that's where the ids are passed in
// label - not shown under the icon (label = null in NavigationBarItem), used as contentDescription for screen readers
enum class Tab(val label: String, @param:DrawableRes val icon: Int){
    QUEUE("Queue", R.drawable.ic_queue),
    NOW_PLAYING("Now", R.drawable.ic_playing),
    PLAYLISTS("Lists", R.drawable.ic_playlists),
    STORAGE("Folders", R.drawable.ic_folder),
    DOWNLOADER("Get", R.drawable.ic_download)
}