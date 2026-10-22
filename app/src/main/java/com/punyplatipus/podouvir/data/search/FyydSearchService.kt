package com.punyplatipus.podouvir.data.search

import com.punyplatipus.podouvir.di.IoDispatcher
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject

interface FyydApi {
    @GET("0.2/search/podcast")
    suspend fun searchPodcasts(@Query("term") term: String, @Query("count") count: Int): FyydResponse
}

@Serializable
data class FyydResponse(val status: Int = 0, val data: List<FyydPodcastDto> = emptyList())

@Serializable
data class FyydPodcastDto(
    val title: String? = null,
    val xmlURL: String? = null,
    val imgURL: String? = null,
    val author: String? = null,
    val categories: List<Int> = emptyList(),
)

/** Top-level category start ids, ascending. A subcategory id belongs to the closest start at or below it. */
private val FYYD_TOP_LEVEL: List<Pair<Int, String>> = listOf(
    100 to "Arts",
    108 to "Business",
    115 to "Comedy",
    119 to "Education",
    124 to "Fiction",
    128 to "Government",
    129 to "History",
    130 to "Health & Fitness",
    137 to "Kids & Family",
    142 to "Leisure",
    151 to "Music",
    155 to "News",
    163 to "Religion & Spirituality",
    171 to "Science",
    181 to "Society & Culture",
    187 to "Sports",
    203 to "Technology",
    204 to "True Crime",
    205 to "TV & Film",
)

private const val FYYD_MIN_ID = 100
private const val FYYD_MAX_ID = 210

/** Top-level fyyd category name for any category or subcategory id; null if unknown. */
fun fyydGenreName(categoryId: Int): String? {
    if (categoryId < FYYD_MIN_ID || categoryId > FYYD_MAX_ID) return null
    return FYYD_TOP_LEVEL.lastOrNull { it.first <= categoryId }?.second
}

fun FyydPodcastDto.toSearchResultOrNull(): PodcastSearchResult? {
    val feed = xmlURL?.takeIf { it.isNotBlank() } ?: return null
    val name = title?.takeIf { it.isNotBlank() } ?: return null
    return PodcastSearchResult(
        feedUrl = feed,
        title = name,
        author = author.orEmpty(),
        artworkUrl = imgURL?.takeIf { it.isNotBlank() },
        genre = categories.firstOrNull()?.let { fyydGenreName(it) }.orEmpty(),
    )
}

class FyydSearchService @Inject constructor(
    private val api: FyydApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PodcastSearchService {
    override suspend fun search(term: String, limit: Int): List<PodcastSearchResult> {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return emptyList()
        return withContext(io) {
            api.searchPodcasts(trimmed, limit).data
                .mapNotNull { it.toSearchResultOrNull() }
                .distinctBy { it.feedUrl }
        }
    }
}
