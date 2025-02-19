package com.metrolist.music.models

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

data class TrackInfo(
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val artworkUrl: String?,
    val artworkBitmap: Flow<Bitmap?>
)