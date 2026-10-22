package com.punyplatipus.podouvir.data.db

import android.database.sqlite.SQLiteConstraintException
import app.cash.turbine.test
import kotlinx.coroutines.flow.distinctUntilChanged
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
class PlaybackStateDaoTest {
    private lateinit var db: PodOuvirDatabase
    private lateinit var dao: PlaybackStateDao

    @Before fun setUp() {
        db = createDb()
        dao = db.playbackStateDao()
    }

    @After fun tearDown() = db.close()

    private suspend fun newEpisodes(count: Int = 1): List<Long> {
        val p = db.podcastDao().insert(podcastEntity())
        return db.episodeDao().insertAll((1..count).map { episodeEntity(p, "g$it") })
    }

    @Test fun `get and observe return null when no state`() = runTest {
        val e = newEpisodes().single()
        assertNull(dao.get(e))
        dao.observe(e).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `upsert inserts a new state`() = runTest {
        val e = newEpisodes().single()
        val s = playbackState(e)
        dao.upsert(s)
        assertEquals(s, dao.get(e))
    }

    @Test fun `upsert twice updates same episodeId`() = runTest {
        val e = newEpisodes().single()
        dao.upsert(playbackState(e))
        val s2 = playbackState(e, positionMs = 9_000, completed = true, updatedAt = 8_000)
        dao.upsert(s2)
        assertEquals(s2, dao.get(e))
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM playback_state WHERE episodeId = $e",
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test fun `observe emits null, then inserted, then updated state`() = runTest {
        val e = newEpisodes().single()
        dao.observe(e).test {
            assertNull(awaitItem())
            val s1 = playbackState(e, positionMs = 1)
            dao.upsert(s1)
            assertEquals(s1, awaitItem())
            val s2 = playbackState(e, positionMs = 2, completed = true)
            dao.upsert(s2)
            assertEquals(s2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `observe does not emit for other episodes' changes`() = runTest {
        val (e1, e2) = newEpisodes(2)
        // Room invalidates per table, so de-duplicate identical re-queries.
        dao.observe(e1).distinctUntilChanged().test {
            assertNull(awaitItem())
            dao.upsert(playbackState(e2))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `upsert fails for unknown episodeId`() = runTest {
        var thrown: Throwable? = null
        try {
            dao.upsert(playbackState(777))
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("expected SQLiteConstraintException but was $thrown", thrown is SQLiteConstraintException)
    }

    @Test fun `boundary values persist`() = runTest {
        val (e1, e2) = newEpisodes(2)
        val zero = playbackState(e1, positionMs = 0, completed = true)
        val max = playbackState(e2, positionMs = Long.MAX_VALUE, completed = true, updatedAt = Long.MAX_VALUE)
        dao.upsert(zero)
        dao.upsert(max)
        assertEquals(zero, dao.get(e1))
        assertEquals(max, dao.get(e2))
    }
}
