package com.metrolist.music.presentation.wear

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.WearableListenerService
import com.metrolist.music.common.enumerated.DataLayerPathEnum
import com.metrolist.music.repository.CurrentTrackHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton


class DataClientService: WearableListenerService() {
    val scope = CoroutineScope(Dispatchers.IO)
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.uri.path?.let {
                    Log.d("WearDataListenerService", "Received data item with path: $it")
                    val path = DataLayerPathEnum.fromPath(it)
                    when (path) {
                        DataLayerPathEnum.SONG_INFO -> {
                            val dataMap = DataMap.fromByteArray(event.dataItem.data!!)
                            val trackName = dataMap.getString("trackName")
                            val artistName = dataMap.getString("artistName")
                            val albumName = dataMap.getString("albumName")
                            val albumImage = dataMap.getString("albumImage")

                            Log.d("WearDataListenerService", "Received track info: $trackName by $artistName")
                            Log.d(albumImage.toString(), "Received album image: $albumImage")
                            CurrentTrackHolder.updateTrack(trackName, artistName, albumName, albumImage)
                        }
                        else -> {
                            Log.d("WearDataListenerService", "Unknown data item path: $it")
                        }
                    }
                }
            }
        }
    }
}
