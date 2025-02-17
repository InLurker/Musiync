package com.metrolist.music.common.enumerated

enum class DataLayerPathEnum(val path: String) {
    SONG_INFO("/song_info");

    companion object {
        fun fromPath(path: String): DataLayerPathEnum? {
            return entries.firstOrNull { it.path == path }
        }
    }
}