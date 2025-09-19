package com.metrolist.music.presentation.data

import android.util.Log
import androidx.compose.ui.graphics.Color
import com.metrolist.music.common.models.MusicState
import com.metrolist.music.common.models.TrackInfo
import com.metrolist.music.presentation.wear.MessageClientService
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Singleton
class MusicRepository @Inject constructor(
    private val messageClientService: MessageClientService
) {

    companion object {
        private const val QUEUE_WINDOW_SIZE = 5
        private const val QUEUE_REQUEST_TIMEOUT_MS = 5_000L
    }

    val queue = MutableStateFlow<MutableMap<Int, TrackInfo>>(mutableMapOf())
    val musicState = MutableStateFlow<MusicState?>(null)
    val accentColor = MutableStateFlow<Color?>(null)

    private val scope = CoroutineScope(Dispatchers.IO)
    private val queueUpdateSignal = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    private val loadedRange = MutableStateFlow<IntRange?>(null)
    private val nextQueueRequestId = AtomicLong(0)
    private val activeQueueRequestId = AtomicLong(0)

    fun setAccentColor(color: Color) {
        accentColor.value = color
    }

    fun handleIncomingState(state: MusicState) {
        if (shouldRequestQueue(state)) {
            scope.launch {
                val requestId = requestPaginatedQueue(state.currentIndex, state.queueSize)
                if (requestId != null) {
                    waitForQueueUpdate(requestId)
                }
                updateState(state)
            }
        } else {
            updateState(state)
        }
    }

    fun ensureQueueForIndex(index: Int) {
        val currentState = musicState.value ?: return
        if (index < 0 || index >= currentState.queueSize) return
        if (queue.value.containsKey(index)) return

        scope.launch {
            val requestId = requestPaginatedQueue(index, currentState.queueSize)
            if (requestId != null) {
                waitForQueueUpdate(requestId)
            }
        }
    }

    private fun calculatePageRange(currentIndex: Int, queueSize: Int): Pair<Int, Int> {
        if (queueSize <= 0) {
            return 0 to 0
        }

        val clampedIndex = currentIndex.coerceIn(0, queueSize - 1)
        val windowRadius = QUEUE_WINDOW_SIZE / 2
        var start = clampedIndex - windowRadius
        var end = clampedIndex + windowRadius + 1

        start = max(0, start)
        end = min(queueSize, end)

        val windowSize = end - start
        if (windowSize < QUEUE_WINDOW_SIZE) {
            val remaining = QUEUE_WINDOW_SIZE - windowSize
            if (start == 0) {
                end = min(queueSize, end + remaining)
            } else if (end == queueSize) {
                start = max(0, start - remaining)
            }
        }

        return start to end
    }

    private fun requestPaginatedQueue(currentIndex: Int, queueSize: Int): Long? {
        if (queueSize <= 0) return null

        val (start, end) = calculatePageRange(currentIndex, queueSize)
        if (start >= end) {
            return null
        }

        val requestId = nextQueueRequestId.incrementAndGet()
        activeQueueRequestId.set(requestId)
        messageClientService.sendQueueRangeRequest(start, end, requestId)
        return requestId
    }

    private suspend fun waitForQueueUpdate(requestId: Long) {
        try {
            withTimeout(QUEUE_REQUEST_TIMEOUT_MS) {
                queueUpdateSignal.first { it == requestId }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e("MusicRepository", "Queue update timed out for requestId=$requestId", e)
        }
    }

    private fun shouldRequestQueue(state: MusicState): Boolean {
        val current = musicState.value ?: return true
        if (current.queueHash != state.queueHash || current.queueSize != state.queueSize) {
            loadedRange.value = null
            queue.value = mutableMapOf()
            return true
        }

        val range = loadedRange.value ?: return true
        if (!range.contains(state.currentIndex)) {
            return true
        }

        return !queue.value.containsKey(state.currentIndex)
    }

    fun updateQueue(
        hash: Int,
        trackDelta: Map<Int, TrackInfo>?,
        startIndex: Int,
        endIndexExclusive: Int,
        requestId: Long
    ) {
        val rangeDescription = if (startIndex < endIndexExclusive) "$startIndex..${endIndexExclusive - 1}" else "empty"
        Log.d("MusicRepository", "Queue update id=$requestId range=$rangeDescription hash=$hash")

        if (requestId != 0L && requestId < activeQueueRequestId.get()) {
            Log.d("MusicRepository", "Ignoring stale queue response id=$requestId")
            return
        }

        val currentHash = musicState.value?.queueHash
        if (hash != currentHash) {
            Log.d("MusicRepository", "Queue hash changed $currentHash -> $hash")
            queue.value = mutableMapOf()
        }

        val updated = queue.value.toMutableMap().apply {
            trackDelta?.let { putAll(it) }
        }
        queue.value = updated

        loadedRange.value = if (startIndex < endIndexExclusive) {
            startIndex until endIndexExclusive
        } else {
            null
        }

        scope.launch {
            queueUpdateSignal.emit(requestId)
        }
    }

    private fun updateState(state: MusicState) {
        musicState.value = state
    }
}
