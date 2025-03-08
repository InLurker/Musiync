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

    init {
        messageClient.addListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("Metrolist Mobile", "Received message with path: ${messageEvent.path}")
    }

    fun sendPlaybackCommand(command: WearCommandEnum) {
        sendMessage(MessageClientPathEnum.PLAYBACK_COMMAND.path, command.name.toByteArray())
    }

    fun sendRequestSeekCommand(command: WearCommandEnum, index: Int) {
        sendMessage(MessageClientPathEnum.REQUEST_SEEK.path, "${command.name}:${index}".toByteArray())
    }

    fun sendQueueEntriesRequest(indices: List<Int>) {
        sendMessage(MessageClientPathEnum.REQUEST_QUEUE.path, indices.joinToString(",").toByteArray())
    }

    fun sendMessage(path: String, payload: ByteArray?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = withContext(Dispatchers.IO) {
                    Tasks.await(nodeClient.connectedNodes)
                }
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, path, payload)
                    Log.d("MessageSender", "Sent message to node ${node.id} with path: $path with payload: ${payload?.toString(Charsets.UTF_8)}")
                }
            } catch (e: Exception) {
                Log.e("MessageSender", "Failed to send message", e)
            }
        }
    }
}