package com.punyplatipus.podouvir.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

fun createDb(): PodOuvirDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        PodOuvirDatabase::class.java,
    ).allowMainThreadQueries().build()

fun podcastEntity(
    id: Long = 0,
    feedUrl: String = "https://example.com/feed.xml",
    title: String = "Title",
    author: String = "Author",
    description: String = "Description",
    artworkUrl: String? = "https://example.com/art.png",
    genre: String = "Technology",
    genreOverride: String? = null,
    addedAt: Long = 1_000L,
    lastUpdated: Long = 2_000L,
    customOrder: Int = 0,
) = PodcastEntity(
    id = id, feedUrl = feedUrl, title = title, author = author, description = description,
    artworkUrl = artworkUrl, genre = genre, genreOverride = genreOverride,
    addedAt = addedAt, lastUpdated = lastUpdated, customOrder = customOrder,
)

fun episodeEntity(
    podcastId: Long,
    guid: String,
    id: Long = 0,
    title: String = "Episode $guid",
    description: String = "Desc $guid",
    audioUrl: String = "https://example.com/$guid.mp3",
    durationMs: Long? = 60_000L,
    publishedAt: Long? = 1_000L,
) = EpisodeEntity(
    id = id, podcastId = podcastId, guid = guid, title = title, description = description,
    audioUrl = audioUrl, durationMs = durationMs, publishedAt = publishedAt,
)

fun playbackState(
    episodeId: Long,
    positionMs: Long = 1_000L,
    completed: Boolean = false,
    updatedAt: Long = 5_000L,
) = PlaybackStateEntity(episodeId, positionMs, completed, updatedAt)
