package com.metrolist.music.presentation.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.google.android.gms.tasks.Tasks
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Asset.cacheInCoil(context: Context, dataClient: DataClient, key: String) {
    withContext(Dispatchers.IO) {
        try {
            val assetResponse = Tasks.await(dataClient.getFdForAsset(this@cacheInCoil))
            assetResponse.inputStream.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap =  BitmapFactory.decodeStream(inputStream, null, options) ?: return@withContext
                Log.d("WearCache", "Decoded asset for key(len)=${key.length} size=${bitmap.width}x${bitmap.height}")
                val imageLoader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(bitmap) // Use the bitmap as data
                    .memoryCacheKey(key) // Unique cache key based on artwork URL
                    .diskCacheKey(key) // Unique key for disk caching
                    .diskCachePolicy(CachePolicy.ENABLED) // Enable disk caching
                    .build()

                imageLoader.enqueue(request) // Store in Coil cache
                Log.d("WearCache", "Enqueued cache write for key(len)=${key.length}")
            }
        } catch (e: Exception) {
            Log.e("WearCache", "Failed caching asset for key", e)
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
    val best = colorsToPopulation.maxByOrNull { it.value }?.key ?: 0xFF000000.toInt()
    return Color(best)
}
