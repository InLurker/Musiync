package com.metrolist.music.wear.model

import com.google.android.gms.wearable.Asset

class MusicQueue(
    val queueHash: String,
    val trackList: Map<Int, TrackInfo>,
    val artworkAssets: Map<String, Asset>
)