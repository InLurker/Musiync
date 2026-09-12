package com.metrolist.music.wear

import android.content.Context
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.wear.enumerated.DataLayerPathEnum
import com.metrolist.music.wear.enumerated.MessageLayerPathEnum
import com.metrolist.music.wear.enumerated.WearCommandEnum
import com.metrolist.music.wear.model.toDataMap
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageLayerHelper @Inject constructor(context: Context, val dataLayerHelper: DataLayerHelper) :
    MessageClient.OnMessageReceivedListener {

    @Volatile
    var playerConnection: PlayerConnection? = null

    @Volatile
    var musicService: MusicService? = null

    private val messageClient: MessageClient = Wearable.getMessageClient(context)

    init {
        messageClient.addListener(this)
        Timber.Forest.tag("DataLayerHelper").d("Listening for Wear OS commands")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.Forest.tag("MessageLayerHelper").d("Received message with path: ${messageEvent.path}")
        try {
            when (messageEvent.path) {
                MessageLayerPathEnum.REQUEST_QUEUE.path -> {
                    val requestedIndices = String(messageEvent.data, Charsets.UTF_8)
                        .split(",")
                        .mapNotNull { it.toIntOrNull() }
                        .distinct()
                    if (requestedIndices.isEmpty()) return
                    Timber.Forest.tag("MessageLayerHelper").d("Received request for queue indices: $requestedIndices")
                    dataLayerHelper.handleQueueRangeRequest(requestedIndices) { queue ->
                        if (queue == null || queue.trackList.isEmpty()) {
                            Timber.Forest.tag("MessageLayerHelper").d("Queue is empty")
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
                    Timber.Forest.tag("MessageLayerHelper").d("Received playback command: $command")
                    handleMusicCommand(command)
                }
                MessageLayerPathEnum.REQUEST_SEEK.path -> {
                    val commandData = String(messageEvent.data).split(":")
                    val command = WearCommandEnum.valueOf(commandData[0])
                    val index = commandData[1].toInt()

                    Timber.Forest.tag("MessageLayerHelper").d("Received request seek command: $command with index: $index")
                    handleSeekCommand(command, index)
                }
                else -> {
                    Timber.Forest.tag("MessageLayerHelper").d("Unknown message path: ${messageEvent.path}")
                }
            }
        } catch (e: Exception) {
            Timber.Forest.tag("MessageLayerHelper").e(e, "Error handling message")
        }
    }

    private fun handleMusicCommand(command: WearCommandEnum) {
        Timber.Forest.tag("MessageLayerHelper").d("Executing $command command")
        when (command) {
            WearCommandEnum.NEXT -> withPlayback(
                connectionAction = { it.seekToNext() },
                playerAction = { it.seekToNext() },
            )
            WearCommandEnum.PREVIOUS -> withPlayback(
                connectionAction = { it.seekToPrevious() },
                playerAction = { it.seekToPrevious() },
            )
            WearCommandEnum.PLAY_PAUSE -> withPlayback(
                connectionAction = { it.togglePlayPause() },
                playerAction = { it.togglePlayPause() },
            )
            else -> {
                Timber.Forest.tag("MessageLayerHelper").d("Unknown command: $command")
            }
        }
    }

    private fun handleSeekCommand(command: WearCommandEnum, index: Int) {
        Timber.Forest.tag("MessageLayerHelper").d("Executing $command command with index: $index")
        when (command) {
            WearCommandEnum.SEEK_TO -> {
                withPlayback(
                    connectionAction = {
                        it.player.seekToDefaultPosition(index)
                        it.player.playWhenReady = true
                    },
                    playerAction = {
                        it.seekToDefaultPosition(index)
                        it.playWhenReady = true
                    },
                )
            }
            else -> {
                Timber.Forest.tag("MessageLayerHelper").d("Unknown command: $command")
            }
        }
    }

    private fun withPlayback(
        connectionAction: (PlayerConnection) -> Unit,
        playerAction: (androidx.media3.exoplayer.ExoPlayer) -> Unit,
    ) {
        playerConnection?.let {
            runCatching { connectionAction(it) }
                .onFailure { Timber.Forest.tag("MessageLayerHelper").e(it, "Failed to execute Wear command") }
            return
        }

        val service = musicService ?: return
        if (!service.isPlayerReady.value) return

        runCatching { service.player }
            .getOrNull()
            ?.let { player ->
                runCatching { playerAction(player) }
                    .onFailure { Timber.Forest.tag("MessageLayerHelper").e(it, "Failed to execute Wear command") }
            }
    }
}
