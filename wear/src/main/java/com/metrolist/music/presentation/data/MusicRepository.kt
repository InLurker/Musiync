package com.metrolist.music.presentation.data

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.metrolist.music.common.models.MusicState
import com.metrolist.music.common.models.TrackInfo
import com.metrolist.music.presentation.wear.MessageClientService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class MusicRepository @Inject constructor(
    private val messageClientService: MessageClientService
) {
    val queue = MutableStateFlow<MutableMap<Int, TrackInfo>>(mutableMapOf())

    val artworks = MutableStateFlow<MutableMap<String, Flow<Bitmap?>>>(mutableMapOf())

    val musicState = MutableStateFlow<MusicState?>(null)

    val accentColor = MutableStateFlow<Color?>(null)

    private var queueRequested: Boolean = false
    private val queueAvailable = MutableSharedFlow<Unit>(replay = 1)

    private val scope = CoroutineScope(Dispatchers.IO)

    private val queueUpdateSignal = MutableSharedFlow<Unit>()


    fun setAccentColor(color: Color) {
        accentColor.value = color
    }

    fun handleIncomingState(state: MusicState) {
        if (shouldRequestQueue(state)) {
            scope.launch {
                requestPaginatedQueue(state.currentIndex, state.queueSize)
                waitForQueueUpdate()
                updateState(state)
            }
        } else {
            updateState(state)
        }
    }

    private fun calculatePageRange(currentIndex: Int, queueSize: Int): Pair<Int, Int> {
        if (queueSize <= 0) {
            // Handle empty queue case
            return Pair(0, 0)
        }

        return when {
            currentIndex < 2 -> Pair(0, min(5, queueSize)) // First 5 items (or fewer if queue is small)
            currentIndex > queueSize - 3 -> Pair(max(0, queueSize - 5), queueSize) // Last 5 items
            else -> Pair(max(0, currentIndex - 2), min(currentIndex + 3, queueSize)) // Centered window
        }
    }

    private fun requestPaginatedQueue(currentIndex: Int, queueSize: Int) {
        val (start, end) = calculatePageRange(currentIndex, queueSize)
        messageClientService.sendQueueRangeRequest(start, end)
    }

    private suspend fun waitForQueueUpdate() {
        try {
            withTimeout(5000) {
                queueUpdateSignal.first()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("MusicRepository", "Queue update timed out")
        }
    }

    private fun shouldRequestQueue(state: MusicState): Boolean {
        return musicState.value?.let { current ->
            current.queueHash != state.queueHash || current.queueSize != state.queueSize
        } ?: true
    }

    fun updateQueue(hash: Int, trackDelta: Map<Int, TrackInfo>?, artworkDelta: Map<String, Flow<Bitmap?>>?) {
        if (hash != musicState.value?.queueHash) {
            Log.d("MusicRepository", "Received new queue with hash: $hash")
            queue.value.clear()
            artworks.value.clear()
        }
        queue.value = queue.value.toMutableMap().apply {
            trackDelta?.let { putAll(it) }
        }
        artworks.value = artworks.value.toMutableMap().apply {
            artworkDelta?.let { putAll(it) }
        }

        scope.launch {
            queueUpdateSignal.emit(Unit)
        }
    }

    private fun updateState(state: MusicState) {
        musicState.value = state
    }

}