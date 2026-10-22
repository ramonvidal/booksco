package com.punyplatipus.podouvir.testutil

import com.punyplatipus.podouvir.data.repository.PodcastRepository
import com.punyplatipus.podouvir.domain.model.Episode
import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory PodcastRepository for ViewModel tests. Configure results via the public vars. */
class FakePodcastRepository(initial: List<Podcast> = emptyList()) : PodcastRepository {
    val podcasts = MutableStateFlow(initial)
    val episodes = MutableStateFlow<Map<Long, List<Episode>>>(emptyMap())

    var searchResult: Result<List<PodcastSearchResult>> = Result.success(emptyList())
    var subscribeResult: ((String) -> Result<Long>)? = null
    val searchCalls = mutableListOf<String>()
    val subscribeCalls = mutableListOf<Pair<String, String?>>()
    val unsubscribeCalls = mutableListOf<Long>()
    val genreOverrideCalls = mutableListOf<Pair<Long, String?>>()
    val customOrderCalls = mutableListOf<List<Long>>()

    override fun observeLibrary(): Flow<List<Podcast>> = podcasts
    override fun observeEpisodes(podcastId: Long): Flow<List<Episode>> = episodes.map { it[podcastId].orEmpty() }

    override suspend fun search(term: String): Result<List<PodcastSearchResult>> {
        searchCalls += term
        return searchResult
    }

    /** Default: adds a podcast with the next id (or returns the existing one). */
    override suspend fun subscribe(feedUrl: String, fallbackGenre: String?): Result<Long> {
        subscribeCalls += feedUrl to fallbackGenre
        subscribeResult?.let { return it(feedUrl) }
        podcasts.value.firstOrNull { it.feedUrl == feedUrl.trim() }?.let { return Result.success(it.id) }
        val id = (podcasts.value.maxOfOrNull { it.id } ?: 0) + 1
        podcasts.update { it + Podcast(id = id, feedUrl = feedUrl.trim(), title = "Podcast $id", genre = fallbackGenre.orEmpty(), customOrder = it.size) }
        return Result.success(id)
    }

    override suspend fun unsubscribe(podcastId: Long) {
        unsubscribeCalls += podcastId
        podcasts.update { list -> list.filterNot { it.id == podcastId } }
    }

    override suspend fun refresh(podcastId: Long): Result<Int> = Result.success(0)

    override suspend fun setGenreOverride(podcastId: Long, genre: String?) {
        genreOverrideCalls += podcastId to genre
        podcasts.update { list ->
            list.map { if (it.id == podcastId) it.copy(genreOverride = genre?.takeIf { g -> g.isNotBlank() }) else it }
        }
    }

    override suspend fun setCustomOrder(orderedIds: List<Long>) {
        customOrderCalls += orderedIds
        podcasts.update { list ->
            list.map { p -> orderedIds.indexOf(p.id).takeIf { it >= 0 }?.let { p.copy(customOrder = it) } ?: p }
        }
    }
}
