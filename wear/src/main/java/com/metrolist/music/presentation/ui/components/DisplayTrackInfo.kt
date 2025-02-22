package com.metrolist.music.presentation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.metrolist.music.models.TrackInfo
import kotlinx.coroutines.delay

@Composable
fun DisplayTrackInfo(trackInfo: TrackInfo) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                MaterialTheme.colors.background.copy(alpha = 0.3f),
            )
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        MarqueeText(text = trackInfo.trackName ?: "Unknown Track", fontSize = 16.sp)

        Spacer(modifier = Modifier.height(4.dp))

        val albumArtistText = listOfNotNull(
            trackInfo.artistName.takeIf { it?.isNotEmpty() ?: false },
            trackInfo.albumName.takeIf { it?.isNotEmpty() ?: false }
        ).joinToString(" - ")
        albumArtistText.takeIf { it.isNotEmpty() }?.let {
            MarqueeText(text = it, fontSize = 12.sp)
        }
    }
}


@Composable
fun MarqueeText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    delayMillis: Long = 1000L,
    desiredSpeed: Float = 50f // desired speed in pixels per second
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(text) {
        delay(delayMillis) // Allow layout measurement

        // Calculate the duration based on distance and desired speed
        val distance = scrollState.maxValue
        // Avoid division by zero
        val animationDurationMillis = if (distance > 0) {
            (distance / desiredSpeed * 1000).toInt()
        } else {
            30000 // Fallback duration if distance is not measured yet
        }

        while (true) {
            // Animate scroll to the end
            scrollState.animateScrollTo(
                scrollState.maxValue,
                animationSpec = tween(durationMillis = animationDurationMillis, easing = LinearEasing)
            )
            delay(delayMillis)
            // Animate scroll back to the start
            scrollState.animateScrollTo(
                0,
                animationSpec = tween(durationMillis = animationDurationMillis, easing = LinearEasing)
            )
            delay(delayMillis)
        }
    }

    Row(modifier = modifier.horizontalScroll(scrollState)) {
        Text(
            text = text,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            fontSize = fontSize
        )
    }
}