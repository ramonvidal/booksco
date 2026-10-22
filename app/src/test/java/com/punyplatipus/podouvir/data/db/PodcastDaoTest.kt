package com.punyplatipus.podouvir.data.db

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastDaoTest {
    private lateinit var db: PodOuvirDatabase
    private lateinit var dao: PodcastDao

    @Before fun setUp() {
        db = createDb()
        dao = db.podcastDao()
    }

    @After fun tearDown() = db.close()

    @Test fun `insert returns generated id for new podcast`() = runTest {
        val p = podcastEntity()
        val id = dao.insert(p)
        assertTrue(id > 0)
        assertEquals(p.copy(id = id), dao.getById(id))
    }

    @Test fun `insert with existing feedUrl returns -1 and keeps existing row`() = runTest {
        val a = podcastEntity(feedUrl = "u", title = "A")
        val id = dao.insert(a)
        val result = dao.insert(podcastEntity(feedUrl = "u", title = "B"))
        assertEquals(-1L, result)
        assertEquals(a.copy(id = id), dao.getByFeedUrl("u"))
        assertEquals(1, dao.getAll().size)
    }

    @Test fun `getById and getByFeedUrl return null when missing`() = runTest {
        assertNull(dao.getById(123))
        assertNull(dao.getByFeedUrl("nope"))
    }

    @Test fun `getAll returns every podcast`() = runTest {
        assertTrue(dao.getAll().isEmpty())
        repeat(3) { dao.insert(podcastEntity(feedUrl = "u$it")) }
        assertEquals(3, dao.getAll().size)
    }

    @Test fun `update persists changed fields`() = runTest {
        val id = dao.insert(podcastEntity())
        val changed = dao.getById(id)!!.copy(title = "New", lastUpdated = 99L, customOrder = 7)
        dao.update(changed)
        assertEquals(changed, dao.getById(id))
    }

    @Test fun `deleteById removes only that podcast`() = runTest {
        val first = dao.insert(podcastEntity(feedUrl = "a"))
        val second = dao.insert(podcastEntity(feedUrl = "b"))
        dao.deleteById(first)
        assertNull(dao.getById(first))
        assertEquals(second, dao.getById(second)!!.id)
        dao.deleteById(9999)
        assertEquals(1, dao.getAll().size)
    }

    @Test fun `setGenreOverride sets then clears override`() = runTest {
        val id = dao.insert(podcastEntity(genre = "News", genreOverride = null))
        dao.setGenreOverride(id, "Comedy")
        assertEquals("Comedy", dao.getById(id)!!.genreOverride)
        assertEquals("News", dao.getById(id)!!.genre)
        dao.setGenreOverride(id, null)
        assertNull(dao.getById(id)!!.genreOverride)
        assertEquals("News", dao.getById(id)!!.genre)
    }

    @Test fun `maxCustomOrder is null when empty and max otherwise`() = runTest {
        assertNull(dao.maxCustomOrder())
        dao.insert(podcastEntity(feedUrl = "a", customOrder = 0))
        dao.insert(podcastEntity(feedUrl = "b", customOrder = 5))
        dao.insert(podcastEntity(feedUrl = "c", customOrder = 2))
        assertEquals(5, dao.maxCustomOrder())
    }

    @Test
    fun `observeAll emits initial then re-emits after insert, update, delete and setGenreOverride`() = runTest {
        dao.observeAll().test {
            assertTrue(awaitItem().isEmpty())

            val id = dao.insert(podcastEntity())
            val p = podcastEntity(id = id)
            assertEquals(listOf(p), awaitItem())

            val updated = p.copy(title = "Updated")
            dao.update(updated)
            assertEquals(listOf(updated), awaitItem())

            dao.setGenreOverride(id, "X")
            assertEquals("X", awaitItem().single().genreOverride)

            dao.setGenreOverride(id, null)
            assertNull(awaitItem().single().genreOverride)

            dao.deleteById(id)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `observeAll emits empty list for empty DB`() = runTest {
        dao.observeAll().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- reorder / setCustomOrder ----

    private suspend fun insertThree(orders: Triple<Int, Int, Int> = Triple(10, 20, 30)): Triple<Long, Long, Long> {
        val a = dao.insert(podcastEntity(feedUrl = "a", customOrder = orders.first))
        val b = dao.insert(podcastEntity(feedUrl = "b", customOrder = orders.second))
        val c = dao.insert(podcastEntity(feedUrl = "c", customOrder = orders.third))
        return Triple(a, b, c)
    }

    private suspend fun order(id: Long) = dao.getById(id)!!.customOrder

    @Test fun `reorder assigns each id its list index`() = runTest {
        val (a, b, c) = insertThree()
        dao.reorder(listOf(c, a, b))
        assertEquals(0, order(c))
        assertEquals(1, order(a))
        assertEquals(2, order(b))
    }

    @Test fun `reorder leaves ids not in the list unchanged`() = runTest {
        val (a, b, c) = insertThree()
        dao.reorder(listOf(b, a))
        assertEquals(0, order(b))
        assertEquals(1, order(a))
        assertEquals(30, order(c))
    }

    @Test fun `reorder ignores unknown ids and does not throw`() = runTest {
        val a = dao.insert(podcastEntity(feedUrl = "a", customOrder = 10))
        val b = dao.insert(podcastEntity(feedUrl = "b", customOrder = 20))
        dao.reorder(listOf(a, 999L, b))
        assertEquals(0, order(a))
        assertEquals(2, order(b))
        assertNull(dao.getById(999L))
        assertEquals(2, dao.getAll().size)
    }

    @Test fun `reorder with empty list is a no-op`() = runTest {
        val (a, b, c) = insertThree()
        dao.reorder(emptyList())
        assertEquals(listOf(10, 20, 30), listOf(order(a), order(b), order(c)))
    }

    @Test fun `setCustomOrder updates only the target row`() = runTest {
        val (a, b, _) = insertThree()
        dao.setCustomOrder(a, 7)
        assertEquals(7, order(a))
        assertEquals(20, order(b))
    }
}
