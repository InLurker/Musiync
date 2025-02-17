package com.metrolist.music.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImagePainter.State.Empty.painter
import com.metrolist.music.R
import com.metrolist.music.common.enumerated.WearCommandEnum
import com.metrolist.music.models.TrackInfo

@Composable
fun DisplayTrackInfo(trackInfo: TrackInfo, onCommand: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                MaterialTheme.colors.background.copy(alpha = 0.3f),
            )
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(text = trackInfo.trackName ?: "Unknown Track", fontSize = 18.sp, color = Color.White)
        Text(text = trackInfo.artistName ?: "Unknown Artist", fontSize = 14.sp, color = Color.Gray)
        Text(text = trackInfo.albumName ?: "Unknown Album", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(20.dp))

        // Media Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            CompactButton(onClick = { onCommand(WearCommandEnum.PREVIOUS.name) }) {
                Icon(
                    painter = painterResource(id = R.drawable.skip_previous),
                    contentDescription = "Previous"
                )
            }
            CompactButton(onClick = { onCommand(WearCommandEnum.PLAY_PAUSE.name) }) {
                Icon(
                    painter = painterResource(id = R.drawable.play),
                    contentDescription = "Play/Pause"
                )
            }
            CompactButton(onClick = { onCommand(WearCommandEnum.NEXT.name) }) {
                Icon(
                    painter = painterResource(id = R.drawable.skip_next),
                    contentDescription = "Next"
                )
            }
        }
    }
}