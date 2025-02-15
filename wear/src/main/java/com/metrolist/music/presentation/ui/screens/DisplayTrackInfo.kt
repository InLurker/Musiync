package com.metrolist.music.presentation.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import com.metrolist.music.models.TrackInfo

@Composable
fun DisplayTrackInfo(trackInfo: TrackInfo, onCommand: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
            Text(
                "⏮️",
                modifier = Modifier
                    .clickable {
                        onCommand("PREVIOUS")
                    }
            )
            Button(onClick = { onCommand("PLAY/PAUSE") }) {
                Text("▶️")
            }
            Button(onClick = { onCommand("NEXT") }) {
                Text("⏭️")
            }
        }
    }
}