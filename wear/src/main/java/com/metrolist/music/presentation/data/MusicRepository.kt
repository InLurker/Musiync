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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

@Singleton
class MusicRepository @Inject constructor(
    private val messageClientService: MessageClientService
) {
    val queue = MutableStateFlow<SortedMap<Int, TrackInfo>>(sortedMapOf())
    val artworks = MutableStateFlow<MutableMap<String, Bitmap?>>(mutableMapOf())
    val musicState = MutableStateFlow<MusicState?>(null)
    val accentColor = MutableStateFlow<Color?>(null)
    val isFetching = MutableStateFlow(false)
    var displayedIndices = SnapshotStateList<Int>()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val queueUpdateSignal = MutableSharedFlow<Unit>()
    private val mutex = Mutex()
    private val pendingFetchRanges = mutableSetOf<Int>()

    fun setAccentColor(color: Color) {
        accentColor.value = color
    }

    fun handleIncomingState(state: MusicState) {
        if (shouldReInitializeQueue(state)) {
            resetQueue(state)
        } else {
            // Ensure current index is loaded before updating state
            scope.launch {
                if (!queue.value.containsKey(state.currentIndex)) {
                    requestPaginatedQueue(state.currentIndex, state.currentIndex + 1)
                    waitForQueueUpdate()
                }
                updateState(state)
                handleQueuePagination(state.currentIndex)
            }
        }
    }

    private fun resetQueue(state: MusicState) {
        queue.value.clear()
        artworks.value.clear()
        displayedIndices.clear()
        pendingFetchRanges.clear()
        
        scope.launch {
            val range = calculateInitialPageRange(state.currentIndex, state.queueSize)
            requestPaginatedQueue(range.first, range.second)
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

    fun requestPaginatedQueue(startIndex: Int, endSize: Int): Boolean {
        val currentQueue = queue.value
        // Filter out indices that are already in the queue or pending fetch
        val desiredIndices = (startIndex until endSize)
            .filterNot { index -> 
                currentQueue.containsKey(index) || pendingFetchRanges.contains(index)
            }
        
        if (desiredIndices.isNotEmpty()) {
            isFetching.value = true
            // Mark these indices as pending fetch to avoid duplicate requests
            pendingFetchRanges.addAll(desiredIndices)
            Log.d("MusicRepository", "Requesting queue indices: ${desiredIndices.joinToString()}")
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
        } finally {
            // Clear pending fetches in case of timeout
            pendingFetchRanges.clear()
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

            // Update the queue with new track data
            queue.update {
                it.apply { trackDelta?.let { delta -> putAll(delta) } }
            }

            // Update displayed indices based on fetched data
            if (trackDelta != null) {
                updateDisplayedIndices(trackDelta.keys)
                // Remove these indices from pending fetch
                pendingFetchRanges.removeAll(trackDelta.keys)
            }

            scope.launch {
                queueUpdateSignal.emit(Unit)
                val newArtworks = artworkDelta()
                if (newArtworks != null) {
                    artworks.update { it.apply { putAll(newArtworks) } }
                }
            }
        }.invokeOnCompletion {
            isFetching.value = false
        }
    }

    private fun updateDisplayedIndices(fetchedIndices: Set<Int>) {
        if (fetchedIndices.isEmpty()) return

        if (displayedIndices.isEmpty()) {
            // If we don't have any displayed indices yet, add all fetched indices
            displayedIndices.addAll(fetchedIndices.sorted())
            return
        }
        val newIndices = fetchedIndices.filter { it !in displayedIndices }
        if (newIndices.isEmpty()) return

        // Check if the new indices are contiguous with current displayed ranges
        val currentMin = displayedIndices.firstOrNull() ?: return
        val currentMax = displayedIndices.lastOrNull() ?: return
        
        val minNewIndex = newIndices.firstOrNull() ?: return
        val maxNewIndex = newIndices.lastOrNull() ?: return

        if (minNewIndex > currentMax + 7 || maxNewIndex < currentMin - 7) {
            // Option 2: Focus on new range if it's far from current display
            val currentState = musicState.value
            if (currentState != null && newIndices.contains(currentState.currentIndex)) {
                // If the current playing index is in the new indices, reset display to focus on it
                displayedIndices.clear()
                displayedIndices.addAll(newIndices)
                return
            }
        }
        
        // Otherwise, merge the indices maintaining order
        val allIndices = displayedIndices.toList() + newIndices
        displayedIndices.clear()
        displayedIndices.addAll(allIndices.sorted().distinct())
        
        Log.d("MusicRepository", "Updated displayedIndices: ${displayedIndices.joinToString()}")
    }

    private fun updateState(state: MusicState) {
        musicState.value = state
    }

    private fun handleQueuePagination(currentIndex: Int) {
        if (displayedIndices.isEmpty()) {
            resetDisplayedQueue(currentIndex)
            return
        }
        
        // Check if current index is in displayedIndices
        if (!displayedIndices.contains(currentIndex)) {
            // If we're jumping to a completely new position, reset the displayed queue
            resetDisplayedQueue(currentIndex)
            return
        }
        
        val firstDisplayed = displayedIndices.first()
        val lastDisplayed = displayedIndices.last()
        
        // If current index is near the edges of displayed indices, fetch more
        if (currentIndex in (firstDisplayed..firstDisplayed + 1)) {
            fetchPreviousTracks()
        } else if (currentIndex in (lastDisplayed - 1)..lastDisplayed) {
            fetchNextTracks()
        }
    }

    private fun fetchNextTracks() {
        val start = (displayedIndices.lastOrNull()?.plus(1) ?: return)
        val end = min(musicState.value?.queueSize ?: return, start + 7)
        if (start < end) {
            requestPaginatedQueue(start, end)
        }
    }

    private fun fetchPreviousTracks() {
        val end = (displayedIndices.firstOrNull() ?: return)
        val start = max(0, end - 7)
        if (start < end) {
            requestPaginatedQueue(start, end)
        }
    }

    private fun resetDisplayedQueue(currentIndex: Int) {
        val range = calculateInitialPageRange(currentIndex, musicState.value?.queueSize ?: 0)
        requestPaginatedQueue(range.first, range.second)
    }
}