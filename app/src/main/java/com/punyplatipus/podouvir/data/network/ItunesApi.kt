package com.punyplatipus.podouvir.data.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("media") media: String = "podcast",
        @Query("entity") entity: String = "podcast",
        @Query("limit") limit: Int = 25,
    ): ItunesSearchResponse
}

@Serializable
data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesPodcastDto> = emptyList(),
)

@Serializable
data class ItunesPodcastDto(
    val collectionName: String? = null,
    val artistName: String? = null,
    val feedUrl: String? = null,
    val artworkUrl600: String? = null,
    val artworkUrl100: String? = null,
    val primaryGenreName: String? = null,
)

/** Null when feedUrl or collectionName is missing/blank. artwork = 600 ?: 100; author/genre default "". */
fun ItunesPodcastDto.toSearchResultOrNull(): PodcastSearchResult? {
    val feed = feedUrl?.takeIf { it.isNotBlank() } ?: return null
    val title = collectionName?.takeIf { it.isNotBlank() } ?: return null
    return PodcastSearchResult(
        feedUrl = feed,
        title = title,
        author = artistName.orEmpty(),
        artworkUrl = artworkUrl600 ?: artworkUrl100,
        genre = primaryGenreName.orEmpty(),
    )
}

/** Json used for all network parsing. */
val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Helper used by NetworkModule and tests. The converter ignores the response content type. */
fun createItunesApi(baseUrl: String, client: OkHttpClient, json: Json = NetworkJson): ItunesApi =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ItunesApi::class.java)
