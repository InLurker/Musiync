package com.metrolist.music.presentation.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.metrolist.music.presentation.ui.components.TrackListItem
import com.metrolist.music.presentation.viewmodel.PlayerViewModel

@Composable
fun QueueScreen(viewModel: PlayerViewModel) {
    // Collect state from the ViewModel
    val musicQueue by viewModel.musicQueue.collectAsState()
    val musicState by viewModel.musicState.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()

    // Sort tracks by their index
    val sortedTracks = musicQueue.toList().sortedBy { it.first }.map { it.second }

    // Scroll state for the ScalingLazyColumn
    val lazyListState = rememberScalingLazyListState()

    // Scroll to the current track when the music state changes
    LaunchedEffect(musicState?.currentIndex) {
        val currentIndex = musicState?.currentIndex ?: return@LaunchedEffect
        lazyListState.scrollToItem(currentIndex)
    }

    ScalingLazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize()
    ) {
        val passiveColor = accentColor?.let {
            lerp(Color.Black, it, 0.2f)
        } ?: Color.White

        val activeColor = accentColor?.let {
            lerp(Color.Black, it, 0.5f)
        } ?: Color.White
        itemsIndexed(sortedTracks) { index, track ->
            TrackListItem(
                trackInfo = track,
                isPlaying = index == musicState?.currentIndex,
                passiveColor = passiveColor,
                activeColor = activeColor,
            )
        }
    }
}