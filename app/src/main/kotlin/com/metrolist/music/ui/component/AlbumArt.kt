package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

@Composable
fun AlbumArt(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAsyncImagePainter(model = artworkUrl)
    val isSquare by remember {
        derivedStateOf {
            val state = painter.state
            if (state is AsyncImagePainter.State.Success) {
                (state.painter.intrinsicSize.width / state.painter.intrinsicSize.height) > 0.9
            } else {
                false
            }
        }
    }

    Box(
        modifier = modifier
    ) {
        if (!isSquare) {
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
        }

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