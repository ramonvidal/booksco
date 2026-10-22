package com.punyplatipus.podouvir.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.punyplatipus.podouvir.data.prefs.LibraryPreferences
import com.punyplatipus.podouvir.data.repository.PodcastRepository
import com.punyplatipus.podouvir.domain.model.LibrarySettings
import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.ViewMode
import com.punyplatipus.podouvir.domain.model.groupByGenre
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenreGroup(val genre: String, val podcasts: List<Podcast>)

data class LibraryUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val genres: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val sortOrder: SortOrder = SortOrder.TITLE,
    val viewMode: ViewMode = ViewMode.GRID,
    val groups: List<GenreGroup> = emptyList(),
    val isReordering: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val preferences: LibraryPreferences,
) : ViewModel() {

    private val reordering = MutableStateFlow(false)

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.observeLibrary(),
        preferences.settings,
        reordering,
    ) { library, settings, isReordering ->
        buildState(library, settings, isReordering)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun selectGenre(genre: String?) {
        viewModelScope.launch { preferences.setSelectedGenre(genre) }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { preferences.setSortOrder(order) }
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val current = preferences.settings.first().viewMode
            preferences.setViewMode(if (current == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
        }
    }

    fun startReorder() {
        reordering.value = true
        viewModelScope.launch { preferences.setSortOrder(SortOrder.CUSTOM) }
    }

    fun finishReorder() {
        reordering.value = false
    }

    fun moveUp(podcastId: Long) = move(podcastId, -1)

    fun moveDown(podcastId: Long) = move(podcastId, 1)

    fun changeGenre(podcastId: Long, genre: String?) {
        viewModelScope.launch { repository.setGenreOverride(podcastId, genre) }
    }

    fun unsubscribe(podcastId: Long) {
        viewModelScope.launch { repository.unsubscribe(podcastId) }
    }

    private fun move(podcastId: Long, delta: Int) {
        if (!reordering.value) return
        viewModelScope.launch {
            val library = repository.observeLibrary().first()
            val groups = library.groupByGenre(SortOrder.CUSTOM).values.map { it.toMutableList() }
            val group = groups.firstOrNull { list -> list.any { it.id == podcastId } } ?: return@launch
            val from = group.indexOfFirst { it.id == podcastId }
            val to = from + delta
            if (to !in group.indices) return@launch
            group[from] = group[to].also { group[to] = group[from] }
            repository.setCustomOrder(groups.flatten().map { it.id })
        }
    }
}

private fun buildState(
    library: List<Podcast>,
    settings: LibrarySettings,
    isReordering: Boolean,
): LibraryUiState {
    val all = library.groupByGenre(settings.sortOrder)
    val genres = all.keys.toList()
    val selected = settings.selectedGenre?.let { sel -> genres.firstOrNull { it.equals(sel, ignoreCase = true) } }
    val groups = all.entries
        .filter { selected == null || it.key == selected }
        .map { GenreGroup(it.key, it.value) }
    return LibraryUiState(
        isLoading = false,
        totalCount = library.size,
        genres = genres,
        selectedGenre = selected,
        sortOrder = settings.sortOrder,
        viewMode = settings.viewMode,
        groups = groups,
        isReordering = isReordering,
    )
}
