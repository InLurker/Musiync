package com.metrolist.music.wear.model

import com.google.android.gms.wearable.DataMap

data class TrackInfo(
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val artworkUrl: String
)

fun TrackInfo.toDataMap(): DataMap {
    val dataMap = DataMap()
    dataMap.putString("trackName", trackName)
    dataMap.putString("artistName", artistName)
    dataMap.putString("albumName", albumName)
    dataMap.putString("artworkUrl", artworkUrl)
    return dataMap
}
