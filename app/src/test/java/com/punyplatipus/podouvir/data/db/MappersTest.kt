package com.punyplatipus.podouvir.data.db

import com.punyplatipus.podouvir.domain.model.Episode
import com.punyplatipus.podouvir.domain.model.PlaybackState
import com.punyplatipus.podouvir.domain.model.Podcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    @Test fun `podcast entity to domain to entity is equal`() {
        val e = podcastEntity(id = 5, genreOverride = "Comedy", customOrder = 3)
        assertEquals(e, e.toDomain().toEntity())
    }

    @Test fun `podcast with null artworkUrl and genreOverride round-trips`() {
        val e = podcastEntity(id = 2, artworkUrl = null, genreOverride = null)
        val d = e.toDomain()
        assertNull(d.artworkUrl)
        assertNull(d.genreOverride)
        assertEquals(e, d.toEntity())
    }

    @Test fun `podcast domain to entity to domain is equal`() {
        val d = Podcast(
            id = 9, feedUrl = "u", title = "T", author = "A", description = "D",
            artworkUrl = "art", genre = "G", genreOverride = "O",
            addedAt = 11, lastUpdated = 22, customOrder = 4,
        )
        assertEquals(d, d.toEntity().toDomain())
    }

    @Test fun `episode round-trips with and without durationMs and publishedAt`() {
        val full = Episode(1, 2, "g", "t", "d", "https://a/x.mp3", 1000L, 2000L)
        val sparse = Episode(3, 2, "g2", "t2", "", "https://a/y.mp3", null, null)
        assertEquals(full, full.toEntity().toDomain())
        assertEquals(sparse, sparse.toEntity().toDomain())
        val fullE = episodeEntity(2, "g", id = 1)
        val sparseE = episodeEntity(2, "g2", id = 3, durationMs = null, publishedAt = null)
        assertEquals(fullE, fullE.toDomain().toEntity())
        assertEquals(sparseE, sparseE.toDomain().toEntity())
    }

    @Test fun `playback state round-trips with completed true and false`() {
        listOf(true, false).forEach { c ->
            val d = PlaybackState(1, 1234, c, 99)
            assertEquals(d, d.toEntity().toDomain())
            val e = playbackState(1, 1234, c, 99)
            assertEquals(e, e.toDomain().toEntity())
        }
    }

    @Test fun `mapper preserves ids including default 0`() {
        assertEquals(0L, podcastEntity().toDomain().toEntity().id)
        assertEquals(0L, Podcast(feedUrl = "u", title = "t").toEntity().id)
        assertEquals(0L, Episode(podcastId = 1, guid = "g", title = "t", audioUrl = "a").toEntity().id)
    }
}
