package com.metrolist.music.presentation.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.metrolist.music.presentation.ui.components.TrackListItem
import com.metrolist.music.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun QueueScreen(viewModel: PlayerViewModel) {
    // Collect state from the ViewModel
    val musicQueue by viewModel.musicQueue.collectAsState()
    val musicState by viewModel.musicState.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()

    // Scroll state for the ScalingLazyColumn
    val lazyListState = rememberScalingLazyListState()

    // Scroll to the current track when the music state changes
    val queueSize = musicState?.queueSize ?: musicQueue.size

    LaunchedEffect(musicState?.currentIndex, queueSize, musicQueue) {
        val currentIndex = musicState?.currentIndex ?: return@LaunchedEffect
        if (queueSize <= 0) return@LaunchedEffect

        if (musicQueue.getOrNull(currentIndex) == null) {
            viewModel.ensureQueueForIndex(currentIndex)
        }

        runCatching {
            lazyListState.scrollToItem(currentIndex.coerceIn(0, queueSize - 1))
        }
    }

    LaunchedEffect(lazyListState, musicQueue, queueSize) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
            .filter { it.isNotEmpty() }
            .map { items ->
                val firstPos = items.minOf { it.index }.coerceAtLeast(0)
                val lastPos = items.maxOf { it.index }.coerceAtLeast(firstPos)
                Pair(firstPos, lastPos)
            }
            .distinctUntilChanged()
            .collectLatest { (firstVisible, lastVisible) ->
                if (queueSize <= 0) return@collectLatest
                if (firstVisible > 0) {
                    viewModel.ensureQueueForIndex(firstVisible - 1)
                }
                if (lastVisible < queueSize - 1) {
                    viewModel.ensureQueueForIndex(lastVisible + 1)
                }
            }
    }

    ScalingLazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize()
    ) {
        val passiveColor = accentColor?.let {
            lerp(Color.Black, it, 0.2f)
        } ?: Color.White.copy(alpha = 0.12f)

        val activeColor = accentColor?.let {
            lerp(Color.Black, it, 0.5f)
        } ?: Color.White.copy(alpha = 0.35f)
        items(queueSize, key = { it }) { absoluteIndex ->
            val track = musicQueue.getOrNull(absoluteIndex)
            TrackListItem(
                trackInfo = track,
                isPlaying = absoluteIndex == musicState?.currentIndex,
                passiveColor = passiveColor,
                activeColor = activeColor,
                onClick = {
                    if (track != null) {
                        viewModel.onQueueItemSelected(absoluteIndex)
                    } else {
                        viewModel.ensureQueueForIndex(absoluteIndex)
                    }
                }
            )
        }
    }
}
