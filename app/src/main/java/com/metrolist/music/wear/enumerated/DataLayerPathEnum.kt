package com.metrolist.music.wear.enumerated


enum class DataLayerPathEnum(val path: String) {
    SONG_INFO("/song_info"),
    ALBUM_INFO("/album_info"),
    PLAYLIST_INFO("/playlist_info");

    companion object {
        fun fromPath(path: String): DataLayerPathEnum? {
            return entries.firstOrNull { it.path == path }
        }
    }
}