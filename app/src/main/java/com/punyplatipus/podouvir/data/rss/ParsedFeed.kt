package com.punyplatipus.podouvir.data.rss

data class ParsedFeed(
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val genre: String,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val durationMs: Long?,
    val publishedAt: Long?,
)

class FeedParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
