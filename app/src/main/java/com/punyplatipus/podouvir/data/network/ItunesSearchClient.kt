package com.punyplatipus.podouvir.data.network

import com.punyplatipus.podouvir.di.IoDispatcher
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ItunesSearchClient @Inject constructor(
    private val api: ItunesApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** Trims [term]; blank returns an empty list without a network call. Drops unusable and duplicate results. */
    suspend fun search(term: String, limit: Int = 25): List<PodcastSearchResult> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return emptyList()
        return withContext(ioDispatcher) {
            api.search(term = trimmed, limit = limit)
                .results
                .mapNotNull { it.toSearchResultOrNull() }
                .distinctBy { it.feedUrl }
        }
    }
}
