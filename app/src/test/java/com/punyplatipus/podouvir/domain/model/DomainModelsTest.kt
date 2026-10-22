package com.punyplatipus.podouvir.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainModelsTest {

    @Test
    fun `Podcast defaults match the spec`() {
        val p = Podcast(feedUrl = "u", title = "t")
        assertEquals(0L, p.id)
        assertEquals("", p.author)
        assertEquals("", p.description)
        assertNull(p.artworkUrl)
        assertEquals("", p.genre)
        assertNull(p.genreOverride)
        assertEquals(0L, p.addedAt)
        assertEquals(0L, p.lastUpdated)
        assertEquals(0, p.customOrder)
    }

    @Test
    fun `Episode defaults match the spec`() {
        val e = Episode(podcastId = 1, guid = "g", title = "t", audioUrl = "a")
        assertEquals(0L, e.id)
        assertEquals("", e.description)
        assertNull(e.durationMs)
        assertNull(e.publishedAt)
    }

    @Test
    fun `models are value-equal and copyable`() {
        val p = Podcast(feedUrl = "u", title = "t")
        assertEquals(p, Podcast(feedUrl = "u", title = "t"))
        assertEquals(p.hashCode(), Podcast(feedUrl = "u", title = "t").hashCode())
        assertNotEquals(p, p.copy(title = "x"))

        val e = Episode(podcastId = 1, guid = "g", title = "t", audioUrl = "a")
        assertEquals(e, Episode(podcastId = 1, guid = "g", title = "t", audioUrl = "a"))
        assertEquals(e.hashCode(), e.copy().hashCode())
        assertNotEquals(e, e.copy(guid = "h"))

        val s = PlaybackState(episodeId = 1, positionMs = 10, completed = false, updatedAt = 5)
        assertEquals(s, PlaybackState(1, 10, false, 5))
        assertEquals(s.hashCode(), s.copy().hashCode())
        assertNotEquals(s, s.copy(positionMs = 11))

        val r = PodcastSearchResult("f", "t", "a", null, "g")
        assertEquals(r, PodcastSearchResult("f", "t", "a", null, "g"))
        assertEquals(r.hashCode(), r.copy().hashCode())
        assertNotEquals(r, r.copy(genre = "h"))
    }

    @Test
    fun `UNKNOWN_GENRE constant is Other`() {
        assertEquals("Other", UNKNOWN_GENRE)
    }
}
