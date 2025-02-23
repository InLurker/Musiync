package com.metrolist.music.presentation.wear

import android.util.Log
import androidx.compose.runtime.collectAsState
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.metrolist.music.common.enumerated.DataLayerPathEnum
import com.metrolist.music.common.models.MusicState
import com.metrolist.music.common.models.TrackInfo
import com.metrolist.music.presentation.data.MusicRepository
import com.metrolist.music.presentation.helper.loadBitmapAsFlow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject


@AndroidEntryPoint
class DataClientService : WearableListenerService() {
    @Inject
    lateinit var musicRepository: MusicRepository

    private lateinit var dataClient: DataClient

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        dataClient = Wearable.getDataClient(this)
    }
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.uri.path?.let {
                    Log.d("WearDataListenerService", "Received data item with path: $it")
                    val path = DataLayerPathEnum.fromPath(it)
                    when (path) {
                        DataLayerPathEnum.CURRENT_STATE -> {
                            processCurrentState(event)
                        }
                        DataLayerPathEnum.QUEUE_RESPONSE -> {
                            processQueueResponse(event)
                        }
                        else -> {
                            Log.d("WearDataListenerService", "Unknown data item path: $it")
                        }
                    }
                }
            }
        }
    }

    private fun processCurrentState(dataEvent: DataEvent) {
        val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
        val musicState = MusicState(
            dataMap.getInt("queueHash"),
            dataMap.getInt("queueSize"),
            dataMap.getInt("currentIndex"),
            dataMap.getBoolean("isPlaying")
        )
        musicRepository.handleIncomingState(musicState)
    }


    //fun TrackInfo.toDataMap(): DataMap {
    //    val dataMap = DataMap()
    //    dataMap.putString("trackName", trackName ?: "")
    //    dataMap.putString("artistName", artistName ?: "")
    //    dataMap.putString("albumName", albumName ?: "")
    //    dataMap.putString("artworkUrl", artworkUrl ?: "")
    //    artworkBitmap?.let { dataMap.putAsset("artworkBitmap", it) }
    //    return dataMap
    //}

    private fun processQueueResponse(dataEvent: DataEvent) {
        val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
        val queueStart = dataMap.getInt("queueStart")
        val queueEnd = dataMap.getInt("queueEnd")
        val queue = mutableMapOf<Int, TrackInfo>()
        for (i in queueStart until queueEnd) {
            val trackDataMap = dataMap.getDataMap("track_$i")
            queue.put(
                i,
                TrackInfo(
                    trackDataMap?.getString("trackName"),
                    trackDataMap?.getString("artistName"),
                    trackDataMap?.getString("albumName"),
                    trackDataMap?.getString("artworkUrl"),
                    loadBitmapAsFlow(trackDataMap?.getAsset("artworkBitmap"), dataClient)
                )
            )
        }
        musicRepository.updateQueue(queue)
    }

}
