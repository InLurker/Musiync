package com.metrolist.music.presentation.wear

import android.annotation.SuppressLint
import android.graphics.Bitmap
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
import com.metrolist.music.presentation.data.PlaylistRepository
import com.metrolist.music.presentation.helper.cacheInCoil
import com.metrolist.music.datastore.PlaylistCollectionProto
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

    @Inject
    lateinit var playlistRepository: PlaylistRepository

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
                        DataLayerPathEnum.PLAYLIST_LIBRARY_RESPONSE -> {
                            val dataItem = DataMapItem.fromDataItem(event.dataItem).dataMap
                            serviceScope.launch {
                                try {
                                    processPlaylistResponse(dataItem, isLibrary = true)
                                } catch (e: Exception) {
                                    Log.e("WearDataListenerService", "Error processing playlist library response", e)
                                }
                            }
                        }
                        DataLayerPathEnum.PLAYLIST_SEARCH_RESPONSE -> {
                            val dataItem = DataMapItem.fromDataItem(event.dataItem).dataMap
                            serviceScope.launch {
                                try {
                                    processPlaylistResponse(dataItem, isLibrary = false)
                                } catch (e: Exception) {
                                    Log.e("WearDataListenerService", "Error processing playlist search response", e)
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
            dataMap.getLong("queueHash"),
            dataMap.getInt("queueSize"),
            dataMap.getInt("currentIndex"),
            dataMap.getBoolean("isPlaying")
        )
        musicRepository.handleIncomingState(musicState)
    }

    private suspend fun processQueueResponse(dataMap: DataMap) {
        val hash = dataMap.getLong("queueHash")
        Log.d("WearDataListenerService", "Received queue response with hash: $hash")
        val tracks = dataMap.getDataMap("trackList")
        val arts = dataMap.getDataMap("artworkAssets")
        Log.d(
            "WearDataListenerService",
            "QUEUE_RESPONSE sizes tracks=${tracks?.keySet()?.size ?: 0} assets=${arts?.keySet()?.size ?: 0}"
        )

        val queue = dataMap.getDataMap("trackList")?.let { extractTrackInfoFromDataMap(it) }
        val startIndex = dataMap.getInt("startIndex")
        val endIndexExclusive = dataMap.getInt("endIndexExclusive")
        val requestId = dataMap.getLong("requestId")
        val artworkLoader = arts?.let { assetsMap ->
            suspend { extractArtworkAssetsFromDataMap(assetsMap) }
        }
        musicRepository.updateQueue(
            hash = hash,
            trackDelta = queue,
            startIndex = startIndex,
            endIndexExclusive = endIndexExclusive,
            requestId = requestId,
            artworkDelta = artworkLoader
        )
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
        artworkDataMap: DataMap
    ): Map<String, Bitmap?> {
        val bitmaps = mutableMapOf<String, Bitmap?>()
        for (key in artworkDataMap.keySet()) {
            Log.d("WearDataListenerService", "Caching artwork key(len)=${key.length}")
            val bitmap = artworkDataMap.getAsset(key)?.cacheInCoil(this, dataClient, key)
            if (bitmap != null) {
                bitmaps[key] = bitmap
            }
        }
        return bitmaps
    }

    private suspend fun processPlaylistResponse(dataMap: DataMap, isLibrary: Boolean) {
        val payloadBytes = dataMap.getByteArray("payload") ?: return
        val proto = PlaylistCollectionProto.parseFrom(payloadBytes)
        val artworks = dataMap.getDataMap("artworkAssets")?.let { extractArtworkAssetsFromDataMap(it) } ?: emptyMap()
        if (isLibrary) {
            playlistRepository.handleLibraryResponse(proto, artworks)
        } else {
            playlistRepository.handleSearchResponse(proto, artworks)
        }
    }
}
