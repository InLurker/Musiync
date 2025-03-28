package com.metrolist.music.presentation.viewmodel

import android.graphics.Bitmap
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

    // Expose UI state from repository
    val musicState = musicRepository.musicState
    val accentColor = musicRepository.accentColor
    val musicQueue = musicRepository.queue
    val artworkBitmaps = musicRepository.artworks
    val displayedIndices = musicRepository.displayedIndices
    val isFetching = musicRepository.isFetching
    
    val currentTrack = musicState.combine(musicQueue) { state, queue ->
        state?.let { queue[it.currentIndex] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val currentArtwork = currentTrack.flatMapLatest { track ->
        track?.artworkUrl.let { url ->
            artworkBitmaps.map { artworks ->
                artworks[url]
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Request initial state when ViewModel is created
        fetchCurrentState()
    }

    fun fetchCurrentState() {
        messageClientService.sendPlaybackCommand(WearCommandEnum.REQUEST_STATE)
    }

    fun sendCommand(command: WearCommandEnum) {
        messageClientService.sendPlaybackCommand(command)
    }

    fun sendRequestSeekCommand(index: Int) {
        messageClientService.sendRequestSeekCommand(WearCommandEnum.SEEK_TO, index)
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

    /**
     * Fetches previous tracks when scrolling up in the UI.
     * This allows loading arbitrary sections of the queue without requiring all previous tracks.
     */
    fun fetchPreviousTracksForScroll() {
        val firstDisplayed = displayedIndices.firstOrNull() ?: return
        if (firstDisplayed <= 0) return
        
        // Load a page of tracks before the current displayed range
        val start = max(0, firstDisplayed - 7)
        val end = firstDisplayed
        
        if (start < end) {
            musicRepository.requestPaginatedQueue(start, end)
        }
    }

    /**
     * Fetches next tracks when scrolling down in the UI.
     * This allows loading arbitrary sections of the queue without requiring all tracks in between.
     */
    fun fetchNextTracksForScroll() {
        val lastDisplayed = displayedIndices.lastOrNull() ?: return
        val queueSize = musicState.value?.queueSize ?: return
        
        if (lastDisplayed >= queueSize - 1) return
        
        // Load a page of tracks after the current displayed range
        val start = lastDisplayed + 1
        val end = min(queueSize, start + 7)
        
        if (start < end) {
            musicRepository.requestPaginatedQueue(start, end)
        }
    }
}