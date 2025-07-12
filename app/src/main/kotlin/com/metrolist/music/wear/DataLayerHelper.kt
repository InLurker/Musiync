package com.metrolist.music.wear

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Timeline
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.wear.enumerated.DataLayerPathEnum
import com.metrolist.music.wear.helper.calculateSampleSize
import com.metrolist.music.wear.helper.transformBitmap
import com.metrolist.music.wear.model.MusicQueue
import com.metrolist.music.wear.model.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataLayerHelper @Inject constructor(context: Context) {

    private var _playerConnection by mutableStateOf<PlayerConnection?>(null)
    var playerConnection: PlayerConnection?
        get() = _playerConnection
        set(value) {
            _playerConnection = value
            if (value != null) {
                startObservingCurrentWindowIndex()
            } else {
                stopObservingCurrentWindowIndex()
            }
        }

    private var currentWindowIndexJob: Job? = null

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var previousHash: Long? = null
    private val sendStateMutex = Mutex()
    private var pendingSendRequest = false
    @Inject
    lateinit var database: MusicDatabase

    lateinit var musicService: MusicService

    fun initializeMusicService(musicService: MusicService) {
        this.musicService = musicService
    }

    fun sendCurrentState() {
        scope.launch {
            sendStateMutex.withLock {
                if (pendingSendRequest) return@launch
                pendingSendRequest = true
                delay(500) // Debounce to avoid redundant calls
                pendingSendRequest = false

                try {
                    val putDataRequest = PutDataMapRequest.create(DataLayerPathEnum.CURRENT_STATE.path).apply {
                        val queue = playerConnection?.queueWindows?.value
                        dataMap.putLong("queueHash", playerConnection?.currentQueueHash?.value ?: 0)
                        dataMap.putInt("queueSize", queue?.size ?: 0)
                        dataMap.putInt("currentIndex", playerConnection?.currentWindowIndex?.value ?: 0)
                        dataMap.putBoolean("isPlaying", playerConnection?.isPlaying?.value ?: false)
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
        }
    }

    fun handleQueueRangeRequest(requestedIndices: List<Int>, callback: (MusicQueue?) -> Unit) {
        callback(getPaginatedQueue(requestedIndices))
    }

    private fun getPaginatedQueue(requestedIndices: List<Int>): MusicQueue? {
        playerConnection?.let { connection ->
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

        for (index in requestedIndices) {
            val window = queue[index]
            val mediaMetadata = window.mediaItem.mediaMetadata

            val title = mediaMetadata.title.toString()
            val artist = mediaMetadata.artist.toString()
            val albumTitle = mediaMetadata.albumTitle.toString()
            val artworkUri = mediaMetadata.artworkUri.toString()

            trackList[index] = TrackInfo(title, artist, albumTitle, artworkUri)

            if (!artworkMap.containsKey(artworkUri)) {
                mediaMetadata.artworkData?.let { data ->
                    generateResizedAssetFromByteArray(400, data)?.let { asset ->
                        artworkMap[artworkUri] = asset
                    }
                }
            }
        }
        return MusicQueue(playerConnection?.currentQueueHash?.value ?: 0, trackList, artworkMap)
    }

    fun sendDataMap(putDataMapRequest: PutDataMapRequest) {
        dataClient.putDataItem(putDataMapRequest.asPutDataRequest()).addOnSuccessListener {
            Timber.Forest.tag("DataLayerHelper").d("DataMap sent via Data Layer: ${putDataMapRequest.dataMap}")
        }.addOnFailureListener { e ->
            Timber.Forest.tag("DataLayerHelper").e(e, "Failed to send DataMap via Data Layer")
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun startObservingCurrentWindowIndex() {
        currentWindowIndexJob?.cancel()
        currentWindowIndexJob = scope.launch {
            playerConnection?.let { connection ->
                combine(
                    connection.isPlaying,
                    connection.mediaMetadata,
                    connection.currentQueueHash,
                ) { isPlaying, mediaMetadata, queueHash ->
                    Triple(isPlaying, mediaMetadata, queueHash)
                }
                .distinctUntilChanged()
                .collect { (_, _, queueHash) ->
                    sendStateWithDebounce(queueHash)
                }
            }
        }
    }

    private suspend fun sendStateWithDebounce(queueHash: Long) {
        sendStateMutex.withLock {  // Lock at the beginning
            if (queueHash != previousHash) {
                previousHash = queueHash
                Timber.Forest.tag("DataLayerHelper").d("Queue hash changed, waiting 3s")
                delay(3000)  // Now delay works correctly in suspend function
            }
            sendCurrentState() // Lock is only released here
        }
    }

    private fun stopObservingCurrentWindowIndex() {
        currentWindowIndexJob?.cancel()
        currentWindowIndexJob = null
    }

    @SuppressLint("NewApi")
    fun generateResizedAssetFromByteArray(size: Int, byteArray: ByteArray): Asset? {
        if (byteArray.isEmpty()) return null

        return try {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(byteArray))
            val decodedBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE

                // Calculate sample size based on original dimensions
                val (originalWidth, originalHeight) = info.size.run { width to height }
                decoder.setTargetSampleSize(
                    calculateSampleSize(
                        originalWidth,
                        originalHeight,
                        size
                    )
                )
            }

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