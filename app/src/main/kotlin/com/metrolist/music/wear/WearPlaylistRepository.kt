package com.metrolist.music.wear

import android.content.Context
import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.filterExplicit
import com.metrolist.music.constants.AlbumSortType
import com.metrolist.music.constants.ArtistSortType
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.datastore.LibraryEntryProto
import com.metrolist.music.datastore.PlaylistSummaryProto
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.filterExplicit
import com.metrolist.music.extensions.filterExplicitAlbums
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.shared.model.LibraryEntry
import com.metrolist.music.shared.model.LibraryEntryType
import com.metrolist.music.shared.model.PlaylistSummary
import com.metrolist.music.shared.model.toCollectionProto
import com.metrolist.music.shared.model.toModel
import com.metrolist.music.shared.model.toProto
import com.metrolist.music.shared.model.toSnapshotProto
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import com.metrolist.music.wear.enumerated.DataLayerPathEnum
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
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
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val entries = buildLibraryEntries(hideExplicit)
            val snapshot = entries.toSnapshotProto(requestId = requestId)
            dataLayerHelper.sendLibrarySnapshot(entries, snapshot)
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

    private suspend fun buildLibraryEntries(hideExplicit: Boolean): List<LibraryEntry> {
        data class EntryCandidate(val entry: LibraryEntry, val addedAt: LocalDateTime?)

        fun EntryCandidate.fallbackTimestamp(): LocalDateTime = addedAt ?: LocalDateTime.MIN

        val playlistCandidates = database
            .playlists(PlaylistSortType.CREATE_DATE, descending = true)
            .firstOrNull()
            .orEmpty()
            .map { playlist ->
                EntryCandidate(
                    entry = mapLibraryPlaylistEntry(playlist),
                    addedAt = playlist.playlist.bookmarkedAt ?: playlist.playlist.createdAt
                )
            }
            .sortedByDescending { it.fallbackTimestamp() }
            .take(MAX_PLAYLIST_ITEMS)

        val albumCandidates = database
            .albumsLiked(AlbumSortType.CREATE_DATE, descending = true)
            .firstOrNull()
            .orEmpty()
            .filterExplicitAlbums(hideExplicit)
            .map { album ->
                EntryCandidate(
                    entry = mapAlbumEntry(album),
                    addedAt = album.album.bookmarkedAt
                )
            }
            .sortedByDescending { it.fallbackTimestamp() }
            .take(MAX_ALBUM_ITEMS)

        val artistCandidates = database
            .artistsBookmarked(ArtistSortType.CREATE_DATE, true)
            .firstOrNull()
            .orEmpty()
            .map { artist ->
                EntryCandidate(
                    entry = mapArtistEntry(artist),
                    addedAt = artist.artist.bookmarkedAt
                )
            }
            .sortedByDescending { it.fallbackTimestamp() }
            .take(MAX_ARTIST_ITEMS)

        val songCandidates = database
            .likedSongs(SongSortType.CREATE_DATE, true)
            .firstOrNull()
            .orEmpty()
            .filterExplicit(hideExplicit)
            .map { song ->
                EntryCandidate(
                    entry = mapSongEntry(song),
                    addedAt = song.song.likedDate ?: song.song.inLibrary
                )
            }
            .sortedByDescending { it.fallbackTimestamp() }
            .take(MAX_SONG_ITEMS)

        return (playlistCandidates + albumCandidates + artistCandidates + songCandidates)
            .sortedByDescending { it.fallbackTimestamp() }
            .take(MAX_LIBRARY_ITEMS)
            .map { it.entry }
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

    fun handlePlayLibraryEntry(entryProto: LibraryEntryProto, playerConnection: PlayerConnection?) {
        if (playerConnection == null) {
            Timber.tag("WearPlaylists").w("PlayerConnection unavailable for library entry playback")
            return
        }
        scope.launch {
            when (entryProto.type) {
                LibraryEntryProto.Type.PLAYLIST -> {
                    val summary = PlaylistSummary(
                        id = entryProto.id,
                        browseId = entryProto.browseId.nullIfBlank(),
                        title = entryProto.title,
                        owner = null,
                        trackCount = 0,
                        artworkUrl = entryProto.artworkUrl.nullIfBlank(),
                        isLocal = entryProto.isLocal,
                        playEndpointParams = entryProto.playEndpointParams.nullIfBlank(),
                        shuffleEndpointParams = entryProto.shuffleEndpointParams.nullIfBlank(),
                        radioEndpointParams = entryProto.radioEndpointParams.nullIfBlank()
                    )
                    playPlaylistInternal(summary.toProto(), playerConnection)
                }
                LibraryEntryProto.Type.ALBUM -> playAlbumEntry(entryProto, playerConnection)
                LibraryEntryProto.Type.ARTIST -> playArtistEntry(entryProto, playerConnection)
                LibraryEntryProto.Type.SONG -> playSongEntry(entryProto, playerConnection)
                LibraryEntryProto.Type.TYPE_UNSPECIFIED, LibraryEntryProto.Type.UNRECOGNIZED -> {
                    Timber.tag("WearPlaylists").w("Unsupported library entry type=${entryProto.type}")
                }
            }
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

    private fun mapLibraryPlaylistEntry(playlist: Playlist): LibraryEntry {
        val trackCount = if (playlist.songCount > 0) playlist.songCount else playlist.playlist.remoteSongCount ?: 0
        val subtitle = if (trackCount > 0) "$trackCount tracks" else null
        val isLocal = playlist.playlist.isEditable || playlist.playlist.isLocal || playlist.songCount > 0
        return LibraryEntry(
            id = playlist.id,
            type = LibraryEntryType.PLAYLIST,
            title = playlist.playlist.name,
            subtitle = subtitle,
            artworkUrl = playlist.thumbnails.firstOrNull(),
            isLocal = isLocal,
            browseId = playlist.playlist.browseId,
            playEndpointParams = playlist.playlist.playEndpointParams,
            shuffleEndpointParams = playlist.playlist.shuffleEndpointParams,
            radioEndpointParams = playlist.playlist.radioEndpointParams
        )
    }

    private fun mapAlbumEntry(album: Album): LibraryEntry = LibraryEntry(
        id = album.id,
        type = LibraryEntryType.ALBUM,
        title = album.album.title,
        subtitle = album.artists.joinToString { it.name }.ifBlank { null },
        artworkUrl = album.album.thumbnailUrl,
        isLocal = album.album.isLocal,
        browseId = album.album.playlistId,
        playEndpointParams = null,
        shuffleEndpointParams = null,
        radioEndpointParams = null
    )

    private fun mapArtistEntry(artist: Artist): LibraryEntry = LibraryEntry(
        id = artist.id,
        type = LibraryEntryType.ARTIST,
        title = artist.artist.name,
        subtitle = artist.songCount.takeIf { it > 0 }?.let { "$it songs" },
        artworkUrl = artist.artist.thumbnailUrl,
        isLocal = artist.artist.isLocal,
        browseId = artist.artist.id,
        playEndpointParams = null,
        shuffleEndpointParams = null,
        radioEndpointParams = null
    )

    private fun mapSongEntry(song: Song): LibraryEntry = LibraryEntry(
        id = song.song.id,
        type = LibraryEntryType.SONG,
        title = song.song.title,
        subtitle = song.artists.joinToString { it.name }.ifBlank { null },
        artworkUrl = song.song.thumbnailUrl,
        isLocal = true,
        browseId = null,
        playEndpointParams = null,
        shuffleEndpointParams = null,
        radioEndpointParams = null
    )

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
        playMediaItemsQueue(summary.title, mediaItems, playerConnection)
    }

    private suspend fun playRemotePlaylist(
        playlistId: String,
        params: String?,
        playerConnection: PlayerConnection
    ) {
        val endpoint = WatchEndpoint(
            playlistId = playlistId,
            params = params
        )
        playRemoteWatchEndpoint(endpoint, playerConnection)
    }

    private suspend fun playAlbumEntry(
        entryProto: LibraryEntryProto,
        playerConnection: PlayerConnection
    ) {
        val localSongs = database.albumSongs(entryProto.id).firstOrNull().orEmpty()
        if (localSongs.isNotEmpty()) {
            val mediaItems = localSongs.map { it.toMediaItem() }
            playMediaItemsQueue(entryProto.title.nullIfBlank(), mediaItems, playerConnection)
            return
        }

        val playlistId = entryProto.browseId.nullIfBlank()
        if (playlistId != null) {
            playRemotePlaylist(playlistId, entryProto.playEndpointParams.nullIfBlank(), playerConnection)
        } else {
            Timber.tag("WearPlaylists").w("No playback source for album id=${entryProto.id}")
        }
    }

    private suspend fun playArtistEntry(
        entryProto: LibraryEntryProto,
        playerConnection: PlayerConnection
    ) {
        val localSongs = database.artistSongsByCreateDateAsc(entryProto.id).firstOrNull().orEmpty()
        if (localSongs.isNotEmpty()) {
            val mediaItems = localSongs.map { it.toMediaItem() }
            playMediaItemsQueue(entryProto.title.nullIfBlank(), mediaItems, playerConnection)
            return
        }

        Timber.tag("WearPlaylists").w("No local tracks available for artist id=${entryProto.id}")
    }

    private suspend fun playSongEntry(
        entryProto: LibraryEntryProto,
        playerConnection: PlayerConnection
    ) {
        val localSong = database.getSongById(entryProto.id)
        if (localSong != null) {
            val mediaItems = listOf(localSong.toMediaItem())
            playMediaItemsQueue(entryProto.title.nullIfBlank(), mediaItems, playerConnection)
            return
        }

        val videoId = entryProto.id.nullIfBlank() ?: entryProto.browseId.nullIfBlank()
        if (videoId != null) {
            playRemoteWatchEndpoint(WatchEndpoint(videoId = videoId), playerConnection)
        } else {
            Timber.tag("WearPlaylists").w("No playback source for song id=${entryProto.id}")
        }
    }

    private suspend fun playMediaItemsQueue(
        title: String?,
        mediaItems: List<MediaItem>,
        playerConnection: PlayerConnection
    ) {
        if (mediaItems.isEmpty()) return
        withContext(Dispatchers.Main) {
            playerConnection.playQueue(
                ListQueue(
                    title = title,
                    items = mediaItems,
                    startIndex = 0
                )
            )
        }
    }

    private suspend fun playRemoteWatchEndpoint(
        endpoint: WatchEndpoint,
        playerConnection: PlayerConnection
    ) {
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

    private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    private companion object {
        private const val MAX_PLAYLIST_ITEMS = 8
        private const val MAX_ALBUM_ITEMS = 6
        private const val MAX_ARTIST_ITEMS = 6
        private const val MAX_SONG_ITEMS = 8
        private const val MAX_SEARCH_RESULTS = 25
    }
}
