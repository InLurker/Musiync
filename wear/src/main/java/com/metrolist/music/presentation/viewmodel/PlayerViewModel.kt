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

    // Make sure the object changes when the current track changes
    val musicState = musicRepository.musicState
    val accentColor = musicRepository.accentColor
    val musicQueue = musicRepository.queue
    val artworkBitmaps = musicRepository.artworks
    var displayedIndices = musicRepository.displayedIndices
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

    fun fetchPreviousTracksForScroll() {
        val firstDisplayed = displayedIndices.firstOrNull() ?: return
        val start =  max(0, firstDisplayed)
        val end = max(0, firstDisplayed - 8)

        if (start <= end) {
            musicRepository.requestPaginatedQueue(end, start)
            musicRepository.appendToDisplayedIndices(start, end)
        }
    }

    fun fetchNextTracksForScroll() {
        val lastDisplayed = displayedIndices.lastOrNull() ?: return
        val start = lastDisplayed + 1
        val end = min(musicState.value?.queueSize ?: return, lastDisplayed + 8)

        if (start <= end) {
            musicRepository.requestPaginatedQueue(start, end)
            musicRepository.appendToDisplayedIndices(start, end)
        }
    }
}