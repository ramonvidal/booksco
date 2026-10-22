package com.punyplatipus.podouvir.data.search

import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

interface PodcastSearchService {
    /** Trimmed [term]; blank -> empty list, no network. Results without feed URL or title are dropped; unique by feedUrl. */
    suspend fun search(term: String, limit: Int = 25): List<PodcastSearchResult>
}

class CompositeSearchService @Inject constructor(
    private val fyyd: FyydSearchService,
    private val podcastIndex: PodcastIndexSearchService,
    private val credentials: PodcastIndexCredentials,
) : PodcastSearchService {

    override suspend fun search(term: String, limit: Int): List<PodcastSearchResult> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!credentials.isConfigured) {
            return fyyd.search(trimmed, limit).distinctBy { it.feedUrl }.take(limit)
        }
        val (fyydOutcome, piOutcome) = coroutineScope {
            val f = async { attempt { fyyd.search(trimmed, limit) } }
            val p = async { attempt { podcastIndex.search(trimmed, limit) } }
            f.await() to p.await()
        }
        val fyydError = fyydOutcome.exceptionOrNull()
        if (fyydError != null && piOutcome.isFailure) throw fyydError
        val merged = piOutcome.getOrDefault(emptyList()) + fyydOutcome.getOrDefault(emptyList())
        return merged.distinctBy { it.feedUrl }.take(limit)
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
}
