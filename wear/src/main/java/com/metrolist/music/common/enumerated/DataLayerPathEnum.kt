package com.metrolist.music.common.enumerated

enum class DataLayerPathEnum(val path: String) {
    CURRENT_STATE("/current_state"),
    QUEUE_RESPONSE("/queue_response"),;

    companion object {
        fun fromPath(path: String): DataLayerPathEnum? {
            return entries.firstOrNull { it.path == path }
        }
    }
}