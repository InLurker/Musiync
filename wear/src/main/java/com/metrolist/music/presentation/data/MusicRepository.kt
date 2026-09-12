package com.metrolist.music.presentation.data

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import com.metrolist.music.common.models.MusicState
import com.metrolist.music.common.models.TrackInfo
import com.metrolist.music.presentation.wear.MessageClientService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.SortedMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Data structure to represent a queue range request
 */
data class QueueRequest(
    val startIndex: Int,
    val endIndex: Int,
    val priority: RequestPriority = RequestPriority.NORMAL
)

enum class RequestPriority {
    HIGH, // For current track or range containing current track
    NORMAL // For pagination requests
}

@Singleton
class MusicRepository @Inject constructor(
    private val messageClientService: MessageClientService
) {
    val queue = MutableStateFlow<SortedMap<Int, TrackInfo>>(sortedMapOf())
    val artworks = MutableStateFlow<MutableMap<String, Bitmap?>>(mutableMapOf())
    val musicState = MutableStateFlow<MusicState?>(null)
    val accentColor = MutableStateFlow<Color?>(null)
    val displayedIndices = SnapshotStateList<Int>()

    private val queueRequestChannel = Channel<QueueRequest>(Channel.BUFFERED)
    private val queueUpdateSignal = MutableSharedFlow<Unit>()

    private val mutex = Mutex()
    val pendingIndices = MutableStateFlow<MutableSet<Int>>(mutableSetOf())

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        startQueueRequestProcessor()
    }
    
    private fun startQueueRequestProcessor() {
        repositoryScope.launch {
            queueRequestChannel.consumeAsFlow().collect { request ->
                processQueueRequest(request)
            }
        }
    }
    
    private suspend fun processQueueRequest(request: QueueRequest) {
        val indicesToFetch = (request.startIndex until request.endIndex)
            .filterNot { index -> 
                queue.value.containsKey(index) || pendingIndices.value.contains(index)
            }
            
        if (indicesToFetch.isEmpty()) return
        
        mutex.withLock {
            pendingIndices.value.addAll(indicesToFetch)
        }

        Log.d("MusicRepository", "Requesting queue indices: ${indicesToFetch.joinToString()}")
        messageClientService.sendQueueEntriesRequest(indicesToFetch)
    }

    fun setAccentColor(color: Color) {
        accentColor.value = color
    }

    fun handleIncomingState(state: MusicState) {
        if (shouldReInitializeQueue(state)) {
            resetQueue(state)
        } else {
            repositoryScope.launch {
                handleQueuePagination(state.currentIndex)
                waitForQueueUpdate()
                updateState(state)
            }
        }
    }

    private fun resetQueue(state: MusicState) {
        queue.value.clear()
        artworks.value.clear()
        displayedIndices.clear()
        
        repositoryScope.launch {
            mutex.withLock {
                pendingIndices.value.clear()
            }
            val range = calculateInitialPageRange(state.currentIndex, state.queueSize)
            requestQueueRange(range.first, range.second, RequestPriority.HIGH)
            waitForQueueUpdate()
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

    /**
     * Request a range of queue items
     * @param startIndex First index to fetch (inclusive)
     * @param endIndex Last index to fetch (exclusive)
     * @param priority Priority of the request
     */
    fun requestQueueRange(startIndex: Int, endIndex: Int, priority: RequestPriority = RequestPriority.NORMAL): Boolean {
        if (startIndex >= endIndex) return true

        val range = (startIndex until endIndex).toSet()

        return when {
            queue.value.keys.containsAll(range) -> {
                updateDisplayedIndices(range)
                true
            }
            pendingIndices.value.containsAll(range) -> true
            else -> {
                repositoryScope.launch { queueRequestChannel.send(QueueRequest(startIndex, endIndex, priority)) }
                false
            }
        }
    }

    private suspend fun waitForQueueUpdate() {
        try {
            withTimeout(5000) {
                queueUpdateSignal.first()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("MusicRepository", "Queue update timed out")
        } finally {
            // Clear pending indices in case of timeout
            mutex.withLock {
                pendingIndices.value.clear()
            }
        }
    }

    private fun shouldReInitializeQueue(state: MusicState): Boolean {
        return musicState.value?.let { current ->
            current.queueHash != state.queueHash || current.queueSize != state.queueSize
        } ?: true
    }

    suspend fun updateQueue(
        hash: Long,
        trackDelta: Map<Int, TrackInfo>?,
        artworkDelta: suspend () -> Map<String, Bitmap?>?
    ) {
        mutex.withLock {
            if (hash != musicState.value?.queueHash) {
                Log.d("MusicRepository", "Received new queue with hash: $hash")
                queue.value.clear()
                artworks.value.clear()
                displayedIndices.clear()
            }

            queue.update {
                it.apply { trackDelta?.let { delta -> putAll(delta) } }
            }

            // Update displayed indices based on fetched data
            if (trackDelta != null) {
                updateDisplayedIndices(trackDelta.keys)
                // Remove these indices from pending
                pendingIndices.value.removeAll(trackDelta.keys)
            }

            // Fetch artwork
            val newArtworks = artworkDelta()
            if (newArtworks != null) {
                artworks.update { it.apply { putAll(newArtworks) } }
            }

            repositoryScope.launch {
                queueUpdateSignal.emit(Unit)
            }
        }
    }

    private fun updateDisplayedIndices(fetchedIndices: Set<Int>) {
        if (displayedIndices.isEmpty()) {
            displayedIndices.addAll(fetchedIndices.sorted())
            return
        }
        // Find where to insert the new indices
        val newIndices = fetchedIndices.filter { it !in displayedIndices }.sorted()

        if (newIndices.first() < displayedIndices.first()) {
            displayedIndices.addAll(0, newIndices)
        } else if (newIndices.last() > displayedIndices.last()) {
            displayedIndices.addAll(newIndices)
        } else {
            displayedIndices.apply {
                clear()
                addAll(fetchedIndices.sorted())
            }
        }
        Log.d("MusicRepository", "Updated displayedIndices: ${displayedIndices.joinToString()}")
    }

    private fun updateState(state: MusicState) {
        musicState.value = state
    }

    private fun handleQueuePagination(currentIndex: Int) {
        if (displayedIndices.isEmpty()) {
            reInitializeDisplayedQueue(currentIndex)
        } else {
            val firstDisplayed = displayedIndices.first()
            val lastDisplayed = displayedIndices.last()
            
            // If current index is near the edges of displayed indices, fetch more
            if (currentIndex in (firstDisplayed.rangeTo(firstDisplayed + 1))) {
                fetchPreviousTracks(currentIndex)
            } else if (currentIndex in (lastDisplayed - 1).rangeTo(lastDisplayed)) {
                fetchNextTracks(currentIndex)
            } else if (currentIndex < firstDisplayed || currentIndex > lastDisplayed) {
                displayedIndices.clear()
                reInitializeDisplayedQueue(currentIndex)
            }
        }
    }

    private fun fetchNextTracks(currentIndex: Int) {
        val start = (displayedIndices.lastOrNull()?.plus(1) ?: return)
        val end = min(musicState.value!!.queueSize, currentIndex + 8)
        if (start < end) {
            requestQueueRange(start, end)
        }
    }

    private fun fetchPreviousTracks(currentIndex: Int) {
        val end = (displayedIndices.firstOrNull() ?: return)
        val start = max(0, currentIndex - 7)
        if (start < end) {
            requestQueueRange(start, end)
        }
    }

    private fun reInitializeDisplayedQueue(currentIndex: Int) {
        val range = calculateInitialPageRange(currentIndex, musicState.value?.queueSize ?: 0)
        requestQueueRange(range.first, range.second, RequestPriority.HIGH)
    }
}