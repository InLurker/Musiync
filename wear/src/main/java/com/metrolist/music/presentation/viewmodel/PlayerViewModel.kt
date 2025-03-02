package com.metrolist.music.presentation.viewmodel

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.common.enumerated.WearCommandEnum
import com.metrolist.music.presentation.data.MusicRepository
import com.metrolist.music.presentation.helper.extractThemeColor
import com.metrolist.music.presentation.wear.MessageClientService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min


@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val messageClientService: MessageClientService
) : ViewModel() {

    // Make sure the object changes when the current track changes
    var queueHash = musicRepository.musicState.value?.queueHash
    val musicState = musicRepository.musicState
    val accentColor = musicRepository.accentColor
    val musicQueue = musicRepository.queue
    val artworkBitmaps = musicRepository.artworks
    var displayedIndices by mutableStateOf(listOf<Int>())

    val currentTrack = musicState.combine(musicQueue) { state, queue ->
        state?.let { queue.get(it.currentIndex) }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentArtwork = currentTrack.flatMapLatest { track ->
        track?.artworkUrl.let { url ->
            artworkBitmaps.map { artworks ->
                artworks[url]
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)


    init {
        viewModelScope.launch {
            musicState.collect { state ->
                if (state?.queueHash != queueHash) {
                    queueHash = state?.queueHash
                    displayedIndices = emptyList()
                }
                state?.currentIndex?.let { index ->
                    handleQueuePagination(index)
                }
            }
        }
    }

    private fun handleQueuePagination(currentIndex: Int) {
        if (musicQueue.value.isEmpty()) return

        if (displayedIndices.isEmpty()) {
            resetDisplayedQueue(currentIndex)
        } else {
            val firstDisplayed = displayedIndices.first()
            val lastDisplayed = displayedIndices.last()
            if (currentIndex in (firstDisplayed.rangeTo(firstDisplayed + 1))) {
                fetchPreviousTracks(currentIndex)
            } else if (currentIndex in (lastDisplayed - 1).rangeTo(lastDisplayed)) {
                fetchNextTracks(currentIndex)
            } else if (currentIndex < firstDisplayed || currentIndex > lastDisplayed) {
                resetDisplayedQueue(currentIndex)
            }
        }
    }

    private fun fetchNextTracks(currentIndex: Int) {
        val start = (displayedIndices.lastOrNull()?.plus(1) ?: return)
        val end = min(musicState.value!!.queueSize, currentIndex + 8)
        musicRepository.requestPaginatedQueue(start, end)
        appendToDisplayedIndices(start, end)
    }

    private fun fetchPreviousTracks(currentIndex: Int) {
        val end =  (displayedIndices.firstOrNull() ?: return)
        val start = max(0, currentIndex - 7)
        musicRepository.requestPaginatedQueue(start, end)
        appendToDisplayedIndices(end, start)
    }

    private fun resetDisplayedQueue(currentIndex: Int) {
        displayedIndices = emptyList()
        val range = musicRepository.calculateInitialPageRange(currentIndex, musicState.value?.queueSize ?: 0)
        musicRepository.requestPaginatedQueue(range.first, range.second)
        appendToDisplayedIndices(range.first, range.second)
    }

    fun fetchCurrentState() {
        messageClientService.sendPlaybackCommand(WearCommandEnum.REQUEST_STATE)
    }

    fun sendCommand(command: WearCommandEnum) {
        messageClientService.sendPlaybackCommand(command)
    }

    fun updateAccentColor(bitmap: Bitmap?) {
        viewModelScope.launch {
            val dominantColor = bitmap?.extractThemeColor() ?: Color.Black
            musicRepository.setAccentColor(dominantColor)
        }
    }

    fun appendBitmapToArtworkMap(url: String, bitmap: Bitmap) {
        musicRepository.artworks.value[url] = bitmap
    }

    fun fetchPreviousTracksForScroll() {
        val firstDisplayed = displayedIndices.firstOrNull() ?: return
        val start =  max(0, firstDisplayed)
        val end = max(0, firstDisplayed - 8)

        if (start <= end) {
            musicRepository.requestPaginatedQueue(end, start)
            appendToDisplayedIndices(start, end)
        }
    }

    fun fetchNextTracksForScroll() {
        val lastDisplayed = displayedIndices.lastOrNull() ?: return
        val start = lastDisplayed + 1
        val end = min(musicQueue.value.keys.last(), lastDisplayed + 8)

        if (start <= end) {
            musicRepository.requestPaginatedQueue(start, end)
            appendToDisplayedIndices(start, end)
        }
    }

    fun appendToDisplayedIndices(start: Int, end: Int) {
        val range = if (start <= end) start until end else end until start
        if (start >= end) {
            // If reversed, insert at the front in reverse order
            displayedIndices = range.reversed().toList() + displayedIndices
        } else {
            // Normal case: append at the end
            displayedIndices += range
        }
    }
}