package com.metrolist.music.wear.helper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.max


fun transformBitmap(source: Bitmap, targetSize: Int): Bitmap {
    val matrix = Matrix().apply {
        // Calculate scale
        val scale = max(
            targetSize.toFloat() / source.width,
            targetSize.toFloat() / source.height
        )
        setScale(scale, scale)

        // Calculate translation for center crop
        postTranslate(
            (targetSize - source.width * scale) / 2,
            (targetSize - source.height * scale) / 2
        )
    }

    return Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.RGB_565).apply {
        Canvas(this).apply {
            drawBitmap(source, matrix, Paint().apply {
                isFilterBitmap = true
                isAntiAlias = true
            })
        }
    }
}

fun calculateSampleSize(originalWidth: Int, originalHeight: Int, targetSize: Int): Int {
    var sampleSize = 1
    val maxDimension = max(originalWidth, originalHeight)

    while (maxDimension / sampleSize > targetSize * 2) {
        sampleSize *= 2
    }

    return sampleSize
}