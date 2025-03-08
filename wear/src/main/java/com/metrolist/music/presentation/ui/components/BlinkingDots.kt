package com.metrolist.music.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun BlinkingDots(modifier: Modifier = Modifier, color1: Color = Color.White, color2: Color = Color.Gray) {
    val dotSize = 8.dp
    val animationDuration = 600

    // Animation states for each dot
    var dot1Color by remember { mutableStateOf(color1) }
    var dot2Color by remember { mutableStateOf(color1) }
    var dot3Color by remember { mutableStateOf(color1) }

    // Create the animation loop
    LaunchedEffect(Unit) {
        while (true) {
            // Change all dots to the second color
            dot1Color = color2
            dot2Color = color2
            dot3Color = color2

            // Delay between animations
            delay(animationDuration.toLong())

            // Change dots back to the first color with staggered delays
            dot1Color = color1
            delay(100)
            dot2Color = color1
            delay(100)
            dot3Color = color1
            delay(300)
        }
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(dot1Color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(dot2Color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(dot3Color, CircleShape)
        )
    }
}