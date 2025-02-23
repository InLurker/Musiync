package com.metrolist.music.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.metrolist.music.common.models.TrackInfo

@Composable
fun TrackListItem(
    track: TrackInfo,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    // Custom composable to display a track item
    Row (
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(if (isPlaying) Color.Gray else Color.Transparent)
            .clickable { onClick() }
    ) {
        Column {
            Text(
                text = track.trackName ?: "Unknown Track",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface
            )
        }
    }
}