package com.punyplatipus.podouvir.data.search

import com.punyplatipus.podouvir.di.IoDispatcher
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject

data class PodcastIndexCredentials(val key: String, val secret: String) {
    val isConfigured: Boolean get() = key.isNotBlank() && secret.isNotBlank()
}

/** Lowercase hex SHA-1 of key + secret + date, as required by the Podcast Index API. */
fun podcastIndexAuthorization(key: String, secret: String, epochSeconds: Long): String =
    MessageDigest.getInstance("SHA-1")
        .digest("$key$secret$epochSeconds".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

class PodcastIndexAuthInterceptor(
    private val credentials: PodcastIndexCredentials,
    private val clock: Clock,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val now = clock.instant().epochSecond
        val request = chain.request().newBuilder()
            .header("X-Auth-Key", credentials.key)
            .header("X-Auth-Date", now.toString())
            .header("Authorization", podcastIndexAuthorization(credentials.key, credentials.secret, now))
            .build()
        return chain.proceed(request)
    }
}

interface PodcastIndexApi {
    @GET("api/1.0/search/byterm")
    suspend fun searchByTerm(@Query("q") term: String, @Query("max") max: Int): PodcastIndexResponse
}

@Serializable
data class PodcastIndexResponse(val feeds: List<PodcastIndexFeedDto> = emptyList())

@Serializable
data class PodcastIndexFeedDto(
    val title: String? = null,
    val url: String? = null,
    val author: String? = null,
    val image: String? = null,
    val artwork: String? = null,
    val categories: Map<String, String>? = null,
)

fun PodcastIndexFeedDto.toSearchResultOrNull(): PodcastSearchResult? {
    val feed = url?.takeIf { it.isNotBlank() } ?: return null
    val name = title?.takeIf { it.isNotBlank() } ?: return null
    val genre = categories.orEmpty().entries
        .mapNotNull { (k, v) -> k.trim().toLongOrNull()?.let { it to v } }
        .minByOrNull { it.first }?.second.orEmpty()
    return PodcastSearchResult(
        feedUrl = feed,
        title = name,
        author = author.orEmpty(),
        artworkUrl = artwork?.takeIf { it.isNotBlank() } ?: image?.takeIf { it.isNotBlank() },
        genre = genre,
    )
}

class PodcastIndexSearchService @Inject constructor(
    private val api: PodcastIndexApi,
    private val credentials: PodcastIndexCredentials,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PodcastSearchService {
    override suspend fun search(term: String, limit: Int): List<PodcastSearchResult> {
        val trimmed = term.trim()
        if (trimmed.isEmpty() || !credentials.isConfigured) return emptyList()
        return withContext(io) {
            api.searchByTerm(trimmed, limit).feeds
                .mapNotNull { it.toSearchResultOrNull() }
                .distinctBy { it.feedUrl }
        }
    }
}
