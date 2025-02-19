package com.metrolist.music.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import com.metrolist.music.models.TrackInfo
import kotlinx.coroutines.flow.Flow

// singleton
object CurrentTrackHolder {
    val _currentTrack = mutableStateOf<TrackInfo?>(null)

    var currentTrack: TrackInfo?
        get() = _currentTrack.value
        set(value) {
            _currentTrack.value = value
        }


    fun updateTrack(track: TrackInfo) {
        currentTrack = track
    }

    fun updateTrack(trackName: String?, artistName: String?, albumName: String?, artworkUrl: String?, artworkBitmap: Flow<Bitmap?>) {
        currentTrack = TrackInfo(trackName, artistName, albumName, artworkUrl, artworkBitmap)
    }


}