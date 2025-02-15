package com.metrolist.music.wear

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

class MessageLayerHelper(private val context: Context) : OnMessageReceivedListener {

    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val commandListener = AtomicReference<(String) -> Unit>()

    fun startListeningForCommands(listener: (String) -> Unit) {
        commandListener.set(listener)
        messageClient.addListener(this)
        Timber.tag("DataLayerHelper").d("Listening for Wear OS commands")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.tag("MessageLayerHelper").d("Received message with path: ${messageEvent.path}")
        if (messageEvent.path == "/music_command") {
            val command = String(messageEvent.data)
            Timber.tag("DataLayerHelper").d("Received command: $command")
            handleMusicCommand(command)
        }
    }

    private fun handleMusicCommand(command: String) {
        when (command) {
            "PLAY/PAUSE" -> {
                Timber.tag("MessageLayerHelper").d("Executing PLAY/PAUSE command")
                commandListener.get()?.invoke("PLAY/PAUSE")
            }
            "NEXT" -> {
                Timber.tag("MessageLayerHelper").d("Executing NEXT command")
                commandListener.get()?.invoke("NEXT")
            }
            "PREVIOUS" -> {
                Timber.tag("MessageLayerHelper").d("Executing PREVIOUS command")
                commandListener.get()?.invoke("PREVIOUS")
            }
            else -> {
                Timber.tag("MessageLayerHelper").w("Received unknown command: $command")
            }
        }
    }
}