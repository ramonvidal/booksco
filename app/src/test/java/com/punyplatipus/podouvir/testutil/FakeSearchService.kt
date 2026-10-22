package com.punyplatipus.podouvir.testutil

import com.punyplatipus.podouvir.data.search.PodcastSearchService
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import kotlinx.coroutines.CompletableDeferred

class FakeSearchService : PodcastSearchService {
    var results: List<PodcastSearchResult> = emptyList()
    var error: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<Pair<String, Int>>()

    override suspend fun search(term: String, limit: Int): List<PodcastSearchResult> {
        calls += term to limit
        gate?.await()
        error?.let { throw it }
        return results
    }
}
