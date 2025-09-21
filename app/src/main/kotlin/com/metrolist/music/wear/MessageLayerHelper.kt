package com.metrolist.music.wear

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.wear.enumerated.DataLayerPathEnum
import com.metrolist.music.wear.enumerated.MessageLayerPathEnum
import com.metrolist.music.wear.enumerated.WearCommandEnum
import com.metrolist.music.wear.model.toDataMap
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageLayerHelper @Inject constructor(
    context: Context,
    val dataLayerHelper: DataLayerHelper,
    private val wearPlaylistRepository: WearPlaylistRepository,
) : OnMessageReceivedListener {

    var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    val lastHeartbeatAt = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    private val commandListener = AtomicReference<(WearCommandEnum) -> Unit>()

    init {
        messageClient.addListener(this)
        Timber.tag("DataLayerHelper").d("Listening for Wear OS commands")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.tag("MessageLayerHelper").d("Received message with path: ${messageEvent.path}")
        try {
            when (messageEvent.path) {
                MessageLayerPathEnum.HEARTBEAT.path -> {
                    val payload = messageEvent.data?.toString(Charsets.UTF_8)
                    if (payload == "ping") {
                        // Respond to watch with pong
                        messageClient.sendMessage(messageEvent.sourceNodeId, MessageLayerPathEnum.HEARTBEAT.path, "pong".toByteArray())
                    } else if (payload == "pong") {
                        lastHeartbeatAt.value = System.currentTimeMillis()
                    }
                }
                MessageLayerPathEnum.REQUEST_QUEUE.path -> {
                    Timber.tag("MessageLayerHelper").d("Received request for queue")
                    val parts = String(messageEvent.data).split(",")
                    if (parts.size < 2) {
                        Timber.tag("MessageLayerHelper").w("Ignoring malformed queue request: ${parts}")
                        return
                    }
                    val start = parts[0].toInt()
                    val end = parts[1].toInt()
                    val requestId = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                    dataLayerHelper.handleQueueRangeRequest(start, end, requestId) { queue ->
                        if (queue == null || queue.trackList.isEmpty()) {
                            Timber.tag("MessageLayerHelper").d("Queue is empty")
                            return@handleQueueRangeRequest
                        }
                        // Create a main DataMap request.
                        val request = PutDataMapRequest.create(DataLayerPathEnum.QUEUE_RESPONSE.path)
                        val dataMap = request.dataMap
                        dataMap.putLong("queueHash", queue.queueHash)
                        dataMap.putInt("startIndex", queue.startIndex)
                        dataMap.putInt("endIndexExclusive", queue.endIndexExclusive)
                        dataMap.putLong("requestId", queue.requestId)

                        // Build a nested DataMap for the track list.
                        val tracksDataMap = DataMap()
                        queue.trackList.forEach { (index, trackInfo) ->
                            tracksDataMap.putDataMap(index.toString(), trackInfo.toDataMap())
                        }
                        dataMap.putDataMap("trackList", tracksDataMap)

                        // Build a nested DataMap for the artwork assets.
                        val artworkDataMap = DataMap()
                        queue.artworkAssets.forEach { (artworkUri, asset) ->
                            artworkDataMap.putAsset(artworkUri, asset)
                        }
                        dataMap.putDataMap("artworkAssets", artworkDataMap)

                        Timber.tag("Wear-Queue").d(
                            "Sending QUEUE_RESPONSE: tracks=%d assets=%d keys(sample)=%s",
                            tracksDataMap.keySet().size,
                            artworkDataMap.keySet().size,
                            artworkDataMap.keySet().take(3).joinToString(limit = 3)
                        )

                        dataLayerHelper.sendDataMap(request)
                    }
                }
                MessageLayerPathEnum.REQUEST_PLAYLIST_LIBRARY.path -> {
                    val payload = messageEvent.data?.toString(Charsets.UTF_8)
                    val requestId = payload?.toLongOrNull() ?: System.currentTimeMillis()
                    Timber.tag("Wear-Playlists").d("Received library request id=$requestId")
                    wearPlaylistRepository.handleLibraryRequest(requestId)
                }
                MessageLayerPathEnum.REQUEST_PLAYLIST_SEARCH.path -> {
                    val payload = messageEvent.data?.toString(Charsets.UTF_8).orEmpty()
                    val (requestId, query) = parseSearchPayload(payload)
                    Timber.tag("Wear-Playlists").d("Received search request id=$requestId query='$query'")
                    wearPlaylistRepository.handleSearchRequest(query, requestId)
                }
                MessageLayerPathEnum.PLAY_PLAYLIST.path -> {
                    runCatching {
                        com.metrolist.music.datastore.PlaylistSummaryProto.parseFrom(messageEvent.data)
                    }.onSuccess { proto ->
                        Timber.tag("Wear-Playlists").d("Play playlist command for id=${proto.id}")
                        wearPlaylistRepository.handlePlayPlaylist(proto, playerConnection)
                    }.onFailure {
                        Timber.tag("Wear-Playlists").e(it, "Failed to parse playlist payload")
                    }
                }
                MessageLayerPathEnum.REQUEST_STATE.path -> {
                    dataLayerHelper.sendCurrentState()
                }
                MessageLayerPathEnum.PLAYBACK_COMMAND.path -> {
                    Timber.tag("MessageLayerHelper").d("Received playback command")
                    val command = WearCommandEnum.valueOf(String(messageEvent.data))
                    handleMusicCommand(command)
                }
                MessageLayerPathEnum.SEEK_TO_INDEX.path -> {
                    val index = String(messageEvent.data).toIntOrNull()
                    if (index == null) {
                        Timber.tag("MessageLayerHelper").w("Ignoring SEEK_TO_INDEX with non-numeric payload")
                        return
                    }
                    Timber.tag("MessageLayerHelper").d("Seeking to queue index $index")
                    playerConnection?.player?.seekToDefaultPosition(index)
                    playerConnection?.player?.playWhenReady = true
                }
                else -> {
                    Timber.tag("MessageLayerHelper").d("Unknown message path: ${messageEvent.path}")
                }
            }
        } catch (e: Exception) {
            Timber.tag("MessageLayerHelper").e(e, "Error handling message")
        }
    }

    private fun handleMusicCommand(command: WearCommandEnum) {
        Timber.tag("MessageLayerHelper").d("Executing $command command")
        when (command) {
            WearCommandEnum.NEXT -> playerConnection?.seekToNext()
            WearCommandEnum.PREVIOUS -> playerConnection?.seekToPrevious()
            WearCommandEnum.PLAY_PAUSE -> playerConnection?.player?.togglePlayPause()
            else -> {
                Timber.tag("MessageLayerHelper").d("Unknown command: $command")
            }
        }
    }

    fun sendHeartbeatPing() {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Timber.tag("MessageLayerHelper").w("No connected nodes. Cannot send heartbeat")
                }
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, MessageLayerPathEnum.HEARTBEAT.path, "ping".toByteArray())
                        .addOnSuccessListener {
                            Timber.tag("MessageLayerHelper").d("Heartbeat sent to ${node.displayName}(${node.id})")
                        }
                        .addOnFailureListener { e ->
                            Timber.tag("MessageLayerHelper").e(e, "Failed to send heartbeat to ${node.displayName}(${node.id})")
                        }
                }
            }
            .addOnFailureListener { e ->
                Timber.tag("MessageLayerHelper").e(e, "Failed to query connected nodes")
            }
    }

    fun fetchConnectedNodes(onResult: (Int) -> Unit) {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            onResult(nodes.size)
        }.addOnFailureListener { _ -> onResult(0) }
    }

    private fun parseSearchPayload(payload: String): Pair<Long, String> {
        if (payload.isEmpty()) return System.currentTimeMillis() to ""
        val parts = payload.split('|', limit = 2)
        val requestId = parts.getOrNull(0)?.toLongOrNull() ?: System.currentTimeMillis()
        val query = parts.getOrNull(1)?.trim().orEmpty()
        return requestId to query
    }
}
