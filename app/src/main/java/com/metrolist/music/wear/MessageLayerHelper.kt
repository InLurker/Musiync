package com.metrolist.music.wear

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.wear.enumerated.WearCommandEnum
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageLayerHelper @Inject constructor(context: Context) : OnMessageReceivedListener {

    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val commandListener = AtomicReference<(WearCommandEnum) -> Unit>()

    fun startListeningForCommands(listener: (WearCommandEnum) -> Unit) {
        commandListener.set(listener)
        messageClient.addListener(this)
        Timber.tag("DataLayerHelper").d("Listening for Wear OS commands")
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.tag("MessageLayerHelper").d("Received message with path: ${messageEvent.path}")
        if (messageEvent.path == "/music_command") {
            val message = String(messageEvent.data)
            try {
                val command = WearCommandEnum.valueOf(message)
                Timber.tag("DataLayerHelper").d("Received command: ${command.name}")
                handleMusicCommand(command)
            } catch (e: IllegalArgumentException) {
                Timber.tag("MessageLayerHelper").e("Invalid command received: ${message}")
            }
        }
    }

    private fun handleMusicCommand(command: WearCommandEnum) {
        Timber.tag("MessageLayerHelper").d("Executing PLAY_PAUSE command")
        commandListener.get()?.invoke(command)
    }
}

