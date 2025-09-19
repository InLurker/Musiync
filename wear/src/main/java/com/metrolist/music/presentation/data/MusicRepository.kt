package com.metrolist.music.presentation.data

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.metrolist.music.common.models.MusicState
import com.metrolist.music.common.models.TrackInfo
import com.metrolist.music.presentation.wear.MessageClientService
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
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

    val queue = MutableStateFlow<List<TrackInfo?>>(emptyList())
    val musicState = MutableStateFlow<MusicState?>(null)
    val accentColor = MutableStateFlow<Color?>(null)

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueRequestChannel = Channel<QueueRequest>(Channel.BUFFERED)
    private val queueUpdateSignal = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private val pendingIndices = MutableStateFlow<Set<Int>>(emptySet())
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
        val requiresReset = shouldResetQueue(state)
        prepareQueueForState(state, requiresReset)
        updateState(state)

        repositoryScope.launch {
            if (requiresReset) {
                val (start, end) = calculateInitialPageRange(state.currentIndex, state.queueSize)
                enqueueQueueRequest(start, end, RequestPriority.HIGH)
            } else {
                ensureQueueForIndex(state.currentIndex, RequestPriority.HIGH)
            }
        }
    }

    fun ensureQueueForIndex(index: Int, priority: RequestPriority = RequestPriority.NORMAL) {
        val currentState = musicState.value ?: return
        if (index < 0 || index >= currentState.queueSize) return

        val alreadyLoaded = queue.value.getOrNull(index) != null
        val isPending = pendingIndices.value.contains(index)
        if (alreadyLoaded || isPending) return

        repositoryScope.launch {
            enqueueQueueRequestForIndex(index, priority)
        }
    }

    private fun enqueueQueueRequestForIndex(index: Int, priority: RequestPriority) {
        val queueSize = musicState.value?.queueSize ?: queue.value.size
        if (queueSize <= 0) return

        val (start, end) = calculateWindowForIndex(index, queueSize)
        enqueueQueueRequest(start, end, priority)
    }

    private fun enqueueQueueRequest(start: Int, end: Int, priority: RequestPriority) {
        if (start >= end) return
        repositoryScope.launch {
            queueRequestChannel.send(QueueRequest(start, end, priority))
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
        val totalSize = musicState.value?.queueSize ?: queue.value.size
        if (totalSize <= 0) return

        val start = request.startIndex.coerceAtLeast(0)
        val end = request.endIndex.coerceAtMost(totalSize)
        if (start >= end) return

        val currentQueue = queue.value
        val currentPending = pendingIndices.value
        val missingIndices = (start until end).filter { index ->
            currentQueue.getOrNull(index) == null && !currentPending.contains(index)
        }

        if (missingIndices.isEmpty()) {
            return
        }

        val actualStart = missingIndices.first()
        val actualEndExclusive = missingIndices.last() + 1

        val requestedRange = actualStart until actualEndExclusive

        mutex.withLock {
            val updated = pendingIndices.value.toMutableSet()
            requestedRange.forEach { updated.add(it) }
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

    private suspend fun waitForQueueUpdate(requestId: Long, requestedRange: IntRange) {
        try {
            withTimeout(QUEUE_REQUEST_TIMEOUT_MS) {
                queueUpdateSignal.first { it == requestId }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("MusicRepository", "Queue update timed out for requestId=$requestId", e)
            clearPendingRange(requestedRange)
        }
    }

    private suspend fun clearPendingRange(range: IntRange) {
        mutex.withLock {
            val updated = pendingIndices.value.toMutableSet()
            range.forEach { updated.remove(it) }
            pendingIndices.value = updated
        }
    }

    private fun shouldResetQueue(state: MusicState): Boolean {
        val current = musicState.value ?: return true
        return current.queueHash != state.queueHash || current.queueSize != state.queueSize
    }

    private fun prepareQueueForState(state: MusicState, forceReset: Boolean) {
        val newSize = state.queueSize

        if (forceReset) {
            pendingIndices.value = emptySet()
        }

        queue.update { current ->
            when {
                newSize <= 0 -> emptyList()
                forceReset -> MutableList(newSize) { null }
                current.size == newSize -> current
                else -> MutableList(newSize) { index -> current.getOrNull(index) }
            }
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

    private fun calculateWindowForIndex(currentIndex: Int, queueSize: Int): Pair<Int, Int> {
        if (queueSize <= 0) return 0 to 0

        val clampedIndex = currentIndex.coerceIn(0, queueSize - 1)
        val windowRadius = DEFAULT_WINDOW_SIZE / 2
        var start = clampedIndex - windowRadius
        var end = clampedIndex + windowRadius + 1

        start = max(0, start)
        end = min(queueSize, end)

        val windowSize = end - start
        if (windowSize < DEFAULT_WINDOW_SIZE) {
            val remaining = DEFAULT_WINDOW_SIZE - windowSize
            if (start == 0) {
                end = min(queueSize, end + remaining)
            } else if (end == queueSize) {
                start = max(0, start - remaining)
            }
        }

        return start to end
    }

    fun updateQueue(
        hash: Long,
        trackDelta: Map<Int, TrackInfo>?,
        startIndex: Int,
        endIndexExclusive: Int,
        requestId: Long
    ) {
        val rangeDescription = if (startIndex < endIndexExclusive) "$startIndex..${endIndexExclusive - 1}" else "empty"
        Log.d("MusicRepository", "Queue update id=$requestId range=$rangeDescription hash=$hash")

        if (requestId != 0L && requestId < activeQueueRequestId.get()) {
            Log.d("MusicRepository", "Ignoring stale queue response id=$requestId")
            queueUpdateSignal.tryEmit(requestId)
            return
        }

        val currentHash = musicState.value?.queueHash
        if (currentHash != null && hash != currentHash) {
            Log.d("MusicRepository", "Queue hash changed $currentHash -> $hash")
            queue.value = emptyList()
        }

        val stateQueueSize = musicState.value?.queueSize ?: queue.value.size
        val targetSize = maxOf(stateQueueSize, endIndexExclusive, queue.value.size)

        queue.update { current ->
            if (targetSize == 0) {
                emptyList()
            } else {
                val base = MutableList(targetSize) { index ->
                    current.getOrNull(index)
                }
                trackDelta?.forEach { (index, track) ->
                    if (index in base.indices) {
                        base[index] = track
                    }
                }
                base
            }
        }

        if (!trackDelta.isNullOrEmpty()) {
            repositoryScope.launch {
                mutex.withLock {
                    val updated = pendingIndices.value.toMutableSet()
                    trackDelta.keys.forEach { updated.remove(it) }
                    pendingIndices.value = updated
                }
            }
        }

        repositoryScope.launch {
            queueUpdateSignal.emit(requestId)
        }
    }

    private fun updateState(state: MusicState) {
        musicState.value = state
    }
}
