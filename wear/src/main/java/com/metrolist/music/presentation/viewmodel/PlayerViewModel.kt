package com.metrolist.music.presentation.viewmodel

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.common.enumerated.WearCommandEnum
import com.metrolist.music.presentation.data.MusicRepository
import com.metrolist.music.presentation.data.RequestPriority
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

    val musicState = musicRepository.musicState
    val accentColor = musicRepository.accentColor
    val musicQueue = musicRepository.queue
    val artworkBitmaps = musicRepository.artworks
    val displayedIndices = musicRepository.displayedIndices

    val isFetching = musicRepository.pendingIndices
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    val currentTrack = musicState.combine(musicQueue) { state, queue ->
        state?.let { queue[it.currentIndex] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentArtwork = currentTrack.flatMapLatest { track ->
        artworkBitmaps.map { artworks -> track?.artworkUrl?.let(artworks::get) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun fetchCurrentState() {
        viewModelScope.launch {
            messageClientService.sendPlaybackCommand(WearCommandEnum.REQUEST_STATE)
        }
    }

    fun sendCommand(command: WearCommandEnum) {
        viewModelScope.launch {
            messageClientService.sendPlaybackCommand(command)
        }
    }

    fun updateAccentColor(bitmap: Bitmap?) {
        viewModelScope.launch {
            val dominantColor = bitmap?.extractThemeColor() ?: Color.Black
            musicRepository.setAccentColor(dominantColor)
        }
    }

    fun onQueueItemSelected(index: Int) {
        viewModelScope.launch {
            messageClientService.sendSeekToIndex(index)
        }
    }

    fun ensureQueueForIndex(index: Int) {
        musicRepository.ensureQueueForIndex(index)
    }

    fun fetchPreviousTracksForScroll() {
        val firstDisplayed = displayedIndices.firstOrNull() ?: return
        if (firstDisplayed <= 0) return

        val start = max(0, firstDisplayed - DEFAULT_WINDOW_SIZE)
        val end = firstDisplayed
        if (start < end) {
            musicRepository.requestQueueRange(start, end, RequestPriority.NORMAL)
        }
    }

    fun fetchNextTracksForScroll() {
        val lastDisplayed = displayedIndices.lastOrNull() ?: return
        val queueSize = musicState.value?.queueSize ?: return
        if (lastDisplayed >= queueSize - 1) return

        val start = lastDisplayed + 1
        val end = min(queueSize, start + DEFAULT_WINDOW_SIZE)
        if (start < end) {
            musicRepository.requestQueueRange(start, end, RequestPriority.NORMAL)
        }
    }

    private companion object {
        const val DEFAULT_WINDOW_SIZE = 7
    }
}
