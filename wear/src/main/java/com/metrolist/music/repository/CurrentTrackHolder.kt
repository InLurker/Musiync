package com.metrolist.music.repository

import androidx.compose.runtime.mutableStateOf
import com.metrolist.music.models.TrackInfo

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

    fun updateTrack(trackName: String?, artistName: String?, albumName: String?, albumImage: String?) {
        currentTrack = TrackInfo(trackName, artistName, albumName, albumImage)
    }


}