package com.metrolist.music.presentation.helper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.material.color.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@SuppressLint("VisibleForTests")
fun Asset.toBitmapFlow(dataClient: DataClient): Flow<Bitmap?> = flow {
    try {
        val assetFileDescriptor = Tasks.await(dataClient.getFdForAsset(this@toBitmapFlow))
        val inputStream = assetFileDescriptor.inputStream
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        emit(bitmap)
    } catch (e: Exception) {
        e.printStackTrace()
        emit(null)
    }
}.flowOn(Dispatchers.IO)


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