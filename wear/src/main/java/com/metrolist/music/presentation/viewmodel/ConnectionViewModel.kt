package com.metrolist.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.metrolist.music.presentation.wear.MessageClientService
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val messageClientService: MessageClientService
) : ViewModel() {

    private val _connectedNodes = MutableStateFlow<Int>(0)
    val connectedNodes: StateFlow<Int> = _connectedNodes.asStateFlow()

    val lastHeartbeatAt: StateFlow<Long?> = messageClientService.lastHeartbeatAt

    init {
        // Query connected nodes once on init via service's nodeClient
        try {
            val nodes = Tasks.await(messageClientService.nodeClient.connectedNodes)
            _connectedNodes.value = nodes.size
        } catch (_: Exception) {}
    }

    fun pingPhone() {
        messageClientService.sendHeartbeatPing()
    }
}
