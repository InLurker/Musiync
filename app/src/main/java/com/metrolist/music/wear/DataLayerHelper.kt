package com.metrolist.music.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Timeline
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.wear.enumerated.DataLayerPathEnum
import com.metrolist.music.wear.helper.resizeBitmap
import com.metrolist.music.wear.model.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoilApi::class)
@Singleton
class DataLayerHelper @Inject constructor(context: Context) {

    private var _playerConnection by mutableStateOf<PlayerConnection?>(null)
    var playerConnection: PlayerConnection?
        get() = _playerConnection
        set(value) {
            _playerConnection = value
            if (value != null) {
                startObservingCurrentWindowIndex()
            } else {
                stopObservingCurrentWindowIndex()
            }
        }

    private var currentWindowIndexJob: Job? = null

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val commandListener = AtomicReference<(String) -> Unit>()
    @Inject
    lateinit var database: MusicDatabase

    lateinit var musicService: MusicService

    private val coil = context.imageLoader

    fun initializeMusicService(musicService: MusicService) {
        this.musicService = musicService
    }

    fun sendCurrentState() {
        scope.launch {
            try {
                val putDataRequest = PutDataMapRequest.create(DataLayerPathEnum.CURRENT_STATE.path).apply {
                    val queue = playerConnection?.queueWindows?.value
                    dataMap.putInt("queueHash", queue.hashCode())
                    dataMap.putInt("queueSize", queue?.size ?: 0)
                    dataMap.putInt("currentIndex", playerConnection?.currentWindowIndex?.value ?: 0)
                    dataMap.putBoolean("isPlaying", playerConnection?.isPlaying?.value ?: false)
                }.asPutDataRequest()
                val stringPayload = playerConnection?.let {
                    "currentIndex=${it.currentWindowIndex.value},queueSize=${it.queueWindows.value.size},isPlaying=${it.isPlaying.value},queueHash=${it.queueWindows.hashCode()}"
                } ?: "null playerConnection"
                // Send data through Data Layer
                dataClient.putDataItem(putDataRequest).addOnSuccessListener {
                    Timber.tag("DataLayerHelper").d("Current state sent via Data Layer: ${stringPayload}")
                }.addOnFailureListener { e ->
                    Timber.tag("DataLayerHelper").e(e, "Failed to send current state via Data Layer")
                }
            } catch (e: Exception) {
                Timber.tag("DataLayerHelper").e(e, "Failed to send current state")
            }
        }
    }

    fun handleQueueRangeRequest(start: Int, end: Int, callback: (Map<Int,TrackInfo>) -> Unit) {
        callback(getPaginatedQueue(start, end))
    }

    private fun getPaginatedQueue(start: Int, end: Int): Map<Int, TrackInfo> {
        playerConnection?.let { connection ->
            val queue = connection.queueWindows.value
            val paginatedQueue = getPaginatedMap(queue, start, end)
            return paginatedQueue
        } ?: run {
            return emptyMap()
        }
    }

    private fun getPaginatedMap(queue: List<Timeline. Window>, start: Int, end: Int): Map<Int, TrackInfo> {
        return queue.subList(start, end).mapIndexed { index, window ->
            val originalIndex = start + index
            originalIndex to TrackInfo(
                window.mediaItem.mediaMetadata.title.toString(),
                window.mediaItem.mediaMetadata.artist.toString(),
                window.mediaItem.mediaMetadata.albumTitle.toString(),
                window.mediaItem.mediaMetadata.artworkUri.toString(),
                window.mediaItem.mediaMetadata.artworkData?.let { generateResizedAssetFromByteArray(400, it) }
            )
        }.toMap()
    }

    fun sendDataMap(putDataMapRequest: PutDataMapRequest) {
        dataClient.putDataItem(putDataMapRequest.asPutDataRequest()).addOnSuccessListener {
            Timber.tag("DataLayerHelper").d("DataMap sent via Data Layer: ${putDataMapRequest.dataMap}")
        }.addOnFailureListener { e ->
            Timber.tag("DataLayerHelper").e(e, "Failed to send DataMap via Data Layer")
        }
    }

    @OptIn(FlowPreview::class)
    private fun startObservingCurrentWindowIndex() {
        currentWindowIndexJob?.cancel()
        currentWindowIndexJob = scope.launch {
            playerConnection?.let { connection ->
                combine(
                    connection.isPlaying,
                    connection.mediaMetadata
                ) { isPlaying, mediaMetadata ->
                    isPlaying to mediaMetadata
                }
                .distinctUntilChanged() // Only emit if the values have changed
                .debounce(300) // Debounce to limit the rate of emissions
                .collect {
                    sendCurrentState()
                }
            }
        }
    }

