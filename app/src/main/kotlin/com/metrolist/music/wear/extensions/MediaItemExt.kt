@file:OptIn(ExperimentalCoilApi::class)

package com.metrolist.music.wear.extensions

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.common.MediaMetadata.PICTURE_TYPE_MEDIA
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.App.Companion.instance
import com.metrolist.music.db.entities.Song
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

fun Song.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(song.id)
        .setUri(song.id)
        .setCustomCacheKey(song.id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(song.title)
                .setSubtitle(song.artistName ?: artists.joinToString { it.name })
                .setArtist(song.artistName ?: artists.joinToString { it.name })
                .setArtworkUri(song.thumbnailUrl?.toUri())
                .apply {
                    song.thumbnailUrl?.let { url ->
                        instance.applicationContext.imageLoader.diskCache?.openSnapshot(url)?.use {
                            it.data.toFile().readBytes()
                        }
                    }?.let { bytes ->
                        setArtworkData(bytes, PICTURE_TYPE_MEDIA)
                    }
                }
                .setAlbumTitle(song.albumName)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .build(),
        ).build()

fun SongItem.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCustomCacheKey(id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(thumbnail.toUri())
                .apply {
                    instance.applicationContext.imageLoader.diskCache?.openSnapshot(thumbnail)?.use {
                        it.data.toFile().readBytes()
                    }?.let { bytes ->
                        setArtworkData(bytes, PICTURE_TYPE_MEDIA)
                    }
                }
                .setAlbumTitle(album?.name)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .build(),
        ).build()

fun MediaMetadata.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCustomCacheKey(id)
        .setTag(this)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(this.artistName ?: artists.joinToString { it.name })
                .setArtist(this.artistName ?: artists.joinToString { it.name })
                .setArtworkUri(thumbnailUrl?.toUri())
                .apply {
                    thumbnailUrl?.let { url ->
                        instance.applicationContext.imageLoader.diskCache?.openSnapshot(url)?.use {
                            it.data.toFile().readBytes()
                        }
                    }?.let { bytes ->
                        setArtworkData(bytes, PICTURE_TYPE_MEDIA)
                    }
                }
                .setAlbumTitle(album?.title)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .build(),
        ).build()
