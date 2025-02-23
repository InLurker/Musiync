package com.metrolist.music.wear.model

import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataMap

data class TrackInfo(
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val artworkUrl: String?,
    val artworkBitmap: Asset?
)

fun TrackInfo.toDataMap(): DataMap {
    val dataMap = DataMap()
    dataMap.putString("trackName", trackName ?: "")
    dataMap.putString("artistName", artistName ?: "")
    dataMap.putString("albumName", albumName ?: "")
    dataMap.putString("artworkUrl", artworkUrl ?: "")
    artworkBitmap?.let { dataMap.putAsset("artworkBitmap", it) }
    return dataMap
}
