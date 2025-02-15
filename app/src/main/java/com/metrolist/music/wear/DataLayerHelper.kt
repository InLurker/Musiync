package com.metrolist.music.wear

import android.content.Context
import androidx.media3.common.MediaItem
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

class DataLayerHelper(private val context: Context) {

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val commandListener = AtomicReference<(String) -> Unit>()

    fun sendMediaInfo(media: MediaItem) {
        scope.launch {
            try {
                val putDataRequest = PutDataMapRequest.create("/media_info").apply {
                    dataMap.putString("trackName", media.mediaMetadata.title.toString())
                    dataMap.putString("artistName", media.mediaMetadata.artist.toString())
                    dataMap.putString("albumName", media.mediaMetadata.albumTitle.toString())
                    dataMap.putString("albumImage", media.mediaMetadata.artworkUri.toString())
                }.asPutDataRequest()

                // Send data through Data Layer
                dataClient.putDataItem(putDataRequest).addOnSuccessListener {
                    Timber.tag("DataLayerHelper").d("Media info sent via Data Layer: ${putDataRequest.data}")
                }.addOnFailureListener { e ->
                    Timber.tag("DataLayerHelper").e(e, "Failed to send media info via Data Layer")
                }
            } catch (e: Exception) {
                Timber.tag("DataLayerHelper").e(e, "Failed to send track info")
            }
        }
    }
}
