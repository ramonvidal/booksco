package com.punyplatipus.podouvir.data.repository

import com.punyplatipus.podouvir.domain.model.Episode
import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import kotlinx.coroutines.flow.Flow

interface PodcastRepository {
    fun observeLibrary(): Flow<List<Podcast>>
    fun observeEpisodes(podcastId: Long): Flow<List<Episode>>
    suspend fun search(term: String): Result<List<PodcastSearchResult>>

    /** Downloads + parses the feed, stores podcast and episodes. Returns the podcast id.
     *  feedUrl is normalised by trimming whitespace only (no case or trailing-slash changes).
     *  Already subscribed (same normalised feedUrl) -> success with the existing id, nothing re-downloaded. */
    suspend fun subscribe(feedUrl: String, fallbackGenre: String? = null): Result<Long>
    suspend fun unsubscribe(podcastId: Long)

    /** Re-downloads the feed; inserts new episodes; updates title/author/description/artwork/genre
     *  (never genreOverride, addedAt or customOrder). Returns the number of new episodes. */
    suspend fun refresh(podcastId: Long): Result<Int>

    /** Pass null or blank to clear the override. */
    suspend fun setGenreOverride(podcastId: Long, genre: String?)

    /** Sets customOrder = index in [orderedIds] for each id, atomically. Unknown ids are ignored. */
    suspend fun setCustomOrder(orderedIds: List<Long>)
}
