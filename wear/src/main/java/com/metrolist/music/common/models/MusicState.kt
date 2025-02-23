package com.metrolist.music.common.models

data class MusicState(
    val queueHash: Int,
    val queueSize: Int,
    val currentIndex: Int,
    val isPlaying: Boolean,
)