    private fun stopObservingCurrentWindowIndex() {
        currentWindowIndexJob?.cancel()
        currentWindowIndexJob = null
    }


//    fun sendMediaInfo(
//        title: String,
//        artist: String,
//        album: String,
//        artworkUrl: String,
//        isPlaying: Boolean
//    ) {
//        scope.launch {
//            try {
//                val putDataRequest = PutDataMapRequest.create(DataLayerPathEnum.SONG_INFO.path).apply {
//                    dataMap.putString("trackName", title)
//                    dataMap.putString("artistName", artist)
//                    dataMap.putString("albumName", album)
//                    dataMap.putString("artworkUrl", artworkUrl)
//                    dataMap.putAsset("artworkAsset", fetchBitmapByteFromCoil(artworkUrl, 450))
//                    dataMap.putBoolean("isPlaying", isPlaying)
//                }.asPutDataRequest()
//
//                // Send data through Data Layer
//                dataClient.putDataItem(putDataRequest).addOnSuccessListener {
//                    Timber.tag("DataLayerHelper").d("Song info sent via Data Layer: ${putDataRequest.data}")
//                }.addOnFailureListener { e ->
//                    Timber.tag("DataLayerHelper").e(e, "Failed to send song info via Data Layer")
//                }
//            } catch (e: Exception) {
//                Timber.tag("DataLayerHelper").e(e, "Failed to send song info")
//            }
//        }
//    }

//    private fun sendLikedAlbums() {
//        scope.launch {
//            try {
//                // Collect the albumsLiked flow
//                database.albumsLiked(AlbumSortType.CREATE_DATE, true).collect { albums ->
//
//                    if (albums.isEmpty()) {
//                        Timber.d("No liked albums found to send.")
//                        return@collect
//                    }
//
//                    // Convert the list of albums into a DataMapArrayList
//                    val albumDataMapList = ArrayList<DataMap>()
//                    albums.forEach { album ->
//                        val albumDataMap = DataMap().apply {
//                            putString("id", album.album.id)
//                            putString("title", album.album.title)
//                            putString("artist", album.artists.map { it.name }.joinToString(", "))
//                            putString("artworkUrl", album.album.thumbnailUrl ?: "")
//                            putAsset("artworkBitmap", fetchBitmapByteFromCoil(album.album.thumbnailUrl, 100))
//                            putInt("songCount", album.album.songCount)
//                        }
//                        albumDataMapList.add(albumDataMap)
//                    }
//
//                    // Prepare and send the DataMapArrayList
//                    val putDataRequest = PutDataMapRequest.create(DataLayerPathEnum.ALBUM_INFO.path).apply {
//                        dataMap.putDataMapArrayList("albums", albumDataMapList)
//                    }.asPutDataRequest()
//
//                    // Send the data through the Data Layer
//                    dataClient.putDataItem(putDataRequest).addOnSuccessListener {
//                        Timber.d("Successfully sent liked album details.")
//                    }.addOnFailureListener { e ->
//                        Timber.e(e, "Failed to send liked album details")
//                    }
//                }
//            } catch (e: Exception) {
//                Timber.e(e, "Error while sending liked albums")
//            }
//        }
//    }
//
//    private fun sendBookmarkedArtists() {
//        scope.launch {
//            val artists = database.artistsBookmarked(ArtistSortType.CREATE_DATE, true)
//                .map { list -> list.map { it.artist.name } }
//                .firstOrNull() ?: emptyList()
//
//            val putDataRequest = PutDataMapRequest.create(DataLayerPathEnum.ALBUM_INFO.path).apply {
//                dataMap.putStringArrayList("artists", ArrayList(artists))
//            }.asPutDataRequest()
//
//            dataClient.putDataItem(putDataRequest).addOnSuccessListener { _ ->
//                Timber.tag("DataLayerHelper").d("Bookmarked artists sent via Data Layer")
//            }.addOnFailureListener { e ->
//                Timber.tag("DataLayerHelper").e(e, "Failed to send bookmarked artists via Data Layer")
//            }
//        }
//    }
//
//
//    fun sendAlbumDetails(albumId: String) {
//        scope.launch {
//            try {
//                // Fetch album with songs from the database
//                val albumWithSongs = database.albumWithSongs(albumId).firstOrNull()
//
//                if (albumWithSongs != null) {
//                    val album = albumWithSongs.album
//                    val songs = albumWithSongs.songs
//
//                    // Prepare data to be sent
//                    val putDataRequest = PutDataMapRequest.create(DataLayerPathEnum.ALBUM_INFO.path).apply {
//                        dataMap.putString("albumTitle", album.title)
//                        dataMap.putString("albumYear", album.year?.toString() ?: "")
//                        dataMap.putString("albumThumbnail", album.thumbnailUrl ?: "")
//                        dataMap.putString("albumId", album.id)
//                        dataMap.putStringArrayList("songTitles", ArrayList(songs.map { it.song.title }))
//                        dataMap.putStringArrayList("songIds", ArrayList(songs.map { it.song.id }))
//                    }.asPutDataRequest()
//
//                    // Send data through Data Layer
//                    dataClient.putDataItem(putDataRequest).addOnSuccessListener {
//                        Timber.tag("DataLayerHelper").d("Album details sent via Data Layer for albumId: $albumId")
//                    }.addOnFailureListener { e ->
//                        Timber.tag("DataLayerHelper").e(e, "Failed to send album details via Data Layer")
//                    }
//                } else {
//                    Timber.tag("DataLayerHelper").e("Album with ID $albumId not found in the database.")
//                }
//            } catch (e: Exception) {
//                Timber.tag("DataLayerHelper").e(e, "Failed to send album details")
//            }
//        }
//    }

    fun generateResizedAssetFromByteArray(size: Int, byteArray: ByteArray): Asset? {
        if (byteArray.isEmpty()) return null
        val bitmap = resizeBitmap(
            size,
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        )
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Asset.createFromBytes(stream.toByteArray())
    }

    fun fetchBitmapByteFromCoil(url: String?, size: Int): Asset {
        val bitmap = url?.let {
            coil.diskCache?.openSnapshot(it)?.use {
                val imageFile = it.data.toFile().readBytes()
                if (imageFile.isEmpty()) {
                    null
                } else {
                    resizeBitmap(
                        size,
                        BitmapFactory.decodeByteArray(imageFile, 0, imageFile.size)
                    )
                }
            }
        }

        val stream = ByteArrayOutputStream()
        bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return Asset.createFromBytes(stream.toByteArray())
    }
}

