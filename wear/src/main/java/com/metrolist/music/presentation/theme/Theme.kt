package com.metrolist.music.presentation.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import androidx.wear.compose.material.MaterialTheme
import com.google.material.color.score.Score

@Composable
fun MetrolistTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}

