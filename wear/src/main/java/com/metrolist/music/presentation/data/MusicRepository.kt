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
import java.util.ArrayDeque
import java.util.SortedMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

private const val DEFAULT_WINDOW_SIZE = 7
private const val QUEUE_REQUEST_TIMEOUT_MS = 5_000L

private data class QueueRequest(
    val startIndex: Int,
    val endIndex: Int,
    val priority: RequestPriority = RequestPriority.NORMAL
)

enum class RequestPriority {
    HIGH,
    NORMAL
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

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueRequestChannel = Channel<QueueRequest>(Channel.BUFFERED)
    private val queueUpdateSignal = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val pendingIndices = MutableStateFlow<MutableSet<Int>>(mutableSetOf())
    private val mutex = Mutex()
    private val nextQueueRequestId = AtomicLong(0)
    private val activeQueueRequestId = AtomicLong(0)

    init {
        startQueueRequestProcessor()
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

    fun ensureQueueForIndex(index: Int, priority: RequestPriority = RequestPriority.NORMAL) {
        val currentState = musicState.value ?: return
        if (index < 0 || index >= currentState.queueSize) return

        val alreadyLoaded = queue.value.containsKey(index)
        val isPending = pendingIndices.value.contains(index)
        if (alreadyLoaded || isPending) return

        val windowRadius = DEFAULT_WINDOW_SIZE / 2
        val start = max(0, index - windowRadius)
        val end = min(currentState.queueSize, index + windowRadius + 1)
        requestQueueRange(start, end, priority)
    }

    fun requestQueueRange(
        startIndex: Int,
        endIndex: Int,
        priority: RequestPriority = RequestPriority.NORMAL
    ): Boolean {
        if (startIndex >= endIndex) return true

        val range = (startIndex until endIndex).toSet()
        val loaded = queue.value.keys
        val pending = pendingIndices.value

        return when {
            loaded.containsAll(range) -> {
                updateDisplayedIndices(range)
                true
            }
            pending.containsAll(range) -> true
            else -> {
                repositoryScope.launch {
                    queueRequestChannel.send(QueueRequest(startIndex, endIndex, priority))
                }
                false
            }
        }
    }

    private fun startQueueRequestProcessor() {
        repositoryScope.launch {
            val buffer = ArrayDeque<QueueRequest>()
            queueRequestChannel.consumeAsFlow().collect { request ->
                if (request.priority == RequestPriority.HIGH) {
                    buffer.addFirst(request)
                } else {
                    buffer.addLast(request)
                }

                while (buffer.isNotEmpty()) {
                    val next = buffer.removeFirst()
                    processQueueRequest(next)
                }
            }
        }
    }

    private suspend fun processQueueRequest(request: QueueRequest) {
        val queueSize = musicState.value?.queueSize ?: return
        if (queueSize <= 0) return

        val start = request.startIndex.coerceAtLeast(0)
        val end = request.endIndex.coerceAtMost(queueSize)
        if (start >= end) return

        val missingIndices = (start until end).filter { index ->
            !queue.value.containsKey(index) && !pendingIndices.value.contains(index)
        }

        if (missingIndices.isEmpty()) return

        val actualStart = missingIndices.first()
        val actualEndExclusive = missingIndices.last() + 1
        val requestedRange = actualStart until actualEndExclusive

        mutex.withLock {
            val updated = pendingIndices.value.toMutableSet()
            requestedRange.forEach(updated::add)
            pendingIndices.value = updated
        }

        val requestId = requestPaginatedQueue(actualStart, actualEndExclusive)
        if (requestId != null) {
            waitForQueueUpdate(requestId, requestedRange)
        } else {
            clearPendingRange(requestedRange)
        }
    }

    private fun requestPaginatedQueue(startIndex: Int, endIndexExclusive: Int): Long? {
        if (startIndex >= endIndexExclusive) return null

        val requestId = nextQueueRequestId.incrementAndGet()
        activeQueueRequestId.set(requestId)
        messageClientService.sendQueueRangeRequest(startIndex, endIndexExclusive, requestId)
        return requestId
    }

    private suspend fun waitForQueueUpdate(
        requestId: Long? = null,
        requestedRange: IntRange? = null
    ) {
        try {
            withTimeout(QUEUE_REQUEST_TIMEOUT_MS) {
                if (requestId == null) {
                    queueUpdateSignal.first()
                } else {
                    queueUpdateSignal.first { it == requestId }
                }
            }
        } catch (e: TimeoutCancellationException) {
            if (requestId != null) {
                Log.e("MusicRepository", "Queue update timed out for requestId=$requestId", e)
            } else {
                Log.e("MusicRepository", "Queue update timed out", e)
            }
            requestedRange?.let { clearPendingRange(it) }
            if (requestedRange == null) {
                mutex.withLock {
                    pendingIndices.value = mutableSetOf()
                }
            }
        }
    }

    private suspend fun clearPendingRange(range: IntRange) {
        mutex.withLock {
            val updated = pendingIndices.value.toMutableSet()
            range.forEach(updated::remove)
            pendingIndices.value = updated
        }
    }

    private fun shouldReInitializeQueue(state: MusicState): Boolean {
        val current = musicState.value ?: return true
        return current.queueHash != state.queueHash || current.queueSize != state.queueSize
    }

    private fun resetQueue(state: MusicState) {
        queue.value = sortedMapOf()
        artworks.value = mutableMapOf()
        displayedIndices.clear()
        pendingIndices.value = mutableSetOf()

        repositoryScope.launch {
            val range = calculateInitialPageRange(state.currentIndex, state.queueSize)
            requestQueueRange(range.first, range.second, RequestPriority.HIGH)
            waitForQueueUpdate()
            updateState(state)
        }
    }

    fun calculateInitialPageRange(currentIndex: Int, totalQueueSize: Int): Pair<Int, Int> {
        if (totalQueueSize <= 0) return 0 to 0

        return when {
            currentIndex < 3 -> 0 to min(totalQueueSize, 7)
            currentIndex > totalQueueSize - 4 -> max(0, totalQueueSize - 7) to totalQueueSize
            else -> max(0, currentIndex - 3) to min(totalQueueSize, currentIndex + 4)
        }
    }

    suspend fun updateQueue(
        hash: Long,
        trackDelta: Map<Int, TrackInfo>?,
        startIndex: Int,
        endIndexExclusive: Int,
        requestId: Long,
        artworkDelta: (suspend () -> Map<String, Bitmap?>?)? = null
    ) {
        val rangeDescription = if (startIndex < endIndexExclusive) "$startIndex..${endIndexExclusive - 1}" else "empty"
        Log.d("MusicRepository", "Queue update id=$requestId range=$rangeDescription hash=$hash")

        if (requestId != 0L && requestId < activeQueueRequestId.get()) {
            Log.d("MusicRepository", "Ignoring stale queue response id=$requestId")
            queueUpdateSignal.tryEmit(requestId)
            return
        }

        val newArtworks = artworkDelta?.invoke()

        mutex.withLock {
            val currentHash = musicState.value?.queueHash
            if (currentHash != null && hash != currentHash) {
                Log.d("MusicRepository", "Queue hash changed $currentHash -> $hash")
                queue.value = sortedMapOf()
                artworks.value = mutableMapOf()
                displayedIndices.clear()
            }

            if (!trackDelta.isNullOrEmpty()) {
                queue.update { current ->
                    current.apply { putAll(trackDelta) }
                }

                updateDisplayedIndices(trackDelta.keys)
                val updated = pendingIndices.value.toMutableSet().apply {
                    removeAll(trackDelta.keys)
                }
                pendingIndices.value = updated
            }

            if (!newArtworks.isNullOrEmpty()) {
                artworks.update { current ->
                    current.apply { putAll(newArtworks) }
                }
            }
        }

        repositoryScope.launch {
            queueUpdateSignal.emit(requestId)
        }
    }

    private fun updateDisplayedIndices(fetchedIndices: Set<Int>) {
        if (fetchedIndices.isEmpty()) return

        val sorted = fetchedIndices.sorted()

        if (displayedIndices.isEmpty()) {
            displayedIndices.addAll(sorted)
            return
        }

        val firstFetched = sorted.first()
        val lastFetched = sorted.last()

        when {
            firstFetched < displayedIndices.first() -> {
                displayedIndices.addAll(0, sorted)
            }
            lastFetched > displayedIndices.last() -> {
                displayedIndices.addAll(sorted)
            }
            else -> {
                val merged = (displayedIndices.toList() + sorted).distinct().sorted()
                displayedIndices.clear()
                displayedIndices.addAll(merged)
            }
        }
    }

    private fun handleQueuePagination(currentIndex: Int) {
        if (displayedIndices.isEmpty()) {
            reInitializeDisplayedQueue(currentIndex)
            return
        }

        val firstDisplayed = displayedIndices.first()
        val lastDisplayed = displayedIndices.last()

        when {
            currentIndex in firstDisplayed..(firstDisplayed + 1) -> fetchPreviousTracks(currentIndex)
            currentIndex in (lastDisplayed - 1)..lastDisplayed -> fetchNextTracks(currentIndex)
            currentIndex < firstDisplayed || currentIndex > lastDisplayed -> {
                displayedIndices.clear()
                reInitializeDisplayedQueue(currentIndex)
            }
        }
    }

    private fun fetchNextTracks(currentIndex: Int) {
        val queueSize = musicState.value?.queueSize ?: return
        val start = (displayedIndices.lastOrNull()?.plus(1)) ?: return
        val end = min(queueSize, max(currentIndex + 4, start + DEFAULT_WINDOW_SIZE))
        if (start < end) {
            requestQueueRange(start, end)
        }
    }

    private fun fetchPreviousTracks(currentIndex: Int) {
        val startCandidate = (displayedIndices.firstOrNull() ?: return) - DEFAULT_WINDOW_SIZE
        val start = max(0, min(currentIndex - DEFAULT_WINDOW_SIZE, startCandidate))
        val end = displayedIndices.firstOrNull() ?: return
        if (start < end) {
            requestQueueRange(start, end)
        }
    }

    private fun reInitializeDisplayedQueue(currentIndex: Int) {
        val range = calculateInitialPageRange(currentIndex, musicState.value?.queueSize ?: 0)
        requestQueueRange(range.first, range.second, RequestPriority.HIGH)
    }

    private fun updateState(state: MusicState) {
        musicState.value = state
    }
}
