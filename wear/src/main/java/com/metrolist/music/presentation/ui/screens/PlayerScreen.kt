package com.metrolist.music.presentation.ui.screens

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.metrolist.music.presentation.theme.MetrolistTheme
import com.metrolist.music.presentation.ui.components.DisplayTrackInfo
import com.metrolist.music.presentation.ui.components.PlaybackControl
import com.metrolist.music.presentation.ui.components.TrackListItem
import com.metrolist.music.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.flowOf

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
) {

    // track info is viewModel.musicQueue[viewModel.musicState.currentIndex]
    val trackQueue by viewModel.musicQueue.asFlow().collectAsState(initial = null)
    val musicState by viewModel.musicState.asFlow().collectAsState(initial = null)
    val trackInfo = musicState?.let { state ->
        trackQueue?.takeIf { state.currentIndex < state.queueSize }?.get(state.currentIndex)
    }
    val albumArt by trackInfo?.artworkBitmap?.collectAsState(initial = null) ?: remember { mutableStateOf(null) }
    val accentColor by viewModel.accentColor.asFlow().collectAsState(initial = Color.Black)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                    -> viewModel.fetchCurrentState()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(albumArt) {
        viewModel.updateAlbumArt(albumArt)
    }

    // LazyListState for ScalingLazyColumn
    val lazyListState = rememberScalingLazyListState()

    // Scroll to the currently playing track when currentIndex changes
    LaunchedEffect(musicState?.currentIndex) {
        val currentIndex = musicState?.currentIndex ?: return@LaunchedEffect
        lazyListState.scrollToItem(currentIndex)
    }

    MetrolistTheme {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .background(MaterialTheme.colors.background)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(albumArt ?: trackInfo?.artworkUrl)
                    .crossfade(1000)
                    .build(),
                onSuccess = { result ->
                    if (albumArt == null) {
                        val bitmapDrawable = result.result.drawable
                        if (bitmapDrawable is BitmapDrawable) {
                            val bitmap = bitmapDrawable.bitmap
                            trackInfo?.artworkBitmap = flowOf(bitmap)
                        }
                    }
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight()
            )
            TimeText()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colors.background.copy(alpha = 0.7f),
                    )
            ) {

                ScalingLazyColumn(
                    modifier = Modifier
                        .weight(5f),
                    state = lazyListState
                ) {
                    // Convert the trackQueue map to a sorted list of TrackInfo
                    val sortedTracks = trackQueue?.toList()?.sortedBy { it.first }?.map { it.second } ?: emptyList()
                    itemsIndexed(sortedTracks) { index, track ->
                        TrackListItem (
                            track = track,
                            isPlaying = index == musicState?.currentIndex,
                            onClick = { /* Handle track click */ }
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(6f)
                ) {
                    trackInfo?.let { track ->
                        DisplayTrackInfo(
                            track
                        )
                    } ?: Text("Waiting for track info...", color = MaterialTheme.colors.primary)

                    PlaybackControl(
                        accentColor,
                        musicState?.isPlaying ?: false,
                        viewModel::sendCommand,
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp
                            )
                    )
                }
            }
        }
    }
}



@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    PlayerScreen()
}