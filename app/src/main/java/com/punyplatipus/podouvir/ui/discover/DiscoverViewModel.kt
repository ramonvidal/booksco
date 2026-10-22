package com.punyplatipus.podouvir.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.punyplatipus.podouvir.data.repository.PodcastRepository
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SubscriptionStatus { NOT_SUBSCRIBED, SUBSCRIBING, SUBSCRIBED }

data class DiscoverItem(val result: PodcastSearchResult, val status: SubscriptionStatus)

enum class DiscoverMessage { SEARCH_FAILED, SUBSCRIBE_FAILED, SUBSCRIBED, INVALID_URL }

data class DiscoverUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val items: List<DiscoverItem> = emptyList(),
    val isAddingByUrl: Boolean = false,
    val message: DiscoverMessage? = null,
)

private data class Internal(
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<PodcastSearchResult> = emptyList(),
    val subscribing: Set<String> = emptySet(),
    val isAddingByUrl: Boolean = false,
    val message: DiscoverMessage? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val internal = MutableStateFlow(Internal())

    private val libraryUrls = repository.observeLibrary()
        .map { podcasts -> podcasts.map { it.feedUrl }.toSet() }
        .catch { emit(emptySet()) }

    val uiState: StateFlow<DiscoverUiState> =
        combine(query, internal, libraryUrls) { q, s, urls ->
            DiscoverUiState(
                query = q,
                isSearching = s.isSearching,
                hasSearched = s.hasSearched,
                items = s.results.map { r ->
                    val status = when {
                        r.feedUrl in urls -> SubscriptionStatus.SUBSCRIBED
                        r.feedUrl in s.subscribing -> SubscriptionStatus.SUBSCRIBING
                        else -> SubscriptionStatus.NOT_SUBSCRIBED
                    }
                    DiscoverItem(r, status)
                },
                isAddingByUrl = s.isAddingByUrl,
                message = s.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    init {
        viewModelScope.launch {
            query
                .map { it.trim() }
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { term -> runSearch(term) }
        }
    }

    private suspend fun runSearch(term: String) {
        if (term.isEmpty()) {
            internal.update { it.copy(results = emptyList(), hasSearched = false, isSearching = false) }
            return
        }
        internal.update { it.copy(isSearching = true) }
        val outcome = repository.search(term)
        // The repository may wrap a cancellation in a failed Result; never apply stale outcomes.
        currentCoroutineContext().ensureActive()
        outcome
            .onSuccess { list ->
                internal.update { it.copy(results = list, hasSearched = true, isSearching = false) }
            }
            .onFailure {
                internal.update { it.copy(message = DiscoverMessage.SEARCH_FAILED, isSearching = false) }
            }
    }

    fun onQueryChange(query: String) {
        this.query.value = query
    }

    fun subscribe(result: PodcastSearchResult) {
        var started = false
        internal.update {
            if (result.feedUrl in it.subscribing) it else {
                started = true
                it.copy(subscribing = it.subscribing + result.feedUrl)
            }
        }
        if (!started) return
        viewModelScope.launch {
            val outcome = repository.subscribe(result.feedUrl, result.genre)
            internal.update {
                it.copy(
                    subscribing = it.subscribing - result.feedUrl,
                    message = if (outcome.isSuccess) DiscoverMessage.SUBSCRIBED else DiscoverMessage.SUBSCRIBE_FAILED,
                )
            }
        }
    }

    fun addByUrl(url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            internal.update { it.copy(message = DiscoverMessage.INVALID_URL) }
            return
        }
        var started = false
        internal.update {
            if (it.isAddingByUrl) it else {
                started = true
                it.copy(isAddingByUrl = true)
            }
        }
        if (!started) return
        viewModelScope.launch {
            val outcome = repository.subscribe(trimmed, null)
            internal.update {
                it.copy(
                    isAddingByUrl = false,
                    message = if (outcome.isSuccess) DiscoverMessage.SUBSCRIBED else DiscoverMessage.SUBSCRIBE_FAILED,
                )
            }
        }
    }

    fun messageShown() {
        internal.update { it.copy(message = null) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
