package com.metrolist.music.presentation.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.metrolist.music.common.models.TrackInfo

@Composable
fun TrackListItem(
    trackInfo: TrackInfo,
    isPlaying: Boolean,
    passiveColor: Color,
    activeColor: Color
) {
    Row(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .background(
                color = if (isPlaying) activeColor else passiveColor
            )
            .clip(RoundedCornerShape(16.dp))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(trackInfo.artworkUrl)
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
        Column {
            Text(
                text = trackInfo.trackName,
                fontSize = 14.sp,
                maxLines = 1,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            val albumArtistText = listOfNotNull(
                trackInfo.artistName.takeIf { it.isNotEmpty()},
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