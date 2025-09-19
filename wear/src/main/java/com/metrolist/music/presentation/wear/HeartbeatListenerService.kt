package com.metrolist.music.presentation.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.metrolist.music.common.enumerated.MessageClientPathEnum
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HeartbeatListenerService : WearableListenerService() {

    @Inject lateinit var messageClientService: MessageClientService

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        if (path == MessageClientPathEnum.HEARTBEAT.path) {
            val payload = messageEvent.data.toString(Charsets.UTF_8)
            Log.d("WearHeartbeat", "Received heartbeat: $payload from ${messageEvent.sourceNodeId}")
            when (payload) {
                "ping" -> {
                    // Reply immediately
                    messageClientService.messageClient.sendMessage(
                        messageEvent.sourceNodeId,
                        MessageClientPathEnum.HEARTBEAT.path,
                        "pong".toByteArray()
                    )
                }
                "pong" -> {
                    messageClientService.lastHeartbeatAt.value = System.currentTimeMillis()
                }
            }
        }
    }
}

