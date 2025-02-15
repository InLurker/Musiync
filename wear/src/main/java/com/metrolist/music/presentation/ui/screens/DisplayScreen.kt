package com.metrolist.music.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.metrolist.music.models.TrackInfo
import com.metrolist.music.presentation.theme.MetrolistTheme

@Composable
fun DisplayScreen(
    trackInfo: TrackInfo?,
    sendPlaybackCommand: (String) -> Unit
) {
    val context = LocalContext.current
    val targetHeightPx = 450

    val albumArt = trackInfo?.albumImage
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(albumArt)
            .crossfade(true)
            .size(Size(Dimension.Undefined, Dimension.Pixels(targetHeightPx))) // Set target height in pixels
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    )
    MetrolistTheme {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .background(MaterialTheme.colors.background)
                .paint(painter, alignment = Alignment.Center, contentScale = ContentScale.Crop)
        ) {
            TimeText()
            trackInfo?.let { info ->
                DisplayTrackInfo(info) { command ->
                    sendPlaybackCommand(command)
                }
            } ?: Text("Waiting for track info...", color = MaterialTheme.colors.primary)
        }
    }
}