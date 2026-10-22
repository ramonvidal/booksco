package com.punyplatipus.podouvir.domain.model

data class PodcastSearchResult(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val genre: String,
)
