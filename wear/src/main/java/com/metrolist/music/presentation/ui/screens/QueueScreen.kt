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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import com.metrolist.music.presentation.ui.components.TrackListItem
import com.metrolist.music.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
@Composable
fun QueueScreen(viewModel: PlayerViewModel) {
    val musicQueue by viewModel.musicQueue.collectAsState()
    val musicState by viewModel.musicState.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val artworkBitmaps by viewModel.artworkBitmaps.collectAsState()
    val displayedIndices = viewModel.displayedIndices
    val isFetching by viewModel.isFetching.collectAsState()

    var isLoadingPrevious by remember { mutableStateOf(false) }
    var isLoadingNext by remember { mutableStateOf(false) }

    val lazyListState = rememberScalingLazyListState()
    val isScrollingLocked = remember { mutableStateOf(false) }

    val passiveColor = accentColor?.let {
        lerp(Color.Black, it, 0.2f)
    } ?: Color.White.copy(alpha = 0.12f)

    val activeColor = accentColor?.let {
        lerp(Color.Black, it, 0.5f)
    } ?: Color.White.copy(alpha = 0.35f)

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.isScrollInProgress }
            .collect { isScrolling ->
                isScrollingLocked.value = isScrolling
            }
    }

    LaunchedEffect(lazyListState, musicQueue) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
            .filter { it.isNotEmpty() }
            .map { info ->
                val first = info.minOf { it.index }
                val last = info.maxOf { it.index }
                first to last
            }
            .distinctUntilChanged()
            .debounce(200.milliseconds)
            .collectLatest { (firstVisible, lastVisible) ->
                if (displayedIndices.isEmpty() || isFetching || isScrollingLocked.value) return@collectLatest
                val safeFirst = firstVisible.coerceIn(0, displayedIndices.lastIndex)
                val safeLast = lastVisible.coerceIn(0, displayedIndices.lastIndex)

                if (safeFirst <= 2 && displayedIndices.first() > 0 && !isLoadingPrevious) {
                    isLoadingPrevious = true
                    viewModel.fetchPreviousTracksForScroll()
                }

                if (safeLast >= displayedIndices.size - 3 &&
                    displayedIndices.last() < (musicState?.queueSize ?: 0) - 1 &&
                    !isLoadingNext
                ) {
                    isLoadingNext = true
                    viewModel.fetchNextTracksForScroll()
                }
            }
    }

    LaunchedEffect(isLoadingNext, isLoadingPrevious) {
        if (isLoadingNext || isLoadingPrevious) {
            delay(2.seconds)
            isLoadingNext = false
            isLoadingPrevious = false
        }
    }

    LaunchedEffect(displayedIndices.size) {
        if (displayedIndices.isNotEmpty()) {
            isLoadingNext = false
            isLoadingPrevious = false
        }
    }

    LaunchedEffect(musicState?.currentIndex) {
        val currentIndex = musicState?.currentIndex ?: return@LaunchedEffect
        val targetPosition = displayedIndices.indexOf(currentIndex)
        if (targetPosition >= 0) {
            runCatching {
                lazyListState.animateScrollToItem(targetPosition)
            }.onFailure { throwable ->
                Log.w("QueueScreen", "Failed to scroll to index=$currentIndex", throwable)
            }
        } else {
            viewModel.ensureQueueForIndex(currentIndex)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (displayedIndices.isEmpty()) {
            if (isFetching) {
                CircularProgressIndicator()
            } else {
                Text(text = "Queue is empty", color = Color.White)
            }
        } else {
            ScalingLazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = 48.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedIndices, key = { it }) { index ->
                    val track = musicQueue[index]
                    if (track == null) {
                        // Placeholder for pending items
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp)
                                .padding(horizontal = 8.dp)
                                .zIndex(0f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "Loading...", color = Color.White.copy(alpha = 0.6f))
                        }
                        return@items
                    }
                    TrackListItem(
                        trackInfo = track,
                        isPlaying = index == musicState?.currentIndex,
                        passiveColor = passiveColor,
                        activeColor = activeColor,
                        artworkBitmap = artworkBitmaps[track.artworkUrl],
                        onClick = { viewModel.onQueueItemSelected(index) }
                    )
                }
            }

            if (isLoadingPrevious) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .zIndex(1f)
                )
            }

            if (isLoadingNext) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .zIndex(1f)
                )
            }
        }
    }
}
