package com.metrolist.music.shared.model

import com.metrolist.music.datastore.LibraryEntryProto
import com.metrolist.music.datastore.LibrarySnapshotProto
import com.metrolist.music.datastore.PlaylistCollectionProto
import com.metrolist.music.datastore.PlaylistSummaryProto

/**
 * Shared summary model for playlists exchanged between phone and watch.
 */
data class PlaylistSummary(
    val id: String,
    val browseId: String?,
    val title: String,
    val owner: String?,
    val trackCount: Int,
    val artworkUrl: String?,
    val isLocal: Boolean,
    val playEndpointParams: String?,
    val shuffleEndpointParams: String?,
    val radioEndpointParams: String?
)

fun PlaylistSummary.toProto(): PlaylistSummaryProto = PlaylistSummaryProto.newBuilder()
    .setId(id)
    .setBrowseId(browseId.orEmpty())
    .setTitle(title)
    .setOwner(owner.orEmpty())
    .setTrackCount(trackCount)
    .setArtworkUrl(artworkUrl.orEmpty())
    .setIsLocal(isLocal)
    .setPlayEndpointParams(playEndpointParams.orEmpty())
    .setShuffleEndpointParams(shuffleEndpointParams.orEmpty())
    .setRadioEndpointParams(radioEndpointParams.orEmpty())
    .build()

fun PlaylistSummaryProto.toModel(): PlaylistSummary = PlaylistSummary(
    id = id,
    browseId = browseId.ifBlank { null },
    title = title,
    owner = owner.ifBlank { null },
    trackCount = trackCount,
    artworkUrl = artworkUrl.ifBlank { null },
    isLocal = isLocal,
    playEndpointParams = playEndpointParams.ifBlank { null },
    shuffleEndpointParams = shuffleEndpointParams.ifBlank { null },
    radioEndpointParams = radioEndpointParams.ifBlank { null }
)

fun List<PlaylistSummary>.toCollectionProto(
    requestId: Long,
    source: String,
    generatedAt: Long = System.currentTimeMillis()
): PlaylistCollectionProto = PlaylistCollectionProto.newBuilder()
    .setRequestId(requestId)
    .setSource(source)
    .setGeneratedAt(generatedAt)
    .addAllItems(map { it.toProto() })
    .build()

fun PlaylistCollectionProto.toModelList(): List<PlaylistSummary> = itemsList.map { it.toModel() }

enum class LibraryEntryType {
    PLAYLIST,
    ALBUM,
    ARTIST,
    SONG
}

data class LibraryEntry(
    val id: String,
    val type: LibraryEntryType,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val isLocal: Boolean,
    val browseId: String?,
    val playEndpointParams: String?,
    val shuffleEndpointParams: String?,
    val radioEndpointParams: String?
)

fun LibraryEntry.toProto(): LibraryEntryProto = LibraryEntryProto.newBuilder()
    .setId(id)
    .setType(
        when (type) {
            LibraryEntryType.PLAYLIST -> LibraryEntryProto.Type.PLAYLIST
            LibraryEntryType.ALBUM -> LibraryEntryProto.Type.ALBUM
            LibraryEntryType.ARTIST -> LibraryEntryProto.Type.ARTIST
            LibraryEntryType.SONG -> LibraryEntryProto.Type.SONG
        }
    )
    .setTitle(title)
    .setSubtitle(subtitle.orEmpty())
    .setArtworkUrl(artworkUrl.orEmpty())
    .setIsLocal(isLocal)
    .setBrowseId(browseId.orEmpty())
    .setPlayEndpointParams(playEndpointParams.orEmpty())
    .setShuffleEndpointParams(shuffleEndpointParams.orEmpty())
    .setRadioEndpointParams(radioEndpointParams.orEmpty())
    .build()

fun LibraryEntryProto.toModel(): LibraryEntry = LibraryEntry(
    id = id,
    type = when (type) {
        LibraryEntryProto.Type.ALBUM -> LibraryEntryType.ALBUM
        LibraryEntryProto.Type.ARTIST -> LibraryEntryType.ARTIST
        LibraryEntryProto.Type.SONG -> LibraryEntryType.SONG
        LibraryEntryProto.Type.PLAYLIST -> LibraryEntryType.PLAYLIST
        LibraryEntryProto.Type.TYPE_UNSPECIFIED, LibraryEntryProto.Type.UNRECOGNIZED -> LibraryEntryType.PLAYLIST
    },
    title = title,
    subtitle = subtitle.ifBlank { null },
    artworkUrl = artworkUrl.ifBlank { null },
    isLocal = isLocal,
    browseId = browseId.ifBlank { null },
    playEndpointParams = playEndpointParams.ifBlank { null },
    shuffleEndpointParams = shuffleEndpointParams.ifBlank { null },
    radioEndpointParams = radioEndpointParams.ifBlank { null }
)

fun List<LibraryEntry>.toSnapshotProto(
    requestId: Long,
    generatedAt: Long = System.currentTimeMillis()
): LibrarySnapshotProto = LibrarySnapshotProto.newBuilder()
    .setRequestId(requestId)
    .setGeneratedAt(generatedAt)
    .addAllEntries(map { it.toProto() })
    .build()

fun LibrarySnapshotProto.toModelList(): List<LibraryEntry> = entriesList.map { it.toModel() }
