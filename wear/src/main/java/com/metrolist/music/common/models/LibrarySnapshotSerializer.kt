package com.metrolist.music.common.models

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import com.metrolist.music.datastore.LibrarySnapshotProto
import java.io.InputStream
import java.io.OutputStream

object LibrarySnapshotSerializer : Serializer<LibrarySnapshotProto> {
    override val defaultValue: LibrarySnapshotProto = LibrarySnapshotProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): LibrarySnapshotProto {
        return try {
            LibrarySnapshotProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read library snapshot", exception)
        }
    }

    override suspend fun writeTo(t: LibrarySnapshotProto, output: OutputStream) {
        t.writeTo(output)
    }
}

