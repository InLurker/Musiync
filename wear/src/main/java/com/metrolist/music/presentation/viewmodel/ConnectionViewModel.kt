package com.metrolist.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Tasks
import com.metrolist.music.presentation.wear.MessageClientService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val messageClientService: MessageClientService
) : ViewModel() {

    private val _connectedNodes = MutableStateFlow(0)
    val connectedNodes: StateFlow<Int> = _connectedNodes.asStateFlow()

    val lastHeartbeatAt: StateFlow<Long?> = messageClientService.lastHeartbeatAt

    init {
        refreshConnected()
        // Refresh connected nodes whenever we receive a heartbeat
        viewModelScope.launch {
            lastHeartbeatAt.collect { _ ->
                refreshConnected()
            }
        }
    }

    fun pingPhone() {
        messageClientService.sendHeartbeatPing()
    }

    fun refreshConnected() {
        viewModelScope.launch {
            try {
                val nodes = Tasks.await(messageClientService.nodeClient.connectedNodes)
                _connectedNodes.value = nodes.size
            } catch (_: Exception) {
                _connectedNodes.value = 0
            }
        }
    }
}
