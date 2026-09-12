package com.metrolist.music.common.models

data class MusicState(
    val queueHash: Long,
    val queueSize: Int,
    val currentIndex: Int,
    val isPlaying: Boolean,
)