package com.metrolist.music.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.common.models.TrackInfo

@Composable
fun DisplayTrackInfo(
    trackInfo: TrackInfo,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
    ) {
        MarqueeText(
            text = trackInfo.trackName,
            fontSize = 14.sp,
            fontColor = accentColor,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        val albumArtistText = listOfNotNull(
            trackInfo.artistName.takeIf { it.isNotEmpty()},
            trackInfo.albumName.takeIf { it.isNotEmpty() }
        ).joinToString(" - ")
        albumArtistText.takeIf { it.isNotEmpty() }?.let {
            MarqueeText(
                text = it,
                fontSize = 10.sp,
                fontColor = accentColor
            )
        }
    }
}

