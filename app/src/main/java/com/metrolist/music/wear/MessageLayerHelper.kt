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
    private val commandListener = AtomicReference<(WearCommandEnum) -> Unit>()

    init {
        messageClient.addListener(this)
        Timber.tag("DataLayerHelper").d("Listening for Wear OS commands")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.tag("MessageLayerHelper").d("Received message with path: ${messageEvent.path}")
        try {
            when (messageEvent.path) {
                MessageLayerPathEnum.REQUEST_QUEUE.path -> {
                    Timber.tag("MessageLayerHelper").d("Received request for queue")
                    val requestedIndices = String(messageEvent.data).split(",").map { it.toInt() }
                    dataLayerHelper.handleQueueRangeRequest(requestedIndices) { queue ->
                        if (queue == null || queue.trackList.isEmpty()) {
                            Timber.tag("MessageLayerHelper").d("Queue is empty")
                            return@handleQueueRangeRequest
                        }
                        // Create a main DataMap request.
                        val request = PutDataMapRequest.create(DataLayerPathEnum.QUEUE_RESPONSE.path)
                        val dataMap = request.dataMap
                        dataMap.putLong("queueHash", queue.queueHash)

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
                    val command = WearCommandEnum.valueOf(String(messageEvent.data))
                    Timber.tag("MessageLayerHelper").d("Received playback command: $command")
                    handleMusicCommand(command)
                }
                MessageLayerPathEnum.REQUEST_SEEK.path -> {
                    val commandData = String(messageEvent.data).split(":")
                    val command = WearCommandEnum.valueOf(commandData[0])
                    val index = commandData[1].toInt()

                    Timber.tag("MessageLayerHelper").d("Received request seek command: $command with index: $index")
                    handleSeekCommand(command, index)
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

    private fun handleSeekCommand(command: WearCommandEnum, index: Int) {
        Timber.tag("MessageLayerHelper").d("Executing $command command with index: $index")
        when (command) {
            WearCommandEnum.SEEK_TO -> {
                playerConnection?.player?.seekToDefaultPosition(index)
                playerConnection?.player?.playWhenReady = true
            }
            else -> {
                Timber.tag("MessageLayerHelper").d("Unknown command: $command")
            }
        }
    }
}

