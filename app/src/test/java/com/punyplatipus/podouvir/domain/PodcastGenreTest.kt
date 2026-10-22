package com.punyplatipus.podouvir.domain

import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.UNKNOWN_GENRE
import com.punyplatipus.podouvir.domain.model.groupByGenre
import com.punyplatipus.podouvir.domain.model.sortPodcasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastGenreTest {

    private fun podcast(
        id: Long,
        title: String = "t",
        genre: String = "",
        override: String? = null,
        addedAt: Long = 0,
        lastUpdated: Long = 0,
        customOrder: Int = 0,
    ) = Podcast(
        id = id,
        feedUrl = "https://example.com/$id",
        title = title,
        genre = genre,
        genreOverride = override,
        addedAt = addedAt,
        lastUpdated = lastUpdated,
        customOrder = customOrder,
    )

    // effectiveGenre

    @Test
    fun `effectiveGenre uses override when set`() {
        assertEquals("Tech", podcast(1, genre = "News", override = "Tech").effectiveGenre)
    }

    @Test
    fun `effectiveGenre falls back to genre when override is null`() {
        assertEquals("News", podcast(1, genre = "News", override = null).effectiveGenre)
    }

    @Test
    fun `effectiveGenre ignores blank override`() {
        assertEquals("News", podcast(1, genre = "News", override = "   ").effectiveGenre)
        assertEquals("News", podcast(1, genre = "News", override = "").effectiveGenre)
    }

    @Test
    fun `effectiveGenre trims override and genre`() {
        assertEquals("Tech", podcast(1, genre = "News", override = "  Tech ").effectiveGenre)
        assertEquals("News", podcast(1, genre = " News ", override = null).effectiveGenre)
    }

    @Test
    fun `effectiveGenre returns UNKNOWN_GENRE when both are empty or blank`() {
        for (genre in listOf("", "  ")) {
            for (override in listOf(null, "", "  ")) {
                assertEquals(UNKNOWN_GENRE, podcast(1, genre = genre, override = override).effectiveGenre)
            }
        }
        assertEquals("Other", UNKNOWN_GENRE)
    }

    @Test
    fun `effectiveGenre uses genre when override is blank and genre is set`() {
        assertEquals("Comedy", podcast(1, genre = "Comedy", override = " ").effectiveGenre)
    }

    // groupByGenre

    @Test
    fun `groupByGenre groups by effectiveGenre`() {
        val groups = listOf(
            podcast(1, genre = "News"), podcast(2, genre = "Tech"), podcast(3, genre = "News"),
        ).groupByGenre(SortOrder.TITLE)
        assertEquals(2, groups.size)
        assertEquals(2, groups.getValue("News").size)
    }

    @Test
    fun `groupByGenre honours genreOverride over genre`() {
        val groups = listOf(podcast(1, genre = "News", override = "Tech")).groupByGenre(SortOrder.TITLE)
        assertEquals(listOf("Tech"), groups.keys.toList())
        assertFalse(groups.containsKey("News"))
    }

    @Test
    fun `groupByGenre merges genres differing only by case`() {
        val groups = listOf(
            podcast(1, genre = "Tech"), podcast(2, genre = "tech"), podcast(3, genre = "TECH"),
        ).groupByGenre(SortOrder.TITLE)
        assertEquals(1, groups.size)
        assertEquals(3, groups.values.single().size)
    }

    @Test
    fun `merged group is named after the first podcast in sorted order`() {
        val list = listOf(
            podcast(1, title = "A", genre = "tech", addedAt = 1),
            podcast(2, title = "B", genre = "Tech", addedAt = 2),
        )
        assertEquals(listOf("tech"), list.groupByGenre(SortOrder.TITLE).keys.toList())
        assertEquals(listOf("Tech"), list.groupByGenre(SortOrder.DATE_ADDED).keys.toList())
    }

    @Test
    fun `groupByGenre orders keys case-insensitively A to Z`() {
        val groups = listOf(
            podcast(1, genre = "zen"), podcast(2, genre = "Alpha"), podcast(3, genre = "beta"),
        ).groupByGenre(SortOrder.TITLE)
        assertEquals(listOf("Alpha", "beta", "zen"), groups.keys.toList())
    }

    @Test
    fun `UNKNOWN_GENRE group is always last`() {
        val groups = listOf(
            podcast(1, genre = "Zebra"), podcast(2, genre = ""), podcast(3, genre = "Alpha"),
        ).groupByGenre(SortOrder.TITLE)
        assertEquals(listOf("Alpha", "Zebra", UNKNOWN_GENRE), groups.keys.toList())
    }

    @Test
    fun `UNKNOWN_GENRE is last even when it sorts before other genres`() {
        val groups = listOf(
            podcast(1, genre = ""), podcast(2, genre = "Zoo"), podcast(3, genre = "Another"),
        ).groupByGenre(SortOrder.TITLE)
        assertEquals(UNKNOWN_GENRE, groups.keys.last())
        assertEquals(listOf("Another", "Zoo", UNKNOWN_GENRE), groups.keys.toList())
    }

    @Test
    fun `groupByGenre keeps order sort within each group`() {
        val subset = listOf(
            podcast(3, title = "b", genre = "News", addedAt = 5, lastUpdated = 5, customOrder = 1),
            podcast(1, title = "B", genre = "News", addedAt = 5, lastUpdated = 5, customOrder = 1),
            podcast(2, title = "a", genre = "News", addedAt = 9, lastUpdated = 1, customOrder = 0),
        )
        val all = subset + podcast(4, title = "z", genre = "Tech")
        for (order in SortOrder.values()) {
            assertEquals("order $order", subset.sortPodcasts(order), all.groupByGenre(order).getValue("News"))
        }
    }

    @Test
    fun `groupByGenre of empty list returns empty map`() {
        assertTrue(emptyList<Podcast>().groupByGenre(SortOrder.TITLE).isEmpty())
    }

    @Test
    fun `groupByGenre trims genre before grouping`() {
        val groups = listOf(podcast(1, genre = " News "), podcast(2, genre = "News")).groupByGenre(SortOrder.TITLE)
        assertEquals(listOf("News"), groups.keys.toList())
        assertEquals(2, groups.getValue("News").size)
    }

    @Test
    fun `groupByGenre merges a literal other genre with UNKNOWN_GENRE`() {
        val groups = listOf(
            podcast(1, genre = "other"), podcast(2, genre = ""), podcast(3, genre = "Zebra"),
        ).groupByGenre(SortOrder.TITLE)
        assertEquals(2, groups.size)
        assertEquals(UNKNOWN_GENRE, groups.keys.last())
        assertEquals(2, groups.values.last().size)
    }
}
