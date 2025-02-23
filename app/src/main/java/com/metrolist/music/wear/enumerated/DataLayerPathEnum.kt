package com.metrolist.music.wear.enumerated


enum class DataLayerPathEnum(val path: String) {
    CURRENT_STATE("/current_state"),
    QUEUE_RESPONSE("/queue_response"),
    ALBUM_INFO("/album_info"),
    PLAYLIST_INFO("/playlist_info");

    companion object {
        fun fromPath(path: String): DataLayerPathEnum? {
            return entries.firstOrNull { it.path == path }
        }
    }
}