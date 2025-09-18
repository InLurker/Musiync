package com.metrolist.music.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.metrolist.music.wear.enumerated.MessageLayerPathEnum
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WearMessageListenerService : WearableListenerService() {

    @Inject lateinit var messageLayerHelper: MessageLayerHelper

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        if (path == MessageLayerPathEnum.HEARTBEAT.path) {
            val payload = messageEvent.data?.toString(Charsets.UTF_8)
            Log.d("PhoneHeartbeat", "Received heartbeat: $payload from ${messageEvent.sourceNodeId}")
            when (payload) {
                "ping" -> {
                    // Reply immediately to the watch
                    com.google.android.gms.wearable.Wearable.getMessageClient(this)
                        .sendMessage(
                            messageEvent.sourceNodeId,
                            MessageLayerPathEnum.HEARTBEAT.path,
                            "pong".toByteArray()
                        )
                }
                "pong" -> {
                    messageLayerHelper.lastHeartbeatAt.value = System.currentTimeMillis()
                }
            }
        }
    }
}
