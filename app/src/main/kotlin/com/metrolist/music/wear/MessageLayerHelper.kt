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
class MessageLayerHelper @Inject constructor(context: Context, val dataLayerHelper: DataLayerHelper) : OnMessageReceivedListener {

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
                    val (start, end) = String(messageEvent.data).split(",").map { it.toInt() }
                    dataLayerHelper.handleQueueRangeRequest(start, end) { queue ->
                        if (queue == null || queue.trackList.isEmpty()) {
                            Timber.tag("MessageLayerHelper").d("Queue is empty")
                            return@handleQueueRangeRequest
                        }
                        // Create a main DataMap request.
                        val request = PutDataMapRequest.create(DataLayerPathEnum.QUEUE_RESPONSE.path)
                        val dataMap = request.dataMap
                        dataMap.putInt("queueHash", queue.queueHash)

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

                        dataLayerHelper.sendDataMap(request)
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
}
