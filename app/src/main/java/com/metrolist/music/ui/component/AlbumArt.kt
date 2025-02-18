package com.metrolist.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.filterIsInstance

@Composable
fun AlbumArt(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAsyncImagePainter(model = artworkUrl)
    var isSquare by remember { mutableStateOf(false) }

    LaunchedEffect(painter) {
        snapshotFlow { painter.state }
            .filterIsInstance<AsyncImagePainter.State.Success>()
            .collect { state ->
                isSquare = state.painter.intrinsicSize.width == state.painter.intrinsicSize.height
            }
    }

    Box(
        modifier = modifier
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    renderEffect = BlurEffect(
                        radiusX = 75f,
                        radiusY = 75f
                    ),
                    alpha = 0.5f
                )
        )

        // Main thumbnail
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
        )
    }
}