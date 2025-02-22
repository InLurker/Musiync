package com.metrolist.music.presentation.wear

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.metrolist.music.common.enumerated.DataLayerPathEnum
import com.metrolist.music.presentation.data.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                        DataLayerPathEnum.SONG_INFO -> {
                            processCurrentState(event)
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
        // Extract the data map immediately before the DataHolder is closed.
        val dataMap = DataMapItem.fromDataItem(dataEvent.dataItem).dataMap
        val trackName = dataMap.getString("trackName")
        val artistName = dataMap.getString("artistName")
        val albumName = dataMap.getString("albumName")
        val artworkUrl = dataMap.getString("artworkUrl")
        val artworkAsset = dataMap.getAsset("artworkAsset")

        Log.d("WearDataListenerService", "Received track info: $trackName by $artistName")

        // Now launch the coroutine and use the extracted values.
        serviceScope.launch {
            musicRepository.updateTrack(
                trackName,
                artistName,
                albumName,
                artworkUrl,
                loadBitmapAsFlow(artworkAsset)
            )
        }
    }


    fun loadBitmapAsFlow(asset: Asset?): Flow<Bitmap?> = flow {
        if (asset == null) {
            emit(null)
            return@flow
        }
        try {
            val assetFileDescriptor = withContext(Dispatchers.IO) {
                Tasks.await(dataClient.getFdForAsset(asset))
            }
            val inputStream = assetFileDescriptor.inputStream
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            emit(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            emit(null)
        }
    }
}
