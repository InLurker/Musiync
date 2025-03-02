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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class MusicRepository @Inject constructor(
    private val messageClientService: MessageClientService
) {
    val queue = MutableStateFlow<MutableMap<Int, TrackInfo>>(sortedMapOf())
    val artworks = MutableStateFlow<MutableMap<String, Bitmap?>>(mutableMapOf())
    val musicState = MutableStateFlow<MusicState?>(null)
    val accentColor = MutableStateFlow<Color?>(null)

    private val scope = CoroutineScope(Dispatchers.IO)
    private val queueUpdateSignal = MutableSharedFlow<Unit>()
    private val mutex = Mutex()

    fun setAccentColor(color: Color) {
        accentColor.value = color
    }

    fun handleIncomingState(state: MusicState) {
        if (shouldReInitializeQueue(state)) {
            queue.value.clear()
            artworks.value.clear()
            scope.launch {
                val range = calculateInitialPageRange(state.currentIndex, state.queueSize)
                if (requestPaginatedQueue(range.first, range.second)) {
                    waitForQueueUpdate()
                }
                updateState(state)
            }
        } else {
            updateState(state)
        }
    }

    fun calculateInitialPageRange(currentIndex: Int, totalQueueSize: Int): Pair<Int, Int> {
        if (totalQueueSize <= 0) return Pair(0, 0)

        return when {
            currentIndex < 3 -> Pair(0, min(7, totalQueueSize)) // First 7 items
            currentIndex > totalQueueSize - 4 -> Pair(max(0, totalQueueSize - 7), totalQueueSize) // Last 7 items
            else -> Pair(max(0, currentIndex - 3), min(currentIndex + 4, totalQueueSize)) // Centered window
        }
    }

    fun requestPaginatedQueue(startIndex: Int, endSize: Int): Boolean {
        val currentQueue = queue.value
        val desiredIndices = (startIndex until endSize).filterNot { index -> currentQueue.containsKey(index) }
        if (desiredIndices.isNotEmpty()) {
            messageClientService.sendQueueEntriesRequest(desiredIndices)
            return false
        }
        return true
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

    private fun shouldReInitializeQueue(state: MusicState): Boolean {
        return musicState.value?.let { current ->
            current.queueHash != state.queueHash || current.queueSize != state.queueSize
        } ?: true
    }

    suspend fun updateQueue(
        hash: String,
        trackDelta: Map<Int, TrackInfo>?,
        artworkDelta: suspend () -> Map<String, Bitmap?>?
    ) {
        mutex.withLock {
            if (hash != musicState.value?.queueHash) {
                Log.d("MusicRepository", "Received new queue with hash: $hash")
                queue.value.clear()
                artworks.value.clear()
            }

            queue.update { it.toMutableMap().apply { trackDelta?.let { putAll(it) } } }

            scope.launch {
                queueUpdateSignal.emit(Unit)
                val newArtworks = artworkDelta()
                if (newArtworks != null) {
                    artworks.update { it.toMutableMap().apply { putAll(newArtworks) } }
                }
            }
        }
    }

    private fun updateState(state: MusicState) {
        musicState.value = state
    }
}