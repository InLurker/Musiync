package com.metrolist.music.presentation.wear

import android.app.Activity
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MessageClientService(private val activity: Activity): MessageClient.OnMessageReceivedListener {

    val messageClient by lazy { Wearable.getMessageClient(activity) }
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
            val nodes = Tasks.await(Wearable.getNodeClient(activity).connectedNodes)
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