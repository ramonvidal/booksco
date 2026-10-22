package com.punyplatipus.podouvir.data.repository

import com.punyplatipus.podouvir.data.db.EpisodeDao
import com.punyplatipus.podouvir.data.db.EpisodeEntity
import com.punyplatipus.podouvir.data.db.PodcastDao
import com.punyplatipus.podouvir.data.db.PodcastEntity
import com.punyplatipus.podouvir.data.db.toDomain
import com.punyplatipus.podouvir.data.network.FeedFetcher
import com.punyplatipus.podouvir.data.rss.ParsedEpisode
import com.punyplatipus.podouvir.data.rss.RssParser
import com.punyplatipus.podouvir.data.search.PodcastSearchService
import com.punyplatipus.podouvir.di.IoDispatcher
import com.punyplatipus.podouvir.domain.model.Episode
import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject

class DefaultPodcastRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val searchService: PodcastSearchService,
    private val feedFetcher: FeedFetcher,
    private val rssParser: RssParser,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PodcastRepository {

    override fun observeLibrary(): Flow<List<Podcast>> =
        podcastDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeEpisodes(podcastId: Long): Flow<List<Episode>> =
        episodeDao.observeForPodcast(podcastId).map { list -> list.map { it.toDomain() } }

    override suspend fun search(term: String): Result<List<PodcastSearchResult>> =
        runCatchingCancellable { searchService.search(term) }

    override suspend fun subscribe(feedUrl: String, fallbackGenre: String?): Result<Long> {
        val url = feedUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Feed URL must start with http:// or https://"))
        }
        return runCatchingCancellable {
            withContext(ioDispatcher) {
                podcastDao.getByFeedUrl(url)?.id ?: run {
                    val feed = feedFetcher.fetch(url) { rssParser.parse(it) }
                    val now = clock.millis()
                    val entity = PodcastEntity(
                        feedUrl = url,
                        title = feed.title,
                        author = feed.author,
                        description = feed.description,
                        artworkUrl = feed.artworkUrl,
                        genre = feed.genre.ifBlank { fallbackGenre.orEmpty() },
                        genreOverride = null,
                        addedAt = now,
                        lastUpdated = now,
                        customOrder = (podcastDao.maxCustomOrder() ?: -1) + 1,
                    )
                    val id = podcastDao.insert(entity)
                    if (id == -1L) {
                        podcastDao.getByFeedUrl(url)?.id ?: error("Podcast insert failed")
                    } else {
                        episodeDao.insertAll(feed.episodes.map { it.toEntity(id) })
                        id
                    }
                }
            }
        }
    }

    override suspend fun unsubscribe(podcastId: Long) = withContext(ioDispatcher) {
        podcastDao.deleteById(podcastId)
    }

    override suspend fun refresh(podcastId: Long): Result<Int> = runCatchingCancellable {
        withContext(ioDispatcher) {
            val existing = podcastDao.getById(podcastId)
                ?: throw NoSuchElementException("No podcast with id $podcastId")
            val feed = feedFetcher.fetch(existing.feedUrl) { rssParser.parse(it) }
            val inserted = episodeDao.insertAll(feed.episodes.map { it.toEntity(podcastId) }).count { it != -1L }
            podcastDao.update(
                existing.copy(
                    title = feed.title,
                    author = feed.author,
                    description = feed.description,
                    artworkUrl = feed.artworkUrl,
                    genre = feed.genre.ifBlank { existing.genre },
                    lastUpdated = if (inserted > 0) clock.millis() else existing.lastUpdated,
                ),
            )
            inserted
        }
    }

    override suspend fun setGenreOverride(podcastId: Long, genre: String?) = withContext(ioDispatcher) {
        podcastDao.setGenreOverride(podcastId, genre?.takeIf { it.isNotBlank() })
    }

    override suspend fun setCustomOrder(orderedIds: List<Long>) = withContext(ioDispatcher) {
        podcastDao.reorder(orderedIds)
    }

    private fun ParsedEpisode.toEntity(podcastId: Long) = EpisodeEntity(
        podcastId = podcastId,
        guid = guid,
        title = title,
        description = description,
        audioUrl = audioUrl,
        durationMs = durationMs,
        publishedAt = publishedAt,
    )

    private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
