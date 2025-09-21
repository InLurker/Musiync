package com.metrolist.music.presentation.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import com.metrolist.music.shared.model.LibraryEntry
import com.metrolist.music.shared.model.LibraryEntryType

@Composable
fun LibraryEntryListItem(
    entry: LibraryEntry,
    backgroundColor: Color,
    artwork: Bitmap?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .then(clickableModifier)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        val artworkShape = when (entry.type) {
            LibraryEntryType.ARTIST -> CircleShape
            LibraryEntryType.ALBUM, LibraryEntryType.SONG -> RoundedCornerShape(6.dp)
            LibraryEntryType.PLAYLIST -> RoundedCornerShape(12.dp)
        }
        val artworkSize = when (entry.type) {
            LibraryEntryType.ARTIST -> 44.dp
            LibraryEntryType.ALBUM, LibraryEntryType.SONG -> 42.dp
            LibraryEntryType.PLAYLIST -> 40.dp
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artwork ?: entry.artworkUrl)
                .crossfade(true)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(artworkSize)
                .clip(artworkShape)
        )

        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = entry.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White
            )
            entry.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
