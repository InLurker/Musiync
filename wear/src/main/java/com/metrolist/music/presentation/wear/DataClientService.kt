package com.metrolist.music.presentation.wear

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.metrolist.music.common.enumerated.DataLayerPathEnum
import com.metrolist.music.common.models.MusicState
import com.metrolist.music.common.models.TrackInfo
import com.metrolist.music.presentation.data.MusicRepository
import com.metrolist.music.presentation.helper.cacheInCoil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
@SuppressLint("VisibleForTests")
class DataClientService : WearableListenerService() {
    @Inject
    lateinit var musicRepository: MusicRepository

    private lateinit var dataClient: DataClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                            val dataItem = DataMapItem.fromDataItem(event.dataItem).dataMap
                            serviceScope.launch {
                                try {
                                    processQueueResponse(dataItem)
                                } catch (e: Exception) {
                                    Log.e("WearDataListenerService", "Error processing queue response", e)
                                }
                            }
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

    private suspend fun processQueueResponse(dataMap: DataMap) {
        val hash = dataMap.getInt("queueHash")
        Log.d("WearDataListenerService", "Received queue response with hash: $hash")

        val queue = dataMap.getDataMap("trackList")?.let { extractTrackInfoFromDataMap(it) }
        musicRepository.updateQueue(hash, queue)
        dataMap.getDataMap("artworkAssets")?.let { extractArtworkAssetsFromDataMap(it, this@DataClientService) }
    }

    private fun extractTrackInfoFromDataMap(tracksDataMap: DataMap): Map<Int, TrackInfo> {
        val queue = mutableMapOf<Int, TrackInfo>()
        for (key in tracksDataMap.keySet()) {
            val index = key.toIntOrNull() ?: continue
            val trackDataMap = tracksDataMap.getDataMap(key)
            queue[index] = TrackInfo(
                trackDataMap?.getString("trackName")!!,
                trackDataMap?.getString("artistName")!!,
                trackDataMap?.getString("albumName")!!,
                trackDataMap?.getString("artworkUrl")!!
            )
        }
        return queue
    }

    private suspend fun extractArtworkAssetsFromDataMap(
        artworkDataMap: DataMap,
        context: Context
    ) {
        for (key in artworkDataMap.keySet()) {
            artworkDataMap.getAsset(key)?.cacheInCoil(context, dataClient, key)
        }
    }
}
