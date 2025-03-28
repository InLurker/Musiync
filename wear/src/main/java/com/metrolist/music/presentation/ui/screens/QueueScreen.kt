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
    val isFetching by viewModel.isFetching.collectAsState()

    // UI loading state
    var isLoadingPrevious by remember { mutableStateOf(false) }
    var isLoadingNext by remember { mutableStateOf(false) }
    
    // Scroll state for the ScalingLazyColumn
    val lazyListState = rememberScalingLazyListState()

    // Auto-scroll to the current track when it changes
    LaunchedEffect(musicState?.currentIndex) {
        val currentIndex = musicState?.currentIndex ?: return@LaunchedEffect
        
        displayedIndices.indexOf(currentIndex).takeIf { it != -1 }?.let { indexPosition ->
            lazyListState.animateScrollToItem(indexPosition, scrollOffset = 0)
        }
    }

    // Calculate UI colors based on accent color
    val passiveColor = accentColor?.let {
        lerp(Color.Black, it, 0.3f).copy(alpha = 0.6f)
    } ?: Color.Black.copy(alpha = 0.7f)
    
    val activeColor = accentColor?.let {
        lerp(Color.Black, it, 0.6f).copy(alpha = 0.8f)
    } ?: Color.Black.copy(alpha = 0.3f)

    // Load more tracks when scrolling near the edges
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
            .debounce(300.milliseconds)
            .collect { visibleItems ->
                // Skip if we don't have enough data or we're already fetching
                if (musicState == null || musicQueue.isEmpty() || 
                    visibleItems.isEmpty() || displayedIndices.isEmpty() ||
                    isFetching || isLoadingPrevious || isLoadingNext) {
                    return@collect
                }
                
                val firstVisibleItemIndex = visibleItems.first().index
                val lastVisibleItemIndex = visibleItems.last().index

                if (firstVisibleItemIndex >= displayedIndices.size || 
                    lastVisibleItemIndex >= displayedIndices.size) {
                    return@collect
                }

                // perform pagination
                if (firstVisibleItemIndex <= 2 && !isLoadingPrevious) {
                    isLoadingPrevious = true
                    viewModel.fetchPreviousTracksForScroll()
                }

                else if (lastVisibleItemIndex >= displayedIndices.size - 3 && !isLoadingNext) {
                    isLoadingNext = true
                    viewModel.fetchNextTracksForScroll()
                }
            }
    }

    // Reset loading states after delay or when displayedIndices changes
    LaunchedEffect(isLoadingNext, isLoadingPrevious) {
        if (isLoadingNext || isLoadingPrevious) {
            delay(2.seconds)
            isLoadingNext = false
            isLoadingPrevious = false
        }
    }

    LaunchedEffect(displayedIndices.size) {
        isLoadingNext = false
        isLoadingPrevious = false
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        if (displayedIndices.isEmpty()) {
            Text(
                text = "Queue is Empty",
                color = Color.White
            )
        } else {
            Log.d("QueueScreen", "Displayed indices: $displayedIndices")
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
                            onClick = { viewModel.sendRequestSeekCommand(index) }
                        )
                    }
                }
            }

            // Loading indicators
            if (isLoadingPrevious) {
                BlinkingDots(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .zIndex(1f)
                )
            }

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
