package com.punyplatipus.podouvir.domain

import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.sortPodcasts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastSortingTest {

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

    @Test
    fun `sort by TITLE is case-insensitive A to Z`() {
        val list = listOf(podcast(1, "banana"), podcast(2, "Apple"), podcast(3, "cherry"))
        assertEquals(listOf("Apple", "banana", "cherry"), list.sortPodcasts(SortOrder.TITLE).map { it.title })
    }

    @Test
    fun `sort by RECENTLY_UPDATED puts newest lastUpdated first`() {
        val list = listOf(podcast(1, lastUpdated = 10), podcast(2, lastUpdated = 30), podcast(3, lastUpdated = 20))
        assertEquals(listOf(30L, 20L, 10L), list.sortPodcasts(SortOrder.RECENTLY_UPDATED).map { it.lastUpdated })
    }

    @Test
    fun `sort by DATE_ADDED puts newest addedAt first`() {
        val list = listOf(podcast(1, addedAt = 5), podcast(2, addedAt = 50), podcast(3, addedAt = 25))
        assertEquals(listOf(50L, 25L, 5L), list.sortPodcasts(SortOrder.DATE_ADDED).map { it.addedAt })
    }

    @Test
    fun `sort by CUSTOM orders by customOrder ascending`() {
        val list = listOf(podcast(1, customOrder = 2), podcast(2, customOrder = 0), podcast(3, customOrder = 1))
        assertEquals(listOf(0, 1, 2), list.sortPodcasts(SortOrder.CUSTOM).map { it.customOrder })
    }

    @Test
    fun `ties are broken by id ascending for every order`() {
        for (order in SortOrder.values()) {
            val titles = if (order == SortOrder.TITLE) listOf("a", "A", "a") else listOf("t", "t", "t")
            val list = listOf(podcast(3, titles[0]), podcast(1, titles[1]), podcast(2, titles[2]))
            assertEquals("order $order", listOf(1L, 2L, 3L), list.sortPodcasts(order).map { it.id })
        }
    }

    @Test
    fun `sort returns a new list and leaves input unchanged`() {
        val list = mutableListOf(podcast(3, "c"), podcast(1, "a"), podcast(2, "b"))
        val before = list.toList()
        val result = list.sortPodcasts(SortOrder.TITLE)
        assertEquals(before, list)
        assertNotSame(list, result)
        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
    }

    @Test
    fun `sort of empty list returns empty list`() {
        for (order in SortOrder.values()) {
            assertTrue("order $order", emptyList<Podcast>().sortPodcasts(order).isEmpty())
        }
    }

    @Test
    fun `sort of single element returns that element`() {
        val p = podcast(1, "only")
        for (order in SortOrder.values()) {
            assertEquals(listOf(p), listOf(p).sortPodcasts(order))
        }
    }

    @Test
    fun `sort TITLE handles non-ASCII and leading digits deterministically`() {
        val list = listOf(podcast(1, "Éclair"), podcast(2, "eclair"), podcast(3, "10 Things"), podcast(4, "Zed"))
        val first = list.sortPodcasts(SortOrder.TITLE)
        assertEquals(first, list.sortPodcasts(SortOrder.TITLE))
        val titles = first.map { it.title }
        assertTrue(titles.indexOf("10 Things") < titles.indexOf("Zed"))
    }
}
