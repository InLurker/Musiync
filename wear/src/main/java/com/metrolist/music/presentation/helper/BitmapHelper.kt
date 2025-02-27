package com.metrolist.music.presentation.helper

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.material.color.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("VisibleForTests")
suspend fun Asset.toBitmap(dataClient: DataClient): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val assetResponse = Tasks.await(dataClient.getFdForAsset(this@toBitmap))
            assetResponse.inputStream.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                inputStream.use { BitmapFactory.decodeStream(it, null, options) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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