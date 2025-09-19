package com.metrolist.music.presentation.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
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

    // Sort tracks by their index and keep track of absolute indices
    val queueEntries = remember(musicQueue) {
        musicQueue.toList().sortedBy { it.first }
    }

    // Scroll state for the ScalingLazyColumn
    val lazyListState = rememberScalingLazyListState()

    // Scroll to the current track when the music state changes
    LaunchedEffect(musicState?.currentIndex, queueEntries) {
        val currentIndex = musicState?.currentIndex ?: return@LaunchedEffect
        val position = queueEntries.indexOfFirst { it.first == currentIndex }
        if (position == -1) {
            viewModel.ensureQueueForIndex(currentIndex)
            return@LaunchedEffect
        }
        runCatching {
            lazyListState.scrollToItem(position)
        }
    }

    val queueSize = musicState?.queueSize ?: 0
    LaunchedEffect(lazyListState, queueEntries.size, queueSize) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
            .filter { it.isNotEmpty() }
            .map { items ->
                val firstPos = items.minOf { it.index }.coerceAtLeast(0)
                val lastPos = items.maxOf { it.index }.coerceAtLeast(firstPos)
                val firstAbsolute = queueEntries.getOrNull(firstPos)?.first
                val lastAbsolute = queueEntries.getOrNull(lastPos)?.first
                Pair(firstAbsolute, lastAbsolute)
            }
            .distinctUntilChanged()
            .collectLatest { (firstAbsolute, lastAbsolute) ->
                if (queueSize <= 0) return@collectLatest
                firstAbsolute?.let { index ->
                    if (index > 0) {
                        viewModel.ensureQueueForIndex(index - 1)
                    }
                }
                lastAbsolute?.let { index ->
                    if (index < queueSize - 1) {
                        viewModel.ensureQueueForIndex(index + 1)
                    }
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
        itemsIndexed(queueEntries, key = { _, entry -> entry.first }) { position, entry ->
            val (absoluteIndex, track) = entry
            TrackListItem(
                trackInfo = track,
                isPlaying = absoluteIndex == musicState?.currentIndex,
                passiveColor = passiveColor,
                activeColor = activeColor,
                onClick = { viewModel.onQueueItemSelected(absoluteIndex) }
            )
        }
    }
}
