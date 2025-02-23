package com.metrolist.music.presentation.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.material.color.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

fun loadBitmapAsFlow(asset: Asset?, dataClient: DataClient): Flow<Bitmap?> = flow {
    if (asset == null) {
        emit(null)
        return@flow
    }
    try {
        val assetFileDescriptor = withContext(Dispatchers.IO) {
            Tasks.await(dataClient.getFdForAsset(asset))
        }
        val inputStream = assetFileDescriptor.inputStream
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        emit(bitmap)
    } catch (e: Exception) {
        e.printStackTrace()
        emit(null)
    }
}


fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation =
        Palette
            .from(this)
            .maximumColorCount(16)
            .generate()
            .swatches
            .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}