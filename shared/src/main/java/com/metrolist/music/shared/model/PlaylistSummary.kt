package com.metrolist.music.shared.model

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
