package com.metrolist.music.presentation.helper

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
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


fun Bitmap.extractThemeColor(): Color? {
    return Palette
        .from(this)
        .maximumColorCount(16)
        .generate()
        .dominantSwatch
        ?.rgb
        ?.let { Color(it) }
}