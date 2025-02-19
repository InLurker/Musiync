package com.metrolist.music.presentation.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.metrolist.music.R
import com.metrolist.music.models.TrackInfo
import com.metrolist.music.presentation.theme.MetrolistTheme
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf

@Composable
fun DisplayScreen(
    trackInfo: TrackInfo?,
    sendPlaybackCommand: (String) -> Unit
) {
    val context = LocalContext.current
    val targetHeightPx = 450

    val albumArtFlow = trackInfo?.artworkBitmap
    val albumArt by albumArtFlow?.collectAsState(initial = null) ?: remember { mutableStateOf(null) }

    MetrolistTheme {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .background(MaterialTheme.colors.background)
        ) {

            Log.d("DisplayScreen", "Album art: $albumArt")

//            val painter = rememberAsyncImagePainter(model = albumArt)
//            var isSquare by remember { mutableStateOf(false) }
//
//            LaunchedEffect(painter) {
//                snapshotFlow { painter.state }
//                    .filterIsInstance<AsyncImagePainter.State.Success>()
//                    .collect { state ->
//                        isSquare = state.painter.intrinsicSize.width == state.painter.intrinsicSize.height
//                    }
//            }


            AsyncImage(
                model = albumArt,
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
                model = albumArt,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                placeholder = painterResource(id = R.drawable.nade),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
            )
            TimeText()
            trackInfo?.let { info ->
                DisplayTrackInfo(info) { command ->
                    sendPlaybackCommand(command)
                }
            } ?: Text("Waiting for track info...", color = MaterialTheme.colors.primary)
        }
    }
}

@Preview(device = "id:wearos_small_round")
@Composable
fun DefaultPreview() {
    DisplayScreen(
        TrackInfo(
            "Track Name",
            "Artist Name",
            "Album Name",
            null,
            flowOf(null)
        )
    ) {}
}