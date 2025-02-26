package com.metrolist.music.presentation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.metrolist.music.presentation.ui.components.DisplayTrackInfo
import com.metrolist.music.presentation.ui.components.PlaybackControl
import com.metrolist.music.presentation.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Collect state from the ViewModel
    val currentTrack by viewModel.currentTrack.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val musicState by viewModel.musicState.collectAsState()

    // Fetch current state when the screen is started
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.fetchCurrentState()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(4f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(6f)
            ) {

                val whiteAccentedColor = accentColor?.let {
                    lerp(Color.White, it, 0.1f)
                } ?: Color.White

                currentTrack?.let { track ->
                    DisplayTrackInfo(track, whiteAccentedColor)
                } ?: Text(
                    text = "No Ongoing Track",
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                PlaybackControl(
                    accentColor = accentColor,
                    isPlaying = musicState?.isPlaying ?: false,
                    onCommand = viewModel::sendCommand,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}



@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    PlayerScreen(hiltViewModel())
}