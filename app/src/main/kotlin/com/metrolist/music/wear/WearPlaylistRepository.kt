package com.metrolist.music.wear

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.datastore.PlaylistSummaryProto
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import com.metrolist.music.wear.enumerated.DataLayerPathEnum
import com.metrolist.music.shared.model.PlaylistSummary
import com.metrolist.music.shared.model.toCollectionProto
import com.metrolist.music.shared.model.toModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_LIBRARY_ITEMS = 30
private const val MAX_SEARCH_RESULTS = 25

@Singleton
class WearPlaylistRepository @Inject constructor(
    private val database: MusicDatabase,
    private val dataLayerHelper: DataLayerHelper,
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handleLibraryRequest(requestId: Long) {
        scope.launch {
            val libraryPlaylists = database
                .playlists(com.metrolist.music.constants.PlaylistSortType.CREATE_DATE, descending = true)
                .firstOrNull()
                .orEmpty()
                .map(::mapLibraryPlaylist)
                .take(MAX_LIBRARY_ITEMS)
            sendCollection(libraryPlaylists, DataLayerPathEnum.PLAYLIST_LIBRARY_RESPONSE, requestId, source = "library")
        }
    }

    fun handleSearchRequest(query: String, requestId: Long) {
        scope.launch {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                sendCollection(emptyList(), DataLayerPathEnum.PLAYLIST_SEARCH_RESPONSE, requestId, source = "search")
                return@launch
            }
            val results = fetchSearchResults(trimmed)
            sendCollection(results, DataLayerPathEnum.PLAYLIST_SEARCH_RESPONSE, requestId, source = "search")
        }
    }

    fun handlePlayPlaylist(summaryProto: PlaylistSummaryProto, playerConnection: PlayerConnection?) {
        if (playerConnection == null) {
            Timber.tag("WearPlaylists").w("PlayerConnection unavailable for playlist playback")
            return
        }
        scope.launch {
            playPlaylistInternal(summaryProto, playerConnection)
        }
    }

    private suspend fun sendCollection(
        playlists: List<PlaylistSummary>,
        path: DataLayerPathEnum,
        requestId: Long,
        source: String
    ) {
        val payload = playlists.toCollectionProto(requestId = requestId, source = source)
        dataLayerHelper.sendPlaylistCollection(playlists, path, payload)
    }

    private suspend fun fetchSearchResults(query: String): List<PlaylistSummary> {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val results = LinkedHashMap<String, PlaylistSummary>()
        listOf(FILTER_FEATURED_PLAYLIST, FILTER_COMMUNITY_PLAYLIST).forEach { filter ->
            runCatching {
                YouTube.search(query, filter).getOrThrow()
            }.onSuccess { searchResult ->
                searchResult.items
                    .filterIsInstance<PlaylistItem>()
                    .filterExplicit(hideExplicit)
                    .map(::mapRemotePlaylist)
                    .forEach { summary ->
                        if (!results.containsKey(summary.id)) {
                            results[summary.id] = summary
                        }
                    }
            }.onFailure {
                Timber.tag("WearPlaylists").w(it, "Failed to fetch search results for filter=$filter")
                reportException(it)
            }
            if (results.size >= MAX_SEARCH_RESULTS) return results.values.take(MAX_SEARCH_RESULTS)
        }
        return results.values.take(MAX_SEARCH_RESULTS)
    }

    private fun mapLibraryPlaylist(playlist: Playlist): PlaylistSummary {
        val thumbnails = playlist.thumbnails
        val title = playlist.playlist.name
        val trackCount = if (playlist.songCount > 0) playlist.songCount else playlist.playlist.remoteSongCount ?: 0
        return PlaylistSummary(
            id = playlist.id,
            browseId = playlist.playlist.browseId,
            title = title,
            owner = null,
            trackCount = trackCount,
            artworkUrl = thumbnails.firstOrNull(),
            isLocal = playlist.playlist.isEditable || playlist.playlist.isLocal || playlist.songCount > 0,
            playEndpointParams = playlist.playlist.playEndpointParams,
            shuffleEndpointParams = playlist.playlist.shuffleEndpointParams,
            radioEndpointParams = playlist.playlist.radioEndpointParams
        )
    }

    private fun mapRemotePlaylist(item: PlaylistItem): PlaylistSummary {
        return PlaylistSummary(
            id = item.id,
            browseId = item.id,
            title = item.title,
            owner = item.author?.name,
            trackCount = item.songCountText?.extractFirstNumber() ?: 0,
            artworkUrl = item.thumbnail,
            isLocal = false,
            playEndpointParams = item.playEndpoint?.params,
            shuffleEndpointParams = item.shuffleEndpoint?.params,
            radioEndpointParams = item.radioEndpoint?.params
        )
    }

    private suspend fun playPlaylistInternal(
        summaryProto: PlaylistSummaryProto,
        playerConnection: PlayerConnection
    ) {
        val summary = summaryProto.toModel()
        val playlistId = summary.id
        val browseId = summary.browseId

        // Try local queue first when available
        val localSongs = database.playlistSongs(playlistId).firstOrNull().orEmpty()
        if (localSongs.isNotEmpty()) {
            playLocalPlaylist(summary, localSongs, playerConnection)
            return
        }

        // If local lookup failed and we have a browseId, fall back to YouTube queue
        val targetPlaylistId = browseId ?: summary.id
        playRemotePlaylist(targetPlaylistId, summary.playEndpointParams, playerConnection)
    }

    private suspend fun playLocalPlaylist(
        summary: PlaylistSummary,
        songs: List<PlaylistSong>,
        playerConnection: PlayerConnection
    ) {
        if (songs.isEmpty()) return
        val mediaItems = songs.map { it.song.toMediaItem() }
        withContext(Dispatchers.Main) {
            playerConnection.playQueue(
                ListQueue(
                    title = summary.title,
                    items = mediaItems,
                    startIndex = 0
                )
            )
        }
    }

    private suspend fun playRemotePlaylist(
        playlistId: String,
        params: String?,
        playerConnection: PlayerConnection
    ) {
        val endpoint = com.metrolist.innertube.models.WatchEndpoint(
            playlistId = playlistId,
            params = params
        )
        withContext(Dispatchers.Main) {
            playerConnection.playQueue(YouTubeQueue(endpoint))
        }
    }

    private fun String.extractFirstNumber(): Int {
        val digits = buildString {
            for (char in this@extractFirstNumber) {
                if (char.isDigit()) {
                    append(char)
                } else if (isNotEmpty()) {
                    break
                }
            }
        }
        return digits.toIntOrNull() ?: 0
    }
}
