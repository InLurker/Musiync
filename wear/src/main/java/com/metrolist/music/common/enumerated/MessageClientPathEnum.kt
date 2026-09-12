package com.metrolist.music.common.enumerated

enum class MessageClientPathEnum (val path: String) {
    PLAYBACK_COMMAND("/playback_command"),
    REQUEST_SEEK("/request_seek"),
    REQUEST_STATE("/request_state"),
    REQUEST_QUEUE("/request_queue");

    companion object {
        fun fromPath(path: String): MessageClientPathEnum? {
            return entries.firstOrNull { it.path == path }
        }
    }
}