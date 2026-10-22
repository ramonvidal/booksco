package com.punyplatipus.podouvir.domain.model

data class PlaybackState(
    val episodeId: Long,
    val positionMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)
