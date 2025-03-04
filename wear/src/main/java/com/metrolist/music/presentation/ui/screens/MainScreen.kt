package com.metrolist.music.presentation.ui.screens

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.HorizontalPagerScaffold
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.metrolist.music.presentation.theme.MetrolistTheme
import com.metrolist.music.presentation.viewmodel.PlayerViewModel


@Composable
fun MainScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState { 2 }

    val currentTrack by viewModel.currentTrack.collectAsState()
    val artworkBitmaps by viewModel.currentArtwork.collectAsState()

    MetrolistTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Black)
        ) {
            val imageData = artworkBitmaps ?: currentTrack?.artworkUrl
            Log.d("MainScreen", "Artwork: $imageData")
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageData)
                    .crossfade(1000)
                    .allowHardware(false)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build(),
                onSuccess = { result ->
                    val bitmapDrawable = result.result.drawable
                    if (bitmapDrawable is BitmapDrawable) {
                        viewModel.updateAccentColor(bitmapDrawable.bitmap)
                        viewModel.appendBitmapToArtworkMap(currentTrack?.artworkUrl!!, bitmapDrawable.bitmap)
                    }
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight()
            )
            HorizontalPagerScaffold(
                pagerState = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            ) { page ->
                when (page) {
                    0 -> PlayerScreen(viewModel)
                    1 -> QueueScreen(viewModel)
                }
            }
        }
    }
}
