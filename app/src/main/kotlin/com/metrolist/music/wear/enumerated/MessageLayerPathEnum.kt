package com.metrolist.music.wear.enumerated

enum class MessageLayerPathEnum (val path: String) {
    PLAYBACK_COMMAND("/playback_command"),
    REQUEST_STATE("/request_state"),
    REQUEST_QUEUE("/request_queue"),
    REQUEST_PLAYLIST_LIBRARY("/request_playlist_library"),
    REQUEST_PLAYLIST_SEARCH("/request_playlist_search"),
    PLAY_PLAYLIST("/play_playlist"),
    SEEK_TO_INDEX("/seek_to_index"),
    HEARTBEAT("/heartbeat");

    companion object {
        fun fromPath(path: String): MessageLayerPathEnum? {
            return entries.firstOrNull { it.path == path }
        }
    }
}
