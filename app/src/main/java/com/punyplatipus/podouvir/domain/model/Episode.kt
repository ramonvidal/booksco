package com.punyplatipus.podouvir.domain.model

data class Episode(
    val id: Long = 0,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val description: String = "",
    val audioUrl: String,
    val durationMs: Long? = null,
    val publishedAt: Long? = null,
)
