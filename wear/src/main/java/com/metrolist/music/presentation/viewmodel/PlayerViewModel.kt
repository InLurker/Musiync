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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val messageClientService: MessageClientService
) : ViewModel() {

    // Make sure the object changes when the current track changes
    val musicState = musicRepository.musicState
    val accentColor = musicRepository.accentColor
    val musicQueue = musicRepository.queue

    val currentTrack = musicState.combine(musicQueue) { state, queue ->
        state?.let { queue.get(it.currentIndex) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
}