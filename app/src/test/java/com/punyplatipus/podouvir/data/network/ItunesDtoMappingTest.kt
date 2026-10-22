package com.punyplatipus.podouvir.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ItunesDtoMappingTest {
    private fun dto(
        name: String? = "Title",
        artist: String? = "Author",
        feed: String? = "https://example.com/feed.xml",
        a600: String? = null,
        a100: String? = null,
        genre: String? = "Tech",
    ) = ItunesPodcastDto(name, artist, feed, a600, a100, genre)

    @Test
    fun `maps full dto to search result`() {
        val r = dto(a600 = "u600", a100 = "u100").toSearchResultOrNull()!!
        assertEquals("Title", r.title)
        assertEquals("Author", r.author)
        assertEquals("https://example.com/feed.xml", r.feedUrl)
        assertEquals("Tech", r.genre)
        assertEquals("u600", r.artworkUrl)
    }

    @Test
    fun `prefers artworkUrl600 over artworkUrl100`() {
        assertEquals("u600", dto(a600 = "u600", a100 = "u100").toSearchResultOrNull()!!.artworkUrl)
    }

    @Test
    fun `falls back to artworkUrl100 when 600 missing`() {
        assertEquals("u100", dto(a100 = "u100").toSearchResultOrNull()!!.artworkUrl)
    }

    @Test
    fun `artwork is null when both missing`() {
        val r = dto().toSearchResultOrNull()
        assertNotNull(r)
        assertNull(r!!.artworkUrl)
    }

    @Test
    fun `author and genre default to empty string`() {
        val r = dto(artist = null, genre = null).toSearchResultOrNull()!!
        assertEquals("", r.author)
        assertEquals("", r.genre)
    }

    @Test
    fun `returns null when feedUrl is null or blank`() {
        for (f in listOf(null, "", "   ")) assertNull("feed=$f", dto(feed = f).toSearchResultOrNull())
    }

    @Test
    fun `returns null when collectionName is null or blank`() {
        for (n in listOf(null, "", "   ")) assertNull("name=$n", dto(name = n).toSearchResultOrNull())
    }

    @Test
    fun `NetworkJson ignores unknown keys and absent nullable fields`() {
        val r = NetworkJson.decodeFromString<ItunesSearchResponse>(
            """{"resultCount":1,"extra":{"a":1},"results":[{"collectionName":"X","kind":"podcast","trackId":5,"genreIds":["1"]}]}""",
        )
        assertEquals(1, r.resultCount)
        assertEquals("X", r.results.single().collectionName)
        assertNull(r.results.single().feedUrl)
        val empty = NetworkJson.decodeFromString<ItunesSearchResponse>("{}")
        assertEquals(0, empty.resultCount)
        assertEquals(emptyList<ItunesPodcastDto>(), empty.results)
    }
}
