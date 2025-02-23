package com.metrolist.music.wear.helper

import android.graphics.Bitmap

fun resizeBitmap(maxDimension: Int, bitmap: Bitmap): Bitmap {
    if (maxDimension <= 0) return bitmap

    var width = bitmap.width
    var height = bitmap.height

    // Avoid unnecessary resizing
    if (width <= maxDimension && height <= maxDimension) {
        return bitmap
    }

    val bitmapRatio = width.toFloat() / height.toFloat()
    if (bitmapRatio > 1) {
        // Landscape
        width = maxDimension
        height = (width / bitmapRatio).toInt()
    } else {
        // Portrait or square
        height = maxDimension
        width = (height * bitmapRatio).toInt()
    }

    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}