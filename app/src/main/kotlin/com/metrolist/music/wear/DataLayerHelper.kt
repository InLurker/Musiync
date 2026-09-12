package com.metrolist.music.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.media3.common.Timeline
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.wear.enumerated.DataLayerPathEnum
import com.metrolist.music.wear.helper.calculateSampleSize
import com.metrolist.music.wear.helper.transformBitmap
import com.metrolist.music.wear.model.MusicQueue
import com.metrolist.music.wear.model.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataLayerHelper @Inject constructor(context: Context) {

    @Volatile
    private var _playerConnection: PlayerConnection? = null
    var playerConnection: PlayerConnection?
        get() = _playerConnection
        set(value) {
            _playerConnection = value
            restartStateObservation()
        }

    @Volatile
    private var _servicePlayerConnection: PlayerConnection? = null
    var servicePlayerConnection: PlayerConnection?
        get() = _servicePlayerConnection
        set(value) {
            _servicePlayerConnection = value
            restartStateObservation()
        }

    private val activePlayerConnection: PlayerConnection?
        get() = playerConnection ?: servicePlayerConnection

    private var currentWindowIndexJob: Job? = null

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var previousHash: Long? = null
    private var pendingStateSendJob: Job? = null
    fun sendCurrentState() {
        scheduleStateSend(500)
    }

    private fun sendCurrentStateNow() {
        try {
            val connection = activePlayerConnection ?: return
            val putDataRequest = PutDataMapRequest.create(DataLayerPathEnum.CURRENT_STATE.path).apply {
                val queue = connection.queueWindows.value
                dataMap.putLong("queueHash", connection.currentQueueHash.value)
                dataMap.putInt("queueSize", queue.size)
                dataMap.putInt("currentIndex", connection.currentWindowIndex.value.coerceAtLeast(0))
                dataMap.putBoolean("isPlaying", connection.isPlaying.value)
            }.asPutDataRequest()

            dataClient.putDataItem(putDataRequest).addOnSuccessListener {
                Timber.Forest.tag("DataLayerHelper").d("Current state sent via Data Layer")
            }.addOnFailureListener { e ->
                Timber.Forest.tag("DataLayerHelper").e(e, "Failed to send current state via Data Layer")
            }
        } catch (e: Exception) {
            Timber.Forest.tag("DataLayerHelper").e(e, "Failed to send current state, e: $e")
        }
    }

    fun handleQueueRangeRequest(requestedIndices: List<Int>, callback: (MusicQueue?) -> Unit) {
        callback(getPaginatedQueue(requestedIndices))
    }

    private fun getPaginatedQueue(requestedIndices: List<Int>): MusicQueue? {
        activePlayerConnection?.let { connection ->
            val queue = connection.queueWindows.value
            val paginatedQueue = constructMusicQueue(queue, requestedIndices)
            return paginatedQueue
        } ?: run {
            return null
        }
    }

    private fun constructMusicQueue(
        queue: List<Timeline.Window>,
        requestedIndices: List<Int>
    ): MusicQueue {
        val trackList = mutableMapOf<Int, TrackInfo>()
        val artworkMap = mutableMapOf<String, Asset>()

        val validIndices = requestedIndices.distinct().filter { it in queue.indices }
        for (index in validIndices) {
            val window = queue[index]
            val mediaMetadata = window.mediaItem.mediaMetadata

            val title = mediaMetadata.title?.toString().orEmpty()
            val artist = mediaMetadata.artist?.toString().orEmpty()
            val albumTitle = mediaMetadata.albumTitle?.toString().orEmpty()
            val artworkUri = mediaMetadata.artworkUri?.toString().orEmpty()

            trackList[index] = TrackInfo(title, artist, albumTitle, artworkUri)

            if (!artworkMap.containsKey(artworkUri)) {
                mediaMetadata.artworkData?.let { data ->
                    generateResizedAssetFromByteArray(400, data)?.let { asset ->
                        artworkMap[artworkUri] = asset
                    }
                }
            }
        }
        return MusicQueue(activePlayerConnection?.currentQueueHash?.value ?: 0, trackList, artworkMap)
    }

    fun sendDataMap(putDataMapRequest: PutDataMapRequest) {
        dataClient.putDataItem(putDataMapRequest.asPutDataRequest()).addOnSuccessListener {
            Timber.Forest.tag("DataLayerHelper").d("DataMap sent via Data Layer: ${putDataMapRequest.dataMap}")
        }.addOnFailureListener { e ->
            Timber.Forest.tag("DataLayerHelper").e(e, "Failed to send DataMap via Data Layer")
        }
    }

    private fun restartStateObservation() {
        currentWindowIndexJob?.cancel()
        currentWindowIndexJob = scope.launch {
            activePlayerConnection?.let { connection ->
                combine(
                    connection.isPlaying,
                    connection.mediaMetadata,
                    connection.currentQueueHash,
                    connection.currentWindowIndex,
                ) { isPlaying, mediaMetadata, queueHash, currentIndex ->
                    StateSnapshot(isPlaying, mediaMetadata?.id, queueHash, currentIndex)
                }
                .distinctUntilChanged()
                .collect { (_, _, queueHash, _) ->
                    sendStateWithDebounce(queueHash)
                }
            }
        }
    }

    private data class StateSnapshot(
        val isPlaying: Boolean,
        val mediaId: String?,
        val queueHash: Long,
        val currentIndex: Int,
    )

    private suspend fun sendStateWithDebounce(queueHash: Long) {
        val delayMillis = if (queueHash != previousHash) {
            previousHash = queueHash
            Timber.Forest.tag("DataLayerHelper").d("Queue hash changed, waiting 3s")
            3000L
        } else {
            500L
        }
        scheduleStateSend(delayMillis)
    }

    private fun scheduleStateSend(delayMillis: Long) {
        pendingStateSendJob?.cancel()
        pendingStateSendJob = scope.launch {
            delay(delayMillis)
            sendCurrentStateNow()
        }
    }

    fun generateResizedAssetFromByteArray(size: Int, byteArray: ByteArray): Asset? {
        if (byteArray.isEmpty()) return null

        return try {
            val decodedBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(byteArray))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val (originalWidth, originalHeight) = info.size.run { width to height }
                    decoder.setTargetSampleSize(calculateSampleSize(originalWidth, originalHeight, size))
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, size)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
            }
                ?: return null

            // Use matrix transformation for scaling and cropping
            val result = transformBitmap(decodedBitmap, size)

            ByteArrayOutputStream().use { stream ->
                result.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, stream)
                Asset.createFromBytes(stream.toByteArray())
            }
        } catch (e: Exception) {
            Timber.Forest.tag("DataLayerHelper").e(e, "Failed to generate resized asset from byte array")
            null
        }
    }
}
