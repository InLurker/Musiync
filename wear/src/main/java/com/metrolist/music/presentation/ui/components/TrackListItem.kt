package com.metrolist.music.presentation.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import com.metrolist.music.common.models.TrackInfo

@Composable
fun TrackListItem(
    trackInfo: TrackInfo?,
    isPlaying: Boolean,
    passiveColor: Color,
    activeColor: Color,
    onClick: () -> Unit
) {
    val isPlaceholder = trackInfo == null
    var containerModifier = Modifier
        .padding(8.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(
            color = if (isPlaying) activeColor else passiveColor
        )

    if (!isPlaceholder) {
        containerModifier = containerModifier.clickable(onClick = onClick)
    }

    Row(
        modifier = containerModifier
    ) {
        if (isPlaceholder) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .height(32.dp)
                    .width(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )
        } else {
            val artworkUrl = trackInfo.artworkUrl
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl)
                    .apply {
                        memoryCacheKey(artworkUrl)
                        diskCacheKey(artworkUrl)
                    }
                    .crossfade(1000)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build(),
                contentDescription = "Album Artwork",
                modifier = Modifier
                    .padding(8.dp)
                    .height(32.dp)
                    .width(32.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        }
        Column {
            if (isPlaceholder) {
                Text(
                    text = "Loading...",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Fetching details",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            } else {
                Text(
                    text = trackInfo.trackName,
                    fontSize = 14.sp,
                    maxLines = 1,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                val albumArtistText = listOfNotNull(
                    trackInfo.artistName.takeIf { it.isNotEmpty() },
                    trackInfo.albumName.takeIf { it.isNotEmpty() }
                ).joinToString(" - ")
                albumArtistText.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = albumArtistText,
                        fontSize = 10.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
