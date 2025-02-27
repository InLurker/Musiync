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
import kotlinx.coroutines.withContext

@SuppressLint("VisibleForTests")
suspend fun Asset.cacheInCoil(context: Context, dataClient: DataClient, key: String) {
    withContext(Dispatchers.IO) {
        try {
            val assetResponse = Tasks.await(dataClient.getFdForAsset(this@cacheInCoil))
            assetResponse.inputStream.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap =  BitmapFactory.decodeStream(inputStream, null, options) ?: return@withContext
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(bitmap) // Use the bitmap as data
                    .memoryCacheKey(key) // Unique cache key based on artwork URL
                    .diskCacheKey(key) // Unique key for disk caching
                    .diskCachePolicy(CachePolicy.ENABLED) // Enable disk caching
                    .build()

                imageLoader.enqueue(request) // Store in Coil cache
            }
        } catch (e: Exception) {
            e.printStackTrace()
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