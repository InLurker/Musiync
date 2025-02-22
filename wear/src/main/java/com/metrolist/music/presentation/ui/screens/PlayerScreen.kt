package com.metrolist.music.presentation.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.asFlow
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import coil.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.common.enumerated.WearCommandEnum
import com.metrolist.music.presentation.theme.MetrolistTheme
import com.metrolist.music.presentation.ui.components.DisplayTrackInfo
import com.metrolist.music.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val trackInfo by viewModel.currentTrack.asFlow().collectAsState(initial = null)
    val albumArtFlow: Flow<Bitmap?> = trackInfo?.artworkBitmap ?: flowOf(null)
    val albumArt by albumArtFlow.collectAsState(initial = null)
    val accentColor by viewModel.accentColor.asFlow().collectAsState(initial = Color.Black)

    LaunchedEffect(trackInfo) {
        viewModel.updateAlbumArt(albumArtFlow)
    }

    MetrolistTheme {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .background(MaterialTheme.colors.background)
        ) {
            AsyncImage(
                model = albumArt,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
            )
            TimeText()
            trackInfo?.let { track ->
                DisplayTrackInfo(track)

                Spacer(modifier = Modifier.height(20.dp))

                DisplayPlaybackControl(accentColor, onCommand = viewModel::sendCommand)

            } ?: Text("Waiting for track info...", color = MaterialTheme.colors.primary)
        }
    }
}

@Composable
fun DisplayPlaybackControl(accentColor: Color, onCommand: (WearCommandEnum) -> Unit) {
    // Media Controls
    val darkerDominant = accentColor.also {
        it.copy(
            red = it.red * 0.8f,
            green = it.green * 0.8f,
            blue = it.blue * 0.8f
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        CompactButton(
            onClick = { onCommand(WearCommandEnum.PREVIOUS) },
            colors = ButtonDefaults.buttonColors(backgroundColor = darkerDominant)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.skip_previous),
                contentDescription = "Previous"
            )
        }
        CompactButton(
            onClick = { onCommand(WearCommandEnum.PLAY_PAUSE) },
            colors = ButtonDefaults.buttonColors(backgroundColor = darkerDominant)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.play),
                contentDescription = "Play/Pause"
            )
        }
        CompactButton(
            onClick = { onCommand(WearCommandEnum.NEXT) },
            colors = ButtonDefaults.buttonColors(backgroundColor = darkerDominant)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.skip_next),
                contentDescription = "Next"
            )
        }
    }
}


@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    PlayerScreen()
}