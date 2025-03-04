package com.metrolist.music.presentation.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import com.metrolist.music.presentation.ui.components.TrackListItem
import com.metrolist.music.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.FlowPreview

@OptIn(FlowPreview::class)
@Composable
fun QueueScreen(viewModel: PlayerViewModel) {
    // Collect state from the ViewModel
    val musicQueue by viewModel.musicQueue.collectAsState()
    val musicState by viewModel.musicState.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val displayedIndices = viewModel.displayedIndices
    // Scroll state for the ScalingLazyColumn
    val lazyListState = rememberScalingLazyListState()

    // Scroll to the current track when the music state changes
    LaunchedEffect(musicState?.currentIndex) {
        val currentIndex = musicState?.currentIndex ?: return@LaunchedEffect
        lazyListState.scrollToItem(currentIndex)
    }

//    LaunchedEffect(lazyListState) {
//        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
//            .debounce(1.seconds) // Prevent rapid consecutive calls
//            .collect { visibleItems ->
//                if (visibleItems.isEmpty()) return@collect
//
//                val firstVisibleItem = visibleItems.first()
//                val lastVisibleItem = visibleItems.last()
//
//                // Convert LazyList indices to actual track indices
//                val firstVisibleTrackIndex = displayedIndices.getOrNull(firstVisibleItem.index)
//                val lastVisibleTrackIndex = displayedIndices.getOrNull(lastVisibleItem.index)
//
//                firstVisibleTrackIndex?.let { trackIndex ->
//                    if (trackIndex <= (displayedIndices.firstOrNull() ?: return@let) + 2) {
//                        viewModel.fetchPreviousTracksForScroll()
//                    }
//                }
//
//                lastVisibleTrackIndex?.let { trackIndex ->
//                    if (trackIndex >= (displayedIndices.lastOrNull() ?: return@let) - 2) {
//                        viewModel.fetchNextTracksForScroll()
//                    }
//                }
//            }
//    }


    val passiveColor = accentColor?.let {
        lerp(Color.Black, it, 0.2f)
    } ?: Color.Black
    Log.d("InColumn", displayedIndices.joinToString())
    val activeColor = accentColor?.let {
        lerp(Color.Black, it, 0.5f)
    } ?: Color.Black

    // Display the list
    if (displayedIndices.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "Queue is Empty",
                color = Color.White
            )
        }
    }

    ScalingLazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(displayedIndices) { index ->
            musicQueue[index]?.let { track ->
                TrackListItem(
                    trackInfo = track,
                    isPlaying = index == musicState?.currentIndex,
                    passiveColor = passiveColor,
                    activeColor = activeColor,
                )
            }
        }
    }
}