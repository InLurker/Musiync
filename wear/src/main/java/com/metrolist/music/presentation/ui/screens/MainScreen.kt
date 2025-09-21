package com.metrolist.music.presentation.ui.screens

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.toBitmap
import com.metrolist.music.presentation.theme.MetrolistTheme
import com.metrolist.music.presentation.viewmodel.PlayerViewModel


@Composable
fun MainScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState { 4 }

    val currentTrack by viewModel.currentTrack.collectAsState()

    val reduceMotion = rememberReduceMotionPreference()

    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MetrolistTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentTrack?.artworkUrl)
                        .apply {
                            currentTrack?.artworkUrl?.let { url ->
                                memoryCacheKey(url)
                                diskCacheKey(url)
                            }
                        }
                        .crossfade(1000)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .build(),
                    onSuccess = { result ->
                        val image = result.result.image
                        viewModel.updateAccentColor(image.toBitmap())
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxHeight()
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                ) { page ->
                    when (page) {
                        0 -> PlayerScreen(viewModel)
                        1 -> QueueScreen(viewModel)
                        2 -> PlaylistScreen()
                        3 -> StatusScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberReduceMotionPreference(): Boolean {
    return remember {
        val animationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            ValueAnimator.getDurationScale() != 0f
        }
        !animationsEnabled
    }
}
