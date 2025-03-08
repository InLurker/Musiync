package com.metrolist.music.presentation.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.metrolist.music.common.models.TrackInfo

@Composable
fun TrackListItem(
    trackInfo: TrackInfo,
    isPlaying: Boolean,
    passiveColor: Color,
    activeColor: Color,
    artworkBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .fillMaxWidth()
            .background(color = if (isPlaying) activeColor else passiveColor)
            .padding(vertical = 8.dp)
            .padding(horizontal = 8.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artworkBitmap ?: trackInfo.artworkUrl)
                .crossfade(1000)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = "Album Artwork",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(5.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(
            modifier = Modifier.width(8.dp)
        )
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.padding(2.dp)
        ) {
            Text(
                text = trackInfo.trackName,
                fontSize = 10.sp,
                maxLines = 1,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis
            )
            val albumArtistText = buildString {
                trackInfo.artistName?.takeIf { it.isNotEmpty() }?.let {
                    append(it)
                }
                trackInfo.albumName?.takeIf { it.isNotEmpty() }?.let {
                    if (isNotEmpty()) append(" - ")
                    append(it)
                }
            }
            Spacer(
                modifier = Modifier.height(2.dp)
            )
            albumArtistText.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = albumArtistText,
                    fontSize = 9.sp,
                    maxLines = 1,
                    color = Color.White,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}