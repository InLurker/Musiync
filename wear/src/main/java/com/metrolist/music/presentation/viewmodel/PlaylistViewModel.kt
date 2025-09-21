package com.metrolist.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.presentation.data.PlaylistRepository
import com.metrolist.music.presentation.wear.MessageClientService
import com.metrolist.music.shared.model.LibraryEntry
import com.metrolist.music.shared.model.PlaylistSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val messageClientService: MessageClientService
) : ViewModel() {

    val libraryState = playlistRepository.libraryState
    val searchState = playlistRepository.searchState
    val artworkCache = playlistRepository.artworkCache

    private val searchInput = MutableStateFlow<String?>(null)
    private val requestIdGenerator = AtomicLong(0L)

    init {
        observeSearchInput()
        requestLibrary()
    }

    fun refreshLibrary() {
        requestLibrary()
    }

    fun updateSearchQuery(query: String) {
        playlistRepository.updateSearchQuery(query)
        searchInput.value = query
    }

    fun clearSearch() {
        playlistRepository.updateSearchQuery("")
        playlistRepository.clearSearchResults()
        searchInput.value = ""
    }

    fun playPlaylist(playlistSummary: PlaylistSummary) {
        messageClientService.playPlaylist(playlistSummary)
    }

    fun playLibraryEntry(entry: LibraryEntry) {
        messageClientService.playLibraryEntry(entry)
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchInput() {
        viewModelScope.launch {
            searchInput
                .filterNotNull()
                .debounce(400.milliseconds)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        playlistRepository.clearSearchResults()
                        return@collect
                    }
                    val requestId = nextRequestId()
                    playlistRepository.markSearchLoading(requestId, query)
                    messageClientService.requestPlaylistSearch(requestId, query)
                }
        }
    }

    private fun requestLibrary() {
        val requestId = nextRequestId()
        playlistRepository.markLibraryLoading(requestId)
        messageClientService.requestPlaylistLibrary(requestId)
    }

    private fun nextRequestId(): Long = requestIdGenerator.incrementAndGet()
}
