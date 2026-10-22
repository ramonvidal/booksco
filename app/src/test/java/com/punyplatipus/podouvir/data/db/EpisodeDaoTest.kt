package com.punyplatipus.podouvir.data.db

import android.database.sqlite.SQLiteConstraintException
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeDaoTest {
    private lateinit var db: PodOuvirDatabase
    private lateinit var podcasts: PodcastDao
    private lateinit var dao: EpisodeDao

    @Before fun setUp() {
        db = createDb()
        podcasts = db.podcastDao()
        dao = db.episodeDao()
    }

    @After fun tearDown() = db.close()

    private suspend fun newPodcast(url: String = "p1") = podcasts.insert(podcastEntity(feedUrl = url))

    @Test fun `insertAll returns row ids for new episodes`() = runTest {
        val p = newPodcast()
        val eps = listOf("a", "b", "c").map { episodeEntity(p, it) }
        val ids = dao.insertAll(eps)
        assertEquals(3, ids.size)
        assertTrue(ids.all { it > 0 })
        ids.forEachIndexed { i, id -> assertEquals(eps[i].copy(id = id), dao.getById(id)) }
    }

    @Test fun `insertAll skips duplicate guid in same podcast with -1`() = runTest {
        val p = newPodcast()
        val firstId = dao.insertAll(listOf(episodeEntity(p, "g1", title = "Original"))).single()
        val result = dao.insertAll(
            listOf(episodeEntity(p, "g1", title = "Changed"), episodeEntity(p, "g2")),
        )
        assertEquals(-1L, result[0])
        assertTrue(result[1] > 0)
        assertEquals("Original", dao.getById(firstId)!!.title)
    }

    @Test fun `insertAll allows same guid under another podcast`() = runTest {
        val p1 = newPodcast("p1")
        val p2 = newPodcast("p2")
        val id1 = dao.insertAll(listOf(episodeEntity(p1, "g1"))).single()
        val id2 = dao.insertAll(listOf(episodeEntity(p2, "g1"))).single()
        assertTrue(id2 > 0)
        assertNotNull(dao.getById(id1))
        assertNotNull(dao.getById(id2))
    }

    @Test fun `insertAll with empty list returns empty list`() = runTest {
        assertTrue(dao.insertAll(emptyList()).isEmpty())
    }

    @Test fun `insertAll with duplicate guid inside the same batch`() = runTest {
        val p = newPodcast()
        val ids = dao.insertAll(listOf(episodeEntity(p, "g"), episodeEntity(p, "g")))
        assertTrue(ids[0] > 0)
        assertEquals(-1L, ids[1])
        dao.observeForPodcast(p).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `insertAll fails for unknown podcastId`() = runTest {
        var thrown: Throwable? = null
        try {
            dao.insertAll(listOf(episodeEntity(999, "g")))
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("expected SQLiteConstraintException but was $thrown", thrown is SQLiteConstraintException)
    }

    @Test fun `getById returns null when missing`() = runTest {
        assertNull(dao.getById(42))
    }

    @Test fun `observeForPodcast orders newest first, null dates last, ties by id`() = runTest {
        val p = newPodcast()
        // insertion order defines ids ascending
        val specs = listOf(
            "d100" to 100L, "d300" to 300L, "d200" to 200L,
            "nullA" to null, "nullB" to null, "tieA" to 150L, "tieB" to 150L,
        )
        dao.insertAll(specs.map { (g, d) -> episodeEntity(p, g, publishedAt = d) })
        dao.observeForPodcast(p).test {
            val guids = awaitItem().map { it.guid }
            assertEquals(listOf("d300", "d200", "tieA", "tieB", "d100", "nullA", "nullB"), guids)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `observeForPodcast only returns episodes of that podcast`() = runTest {
        val p1 = newPodcast("p1")
        val p2 = newPodcast("p2")
        dao.insertAll(listOf(episodeEntity(p1, "a"), episodeEntity(p2, "b")))
        dao.observeForPodcast(p1).test {
            assertEquals(listOf("a"), awaitItem().map { it.guid })
            cancelAndIgnoreRemainingEvents()
        }
        dao.observeForPodcast(12345).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `observeForPodcast re-emits after insertAll`() = runTest {
        val p = newPodcast()
        dao.observeForPodcast(p).test {
            assertTrue(awaitItem().isEmpty())

            dao.insertAll(
                listOf(episodeEntity(p, "a", publishedAt = 10), episodeEntity(p, "b", publishedAt = 20)),
            )
            assertEquals(listOf("b", "a"), awaitItem().map { it.guid })

            dao.insertAll(
                listOf(episodeEntity(p, "a", publishedAt = 10), episodeEntity(p, "c", publishedAt = 30)),
            )
            assertEquals(listOf("c", "b", "a"), awaitItem().map { it.guid })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `episode with very long title and description round-trips`() = runTest {
        val p = newPodcast()
        val title = "t".repeat(100_000)
        val desc = "d".repeat(100_000)
        val id = dao.insertAll(listOf(episodeEntity(p, "g", title = title, description = desc))).single()
        val stored = dao.getById(id)!!
        assertEquals(title, stored.title)
        assertEquals(desc, stored.description)
    }

    @Test fun `nullable durationMs and publishedAt are stored as null`() = runTest {
        val p = newPodcast()
        val id = dao.insertAll(listOf(episodeEntity(p, "g", durationMs = null, publishedAt = null))).single()
        val stored = dao.getById(id)!!
        assertNull(stored.durationMs)
        assertNull(stored.publishedAt)
    }
}
