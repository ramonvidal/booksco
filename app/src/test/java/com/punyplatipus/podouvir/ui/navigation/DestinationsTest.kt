package com.punyplatipus.podouvir.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationsTest {

    @Test
    fun `tabs are ordered library discover queue`() {
        assertEquals(
            listOf(TopLevelTab.LIBRARY, TopLevelTab.DISCOVER, TopLevelTab.QUEUE),
            TopLevelTab.entries,
        )
    }

    @Test
    fun `each tab maps to its destination object`() {
        assertSame(LibraryDestination, TopLevelTab.LIBRARY.destination)
        assertSame(DiscoverDestination, TopLevelTab.DISCOVER.destination)
        assertSame(QueueDestination, TopLevelTab.QUEUE.destination)
        val destinations = TopLevelTab.entries.map { it.destination }
        assertEquals(destinations.size, destinations.toSet().size)
    }

    @Test
    fun `tabs have distinct non-zero label resources`() {
        val labels = TopLevelTab.entries.map { it.labelRes }
        assertTrue(labels.all { it != 0 })
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `podcast destination is not a top-level tab`() {
        assertFalse(PodcastDestination(1) in TopLevelTab.entries.map { it.destination })
    }

    @Test
    fun `podcast destination keeps its id and has value equality`() {
        val a = PodcastDestination(42)
        val b = PodcastDestination(42)
        assertEquals(42L, a.podcastId)
        assertEquals(a, b)
        assertNotSame(a, b)
        assertNotEquals(a, PodcastDestination(7))
    }
}
