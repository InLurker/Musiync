package com.metrolist.music.presentation.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.common.enumerated.MessageClientPathEnum
import com.metrolist.music.common.enumerated.WearCommandEnum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageClientService @Inject constructor(context: Context): MessageClient.OnMessageReceivedListener {

    val messageClient by lazy { Wearable.getMessageClient(context) }
    val nodeClient by lazy { Wearable.getNodeClient(context) }
    val lastHeartbeatAt = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    init {
        messageClient.addListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("Metrolist Mobile", "Received message with path: ${messageEvent.path}")
        when (MessageClientPathEnum.fromPath(messageEvent.path)) {
            MessageClientPathEnum.HEARTBEAT -> {
                val payload = messageEvent.data.toString(Charsets.UTF_8)
                if (payload == "ping") {
                    // Reply to phone with pong
                    messageClient.sendMessage(messageEvent.sourceNodeId, MessageClientPathEnum.HEARTBEAT.path, "pong".toByteArray())
                } else if (payload == "pong") {
                    // Phone replied to a ping we initiated
                    lastHeartbeatAt.value = System.currentTimeMillis()
                }
            }
            else -> Unit
        }
    }

    fun sendPlaybackCommand(command: WearCommandEnum) {
        sendMessage(MessageClientPathEnum.PLAYBACK_COMMAND.path, command.name.toByteArray())
    }

    fun sendQueueRangeRequest(start: Int, end: Int) {
        sendMessage(MessageClientPathEnum.REQUEST_QUEUE.path, "$start,$end".toByteArray())
    }

    fun sendCurrentStateRequest() {
        sendMessage(MessageClientPathEnum.REQUEST_STATE.path, null)
    }

    fun sendHeartbeatPing() {
        sendMessage(MessageClientPathEnum.HEARTBEAT.path, "ping".toByteArray())
    }

    fun sendMessage(path: String, payload: ByteArray?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = withContext(Dispatchers.IO) {
                    Tasks.await(nodeClient.connectedNodes)
                }
                if (nodes.isEmpty()) {
                    Log.w("MessageSender", "No connected nodes. Cannot send $path")
                } else {
                    nodes.forEach { node ->
                        messageClient.sendMessage(node.id, path, payload)
                            .addOnSuccessListener {
                                Log.d("MessageSender", "Sent message to node ${node.displayName}(${node.id}) path=$path payload=${payload?.toString(Charsets.UTF_8)}")
                            }
                            .addOnFailureListener { e ->
                                Log.e("MessageSender", "Failed to send to ${node.displayName}(${node.id}) path=$path", e)
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e("MessageSender", "Failed to send message", e)
            }
        }
    }
}
