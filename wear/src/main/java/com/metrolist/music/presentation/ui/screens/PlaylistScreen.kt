package com.metrolist.music.presentation.ui.screens

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.metrolist.music.presentation.ui.components.LibraryEntryListItem
import com.metrolist.music.presentation.ui.components.PlaylistListItem
import com.metrolist.music.presentation.viewmodel.PlaylistViewModel

private const val REMOTE_INPUT_KEY = "playlist_search"

@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val libraryState by viewModel.libraryState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val artworkCache by viewModel.artworkCache.collectAsState()

    val listState = rememberScalingLazyListState()

    val remoteInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data?.let { intent ->
            val input = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REMOTE_INPUT_KEY)
            if (!input.isNullOrBlank()) {
                viewModel.updateSearchQuery(input.toString())
            }
        }
    }

    val launchSearch: () -> Unit = remember {
        {
            val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
                .setLabel("Search playlists")
                .build()

            RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
            remoteInputLauncher.launch(intent)
        }
    }

    ScalingLazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        item {
            SearchControls(
                query = searchState.query,
                onSearchClicked = launchSearch,
                onClearQuery = viewModel::clearSearch
            )
        }

        item { SectionHeader(text = "Library") }

        val hasLibraryContent = libraryState.playlists.isNotEmpty() ||
            libraryState.albums.isNotEmpty() ||
            libraryState.artists.isNotEmpty() ||
            libraryState.songs.isNotEmpty()

        when {
            libraryState.isLoading && !hasLibraryContent -> {
                item { LoadingIndicator() }
            }
            !hasLibraryContent -> {
                item { EmptyMessage(text = "Library is empty") }
            }
            else -> {
                if (libraryState.playlists.isNotEmpty()) {
                    item { SubSectionHeader(text = "Playlists") }
                    items(libraryState.playlists, key = { "playlist-${it.id}" }) { entry ->
                        val artwork = entry.artworkUrl?.let { artworkCache[it] }
                        LibraryEntryListItem(
                            entry = entry,
                            backgroundColor = Color.White.copy(alpha = 0.08f),
                            artwork = artwork,
                            onClick = { viewModel.playLibraryEntry(entry) }
                        )
                    }
                }

                if (libraryState.albums.isNotEmpty()) {
                    item { SubSectionHeader(text = "Albums") }
                    items(libraryState.albums, key = { "album-${it.id}" }) { entry ->
                        val artwork = entry.artworkUrl?.let { artworkCache[it] }
                        LibraryEntryListItem(
                            entry = entry,
                            backgroundColor = Color.White.copy(alpha = 0.08f),
                            artwork = artwork,
                            onClick = { viewModel.playLibraryEntry(entry) }
                        )
                    }
                }

                if (libraryState.artists.isNotEmpty()) {
                    item { SubSectionHeader(text = "Artists") }
                    items(libraryState.artists, key = { "artist-${it.id}" }) { entry ->
                        val artwork = entry.artworkUrl?.let { artworkCache[it] }
                        LibraryEntryListItem(
                            entry = entry,
                            backgroundColor = Color.White.copy(alpha = 0.08f),
                            artwork = artwork,
                            onClick = { viewModel.playLibraryEntry(entry) }
                        )
                    }
                }

                if (libraryState.songs.isNotEmpty()) {
                    item { SubSectionHeader(text = "Songs") }
                    items(libraryState.songs, key = { "song-${it.id}" }) { entry ->
                        val artwork = entry.artworkUrl?.let { artworkCache[it] }
                        LibraryEntryListItem(
                            entry = entry,
                            backgroundColor = Color.White.copy(alpha = 0.08f),
                            artwork = artwork,
                            onClick = { viewModel.playLibraryEntry(entry) }
                        )
                    }
                }
            }
        }

        if (searchState.query.isNotBlank()) {
            item { SectionHeader(text = "Online") }

            when {
                searchState.isLoading -> {
                    item { LoadingIndicator() }
                }
                searchState.errorMessage != null -> {
                    item { EmptyMessage(searchState.errorMessage!!) }
                }
                searchState.items.isEmpty() -> {
                    item { EmptyMessage(text = "No results") }
                }
                else -> {
                    items(searchState.items, key = { it.id }) { playlist ->
                        val artwork = playlist.artworkUrl?.let { artworkCache[it] }
                        PlaylistListItem(
                            playlist = playlist,
                            backgroundColor = Color.White.copy(alpha = 0.08f),
                            artwork = artwork,
                            onClick = { viewModel.playPlaylist(playlist) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchControls(
    query: String,
    onSearchClicked: () -> Unit,
    accentColor: Color? = null,
    onClearQuery: () -> Unit
) {
    val animatedColor by animateColorAsState(
        targetValue = accentColor ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 1000)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onSearchClicked,
            colors = ButtonDefaults.buttonColors(animatedColor),
            modifier = Modifier
                .weight(1f)
        ) {
            val label = if (query.isBlank()) "Search" else query
            Text(text = label, maxLines = 1)
        }
        if (query.isNotBlank()) {
            Button(onClick = onClearQuery) {
                Text(text = "Clear")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = Color.White.copy(alpha = 0.7f),
        textAlign = TextAlign.Start
    )
}

@Composable
private fun SubSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        color = Color.White.copy(alpha = 0.6f),
        textAlign = TextAlign.Start
    )
}

@Composable
private fun LoadingIndicator() {
    CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        textAlign = TextAlign.Center,
        color = Color.White.copy(alpha = 0.7f)
    )
}
