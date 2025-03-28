package com.metrolist.music.presentation.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import com.metrolist.music.presentation.ui.components.BlinkingDots
import com.metrolist.music.presentation.ui.components.TrackListItem
import com.metrolist.music.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
@Composable
fun QueueScreen(viewModel: PlayerViewModel) {
    // Collect state from the ViewModel
    val musicQueue by viewModel.musicQueue.collectAsState()
    val musicState by viewModel.musicState.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val artworkBitmaps by viewModel.artworkBitmaps.collectAsState()
    val displayedIndices = viewModel.displayedIndices
    // Scroll state for the ScalingLazyColumn
    val lazyListState = rememberScalingLazyListState()
    val isFetching by viewModel.isFetching.collectAsState()

    var isLoadingPrevious by remember { mutableStateOf(false) }
    var isLoadingNext by remember { mutableStateOf(false) }
    // Scroll to the current track when the music state changes
    LaunchedEffect(musicState?.currentIndex) {
        val currentIndex = musicState?.currentIndex ?: return@LaunchedEffect
        // wait for the list to be populated
        displayedIndices.indexOf(currentIndex).takeIf { it != -1 }?.let {
            lazyListState.animateScrollToItem(it, scrollOffset = 0)
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
            .debounce(300.milliseconds) // Reduce debounce time for better responsiveness
            .collect { visibleItems ->
                if (musicState == null || musicQueue.isEmpty()) return@collect
                if (visibleItems.isEmpty() || displayedIndices.isEmpty()) return@collect
                if (isFetching || isLoadingPrevious || isLoadingNext) return@collect
                // Ensure displayedIndices has elements
                val firstTrackIndex = displayedIndices.first()
                val lastTrackIndex = displayedIndices.last()
                val firstVisibleItemIndex = visibleItems.first().index
                val lastVisibleItemIndex = visibleItems.last().index


                val firstVisibleTrackIndex = displayedIndices.elementAt(firstVisibleItemIndex)
                val lastVisibleTrackIndex = displayedIndices.elementAt(lastVisibleItemIndex)

                if (firstVisibleTrackIndex <= firstTrackIndex + 2) {
                    if (firstTrackIndex <= 0) return@collect
                    isLoadingPrevious = true
                    viewModel.fetchPreviousTracksForScroll()
                } else if (lastVisibleTrackIndex >= lastTrackIndex - 2) {
                    if (lastTrackIndex >= (musicState!!.queueSize - 1)) return@collect
                    musicState?.queueSize?.let { queueSize ->
                        if (lastTrackIndex >= queueSize - 1) return@collect
                    }
                    isLoadingNext = true
                    viewModel.fetchNextTracksForScroll()
                }
            }
    }


    val passiveColor = accentColor?.let {
        lerp(Color.Black, it, 0.3f).copy(alpha = 0.6f)
    } ?: Color.Black.copy(alpha = 0.7f)
    Log.d("InColumn", displayedIndices.joinToString())
    val activeColor = accentColor?.let {
        lerp(Color.Black, it, 0.6f).copy(alpha = 0.8f)
    } ?: Color.Black.copy(alpha = 0.3f)

    LaunchedEffect(isLoadingNext, isLoadingPrevious) {
        if (isLoadingNext || isLoadingPrevious) {
            delay(3.seconds)
            isLoadingNext = false
            isLoadingPrevious = false
        }
    }

    LaunchedEffect(displayedIndices) {
        isLoadingNext = false
        isLoadingPrevious = false
    }

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
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            ScalingLazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = 32.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedIndices) { index ->
                    musicQueue[index]?.let { track ->
                        TrackListItem(
                            trackInfo = track,
                            isPlaying = index == musicState?.currentIndex,
                            passiveColor = passiveColor,
                            activeColor = activeColor,
                            artworkBitmap = artworkBitmaps[track.artworkUrl],
                            modifier = Modifier.padding(vertical = 4.dp),
                            onClick = {
                                viewModel.sendRequestSeekCommand(index)
                            }
                        )
                    }
                }
            }

            // Top loading indicator
            if (isLoadingPrevious) {
                BlinkingDots(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .zIndex(1f)
                )
            }

            // Bottom loading indicator
            if (isLoadingNext) {
                BlinkingDots(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .zIndex(1f)
                )
            }
        }
    }
}