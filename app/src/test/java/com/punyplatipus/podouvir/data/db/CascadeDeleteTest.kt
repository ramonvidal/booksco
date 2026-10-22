package com.punyplatipus.podouvir.data.db

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CascadeDeleteTest {
    private lateinit var db: PodOuvirDatabase

    @Before fun setUp() {
        db = createDb()
    }

    @After fun tearDown() = db.close()

    /** Creates a podcast with two episodes that each have a playback state; returns podcast id and episode ids. */
    private suspend fun seed(url: String): Pair<Long, List<Long>> {
        val p = db.podcastDao().insert(podcastEntity(feedUrl = url))
        val eps = db.episodeDao().insertAll(listOf(episodeEntity(p, "a"), episodeEntity(p, "b")))
        eps.forEach { db.playbackStateDao().upsert(playbackState(it)) }
        return p to eps
    }

    @Test fun `deleting a podcast cascades to episodes and playback state`() = runTest {
        val (p, eps) = seed("p1")
        db.podcastDao().deleteById(p)
        eps.forEach {
            assertNull(db.episodeDao().getById(it))
            assertNull(db.playbackStateDao().get(it))
        }
    }

    @Test fun `deleting a podcast leaves other podcasts' data intact`() = runTest {
        val (p1, _) = seed("p1")
        val (p2, eps2) = seed("p2")
        db.podcastDao().deleteById(p1)
        assertNotNull(db.podcastDao().getById(p2))
        eps2.forEach {
            assertNotNull(db.episodeDao().getById(it))
            assertEquals(playbackState(it), db.playbackStateDao().get(it))
        }
    }
}
