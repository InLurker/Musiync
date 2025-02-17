package com.metrolist.music.presentation.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageClientService @Inject constructor(private val context: Context): MessageClient.OnMessageReceivedListener {

    val messageClient by lazy { Wearable.getMessageClient(context) }
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        messageClient.addListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d("WearOS", "Received message with path: ${messageEvent.path}")
    }

    fun sendPlaybackCommand(command: String) {
        // cannot be run on main app thread
        scope.launch {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, "/music_command", command.toByteArray())
                Log.d("WearOS", "Sent command: $command to node ${node.id}")
            }
        }
    }

    fun destroy() {
        messageClient.removeListener(this)
    }
}