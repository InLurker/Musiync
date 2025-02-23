package com.metrolist.music.common.models

import android.graphics.Bitmap
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.metrolist.music.datastore.TrackInfoProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream
import java.io.OutputStream

data class TrackInfo(
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val artworkUrl: String?,
    var artworkBitmap: Flow<Bitmap?>
)


object TrackInfoSerializer : Serializer<TrackInfoProto> {
    override val defaultValue: TrackInfoProto = TrackInfoProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): TrackInfoProto {
        return try {
            TrackInfoProto.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto", e)
        }
    }

    override suspend fun writeTo(t: TrackInfoProto, output: OutputStream) {
        t.writeTo(output)
    }
}