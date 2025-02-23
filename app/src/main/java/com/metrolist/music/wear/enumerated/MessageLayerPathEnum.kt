package com.metrolist.music.wear.enumerated

enum class MessageLayerPathEnum (val path: String) {
    PLAYBACK_COMMAND("/playback_command"),
    REQUEST_STATE("/request_state"),
    REQUEST_QUEUE("/request_queue");

    companion object {
        fun fromPath(path: String): MessageLayerPathEnum? {
            return entries.firstOrNull { it.path == path }
        }
    }
}