package com.punyplatipus.podouvir.data.db

import com.punyplatipus.podouvir.domain.model.Episode
import com.punyplatipus.podouvir.domain.model.PlaybackState
import com.punyplatipus.podouvir.domain.model.Podcast

fun PodcastEntity.toDomain(): Podcast = Podcast(
    id = id,
    feedUrl = feedUrl,
    title = title,
    author = author,
    description = description,
    artworkUrl = artworkUrl,
    genre = genre,
    genreOverride = genreOverride,
    addedAt = addedAt,
    lastUpdated = lastUpdated,
    customOrder = customOrder,
)

fun Podcast.toEntity(): PodcastEntity = PodcastEntity(
    id = id,
    feedUrl = feedUrl,
    title = title,
    author = author,
    description = description,
    artworkUrl = artworkUrl,
    genre = genre,
    genreOverride = genreOverride,
    addedAt = addedAt,
    lastUpdated = lastUpdated,
    customOrder = customOrder,
)

fun EpisodeEntity.toDomain(): Episode = Episode(
    id = id,
    podcastId = podcastId,
    guid = guid,
    title = title,
    description = description,
    audioUrl = audioUrl,
    durationMs = durationMs,
    publishedAt = publishedAt,
)

fun Episode.toEntity(): EpisodeEntity = EpisodeEntity(
    id = id,
    podcastId = podcastId,
    guid = guid,
    title = title,
    description = description,
    audioUrl = audioUrl,
    durationMs = durationMs,
    publishedAt = publishedAt,
)

fun PlaybackStateEntity.toDomain(): PlaybackState = PlaybackState(
    episodeId = episodeId,
    positionMs = positionMs,
    completed = completed,
    updatedAt = updatedAt,
)

fun PlaybackState.toEntity(): PlaybackStateEntity = PlaybackStateEntity(
    episodeId = episodeId,
    positionMs = positionMs,
    completed = completed,
    updatedAt = updatedAt,
)
