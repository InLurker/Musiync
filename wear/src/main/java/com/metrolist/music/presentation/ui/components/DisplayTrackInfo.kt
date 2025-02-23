package com.metrolist.music.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import com.metrolist.music.common.models.TrackInfo

@Composable
fun DisplayTrackInfo(
    trackInfo: TrackInfo,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
    ) {
        MarqueeText(
            text = trackInfo.trackName ?: "Unknown Track",
            fontSize = 14.sp,
            fontColor = MaterialTheme.colors.onSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        val albumArtistText = listOfNotNull(
            trackInfo.artistName.takeIf { it?.isNotEmpty() ?: false },
            trackInfo.albumName.takeIf { it?.isNotEmpty() ?: false }
        ).joinToString(" - ")
        albumArtistText.takeIf { it.isNotEmpty() }?.let {
            MarqueeText(
                text = it,
                fontSize = 10.sp,
                fontColor = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}

