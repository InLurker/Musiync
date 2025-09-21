package com.metrolist.music.presentation.data

import android.graphics.Bitmap
import com.metrolist.music.datastore.PlaylistCollectionProto
import com.metrolist.music.shared.model.PlaylistSummary
import com.metrolist.music.shared.model.toModelList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class PlaylistSectionState(
    val requestId: Long? = null,
    val query: String = "",
    val items: List<PlaylistSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdatedAt: Long? = null
)

@Singleton
class PlaylistRepository @Inject constructor() {

    val libraryState = MutableStateFlow(PlaylistSectionState())
    val searchState = MutableStateFlow(PlaylistSectionState())
    val artworkCache = MutableStateFlow<Map<String, Bitmap?>>(emptyMap())

    fun markLibraryLoading(requestId: Long) {
        libraryState.update { current ->
            current.copy(requestId = requestId, isLoading = true, errorMessage = null)
        }
    }

    fun markSearchLoading(requestId: Long, query: String) {
        searchState.update { current ->
            current.copy(
                requestId = requestId,
                query = query,
                isLoading = true,
                errorMessage = null
            )
        }
    }

    fun updateSearchQuery(query: String) {
        searchState.update { current -> current.copy(query = query) }
    }

    fun clearSearchResults() {
        searchState.update { current ->
            current.copy(
                items = emptyList(),
                isLoading = false,
                errorMessage = null,
                requestId = null
            )
        }
    }

    fun handleLibraryResponse(
        payload: PlaylistCollectionProto,
        newArtworks: Map<String, Bitmap?>
    ) {
        libraryState.update { current ->
            if (current.requestId != null && payload.requestId < current.requestId) {
                return@update current
            }
            current.copy(
                requestId = payload.requestId,
                items = payload.toModelList(),
                isLoading = false,
                errorMessage = null,
                lastUpdatedAt = payload.generatedAt.takeIf { it != 0L }
            )
        }
        mergeArtworks(newArtworks)
    }

    fun handleSearchResponse(
        payload: PlaylistCollectionProto,
        newArtworks: Map<String, Bitmap?>
    ) {
        searchState.update { current ->
            if (current.requestId != null && payload.requestId < current.requestId) {
                return@update current
            }
            current.copy(
                requestId = payload.requestId,
                items = payload.toModelList(),
                isLoading = false,
                errorMessage = null,
                lastUpdatedAt = payload.generatedAt.takeIf { it != 0L }
            )
        }
        mergeArtworks(newArtworks)
    }

    fun setSearchError(message: String?) {
        searchState.update { current ->
            current.copy(
                isLoading = false,
                errorMessage = message
            )
        }
    }

    private fun mergeArtworks(newArtworks: Map<String, Bitmap?>) {
        if (newArtworks.isEmpty()) return
        artworkCache.update { current -> current + newArtworks.filterValues { it != null } }
    }
}
