package com.punyplatipus.podouvir.domain

import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.groupByGenre
import com.punyplatipus.podouvir.domain.model.sortPodcasts
import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastSortingTest {

    private fun podcast(id: Long, title: String, genre: String, added: Long = id, updated: Long = id, order: Int = 0) =
        Podcast(id, title, "author", null, genre, added, updated, order)

    private val podcasts = listOf(
        podcast(1, "beta", "Tech", updated = 30, order = 2),
        podcast(2, "Alpha", "News", updated = 10, order = 0),
        podcast(3, "Gamma", "tech", updated = 20, order = 1),
    )

    @Test
    fun `title sort is case insensitive`() {
        assertEquals(listOf("Alpha", "beta", "Gamma"), podcasts.sortPodcasts(SortOrder.TITLE).map { it.title })
    }

    @Test
    fun `recently updated sorts newest first`() {
        assertEquals(listOf(1L, 3L, 2L), podcasts.sortPodcasts(SortOrder.RECENTLY_UPDATED).map { it.id })
    }

    @Test
    fun `custom sort follows customOrder`() {
        assertEquals(listOf(2L, 3L, 1L), podcasts.sortPodcasts(SortOrder.CUSTOM).map { it.id })
    }

    @Test
    fun `groups are alphabetical and keep sort order inside`() {
        val groups = podcasts.groupByGenre(SortOrder.TITLE)
        assertEquals(listOf("News", "Tech", "tech"), groups.keys.toList())
        assertEquals(listOf("Alpha"), groups.getValue("News").map { it.title })
    }
}
