package com.punyplatipus.podouvir.data.repository

import app.cash.turbine.test
import com.punyplatipus.podouvir.data.db.PlaybackStateEntity
import com.punyplatipus.podouvir.data.db.PodOuvirDatabase
import com.punyplatipus.podouvir.data.db.createDb
import com.punyplatipus.podouvir.data.db.podcastEntity
import com.punyplatipus.podouvir.data.db.toDomain
import com.punyplatipus.podouvir.data.network.FeedFetcher
import com.punyplatipus.podouvir.data.network.ItunesApi
import com.punyplatipus.podouvir.data.network.ItunesPodcastDto
import com.punyplatipus.podouvir.data.network.ItunesSearchClient
import com.punyplatipus.podouvir.data.network.ItunesSearchResponse
import com.punyplatipus.podouvir.data.rss.FeedParseException
import com.punyplatipus.podouvir.data.rss.RssParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FakeFeedFetcher : FeedFetcher {
    /** url -> fixture resource path (String) or Throwable. */
    val responses = mutableMapOf<String, Any>()
    val requested = mutableListOf<String>()

    override suspend fun <T> fetch(url: String, block: (InputStream) -> T): T {
        requested += url
        when (val r = responses[url] ?: throw IOException("no response for $url")) {
            is Throwable -> throw r
            is String -> {
                val stream = requireNotNull(javaClass.classLoader!!.getResourceAsStream(r)) { "missing $r" }
                return stream.use(block)
            }
            else -> error("bad response")
        }
    }
}

class FakeItunesApi : ItunesApi {
    var results: List<ItunesPodcastDto> = emptyList()
    var error: Throwable? = null
    override suspend fun search(term: String, media: String, entity: String, limit: Int): ItunesSearchResponse {
        error?.let { throw it }
        return ItunesSearchResponse(results.size, results)
    }
}

class MutableClock(var now: Long) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultPodcastRepositoryTest {
    private val url = "https://example.com/feed.xml"
    private val simple = "rss/simple_feed.xml"
    private val plusOne = "rss/simple_feed_plus_one.xml"

    private lateinit var db: PodOuvirDatabase
    private lateinit var fetcher: FakeFeedFetcher
    private lateinit var itunes: FakeItunesApi
    private lateinit var clock: MutableClock
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: DefaultPodcastRepository

    private fun newRepo(io: kotlinx.coroutines.CoroutineDispatcher = dispatcher) = DefaultPodcastRepository(
        db.podcastDao(), db.episodeDao(), ItunesSearchClient(itunes, io), fetcher, RssParser(), clock, io,
    )

    @Before fun setUp() {
        db = createDb()
        fetcher = FakeFeedFetcher()
        itunes = FakeItunesApi()
        clock = MutableClock(10_000L)
        repo = newRepo()
        fetcher.responses[url] = simple
    }

    @After fun tearDown() = db.close()

    private suspend fun subscribeOk(u: String = url, fallback: String? = null): Long =
        repo.subscribe(u, fallback).getOrThrow()

    // ---- subscribe ----

    @Test fun `subscribe stores podcast and episodes and returns id`() = runTest(dispatcher) {
        val id = subscribeOk()
        val podcasts = db.podcastDao().getAll()
        assertEquals(1, podcasts.size)
        assertEquals(id, podcasts[0].id)
        assertEquals("Simple Show", podcasts[0].title)
        val eps = repo.observeEpisodes(id).first()
        assertEquals(setOf("guid-1", "guid-2", "guid-3"), eps.map { it.guid }.toSet())
    }

    @Test fun `subscribe trims feedUrl`() = runTest(dispatcher) {
        val id = subscribeOk("  $url \n")
        assertEquals(listOf(url), fetcher.requested)
        assertEquals(url, db.podcastDao().getById(id)!!.feedUrl)
    }

    @Test fun `subscribe rejects non http(s) url without network call`() = runTest(dispatcher) {
        for (bad in listOf("ftp://x", "example.com/feed", "", "   ")) {
            val r = repo.subscribe(bad)
            assertTrue("expected failure for '$bad'", r.isFailure)
            assertTrue(r.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(0, fetcher.requested.size)
        assertTrue(db.podcastDao().getAll().isEmpty())
    }

    @Test fun `subscribe genre is feed genre, else fallback, else empty`() = runTest(dispatcher) {
        val a = subscribeOk(fallback = "News")
        assertEquals("Technology", db.podcastDao().getById(a)!!.genre)

        fetcher.responses["https://b.example/f"] = "rss/no_genre_feed.xml"
        val b = subscribeOk("https://b.example/f", "News")
        assertEquals("News", db.podcastDao().getById(b)!!.genre)

        fetcher.responses["https://c.example/f"] = "rss/no_genre_feed.xml"
        val c = subscribeOk("https://c.example/f", null)
        assertEquals("", db.podcastDao().getById(c)!!.genre)
    }

    @Test fun `subscribe sets addedAt and lastUpdated from clock`() = runTest(dispatcher) {
        clock.now = 123_456L
        val p = db.podcastDao().getById(subscribeOk())!!
        assertEquals(123_456L, p.addedAt)
        assertEquals(123_456L, p.lastUpdated)
        assertNull(p.genreOverride)
    }

    @Test fun `subscribe assigns customOrder 0 first and max plus one afterwards`() = runTest(dispatcher) {
        val first = subscribeOk()
        assertEquals(0, db.podcastDao().getById(first)!!.customOrder)
        val dao = db.podcastDao()
        dao.insert(podcastEntity(feedUrl = "https://x/1", customOrder = 5))
        fetcher.responses["https://new.example/f"] = simple
        val id = subscribeOk("https://new.example/f")
        assertEquals(6, dao.getById(id)!!.customOrder)
    }

    @Test fun `subscribe on already subscribed url returns existing id without download`() = runTest(dispatcher) {
        val id = subscribeOk()
        val again = repo.subscribe("  $url  ")
        assertEquals(id, again.getOrThrow())
        assertEquals(1, fetcher.requested.size)
        assertEquals(1, db.podcastDao().getAll().size)
        assertEquals(3, db.episodeDao().observeForPodcast(id).first().size)
    }

    @Test fun `subscribe returns IOException failure on network error and writes nothing`() = runTest(dispatcher) {
        fetcher.responses[url] = IOException("down")
        val r = repo.subscribe(url)
        assertTrue(r.exceptionOrNull() is IOException)
        assertTrue(db.podcastDao().getAll().isEmpty())
    }

    @Test fun `subscribe returns failure on FeedParseException and writes nothing`() = runTest(dispatcher) {
        fetcher.responses[url] = "rss/malformed.xml"
        val r = repo.subscribe(url)
        assertTrue("was ${r.exceptionOrNull()}", r.exceptionOrNull() is FeedParseException)
        assertTrue(db.podcastDao().getAll().isEmpty())
    }

    @Test fun `subscribe rethrows CancellationException`() = runTest(dispatcher) {
        fetcher.responses[url] = CancellationException("cancelled")
        try {
            repo.subscribe(url)
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
    }

    @Test fun `subscribe does work on injected io dispatcher`() = runTest {
        val recording = com.punyplatipus.podouvir.data.network.RecordingDispatcher()
        val r = newRepo(recording)
        assertTrue(r.subscribe(url).isSuccess)
        assertTrue(recording.used)
    }

    // ---- unsubscribe ----

    @Test fun `unsubscribe removes podcast and cascades episodes`() = runTest(dispatcher) {
        val id = subscribeOk()
        repo.unsubscribe(id)
        assertNull(db.podcastDao().getById(id))
        repo.observeEpisodes(id).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `unsubscribe unknown id is a no-op and leaves other podcasts intact`() = runTest(dispatcher) {
        val a = subscribeOk()
        fetcher.responses["https://b.example/f"] = plusOne
        val b = subscribeOk("https://b.example/f")
        repo.unsubscribe(999)
        repo.unsubscribe(a)
        assertEquals(listOf(b), db.podcastDao().getAll().map { it.id })
        repo.observeEpisodes(b).test {
            assertEquals(4, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- refresh ----

    @Test fun `refresh inserts only new episodes, returns count, and second refresh returns 0`() = runTest(dispatcher) {
        val id = subscribeOk()
        fetcher.responses[url] = plusOne
        assertEquals(1, repo.refresh(id).getOrThrow())
        assertEquals(0, repo.refresh(id).getOrThrow())
        repo.observeEpisodes(id).test {
            val eps = awaitItem()
            assertEquals(4, eps.size)
            assertEquals(4, eps.map { it.guid }.toSet().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `refresh updates title author description artwork genre`() = runTest(dispatcher) {
        val id = subscribeOk()
        fetcher.responses[url] = plusOne
        repo.refresh(id).getOrThrow()
        val p = db.podcastDao().getById(id)!!
        assertEquals("Simple Show Renamed", p.title)
        assertEquals("John Host", p.author)
        assertEquals("Updated description.", p.description)
        assertEquals("https://example.com/art2.jpg", p.artworkUrl)
        assertEquals("Science", p.genre)
    }

    @Test fun `refresh keeps stored genre when feed genre is blank`() = runTest(dispatcher) {
        fetcher.responses[url] = "rss/no_genre_feed.xml"
        val id = subscribeOk(fallback = "Tech")
        assertEquals("Tech", db.podcastDao().getById(id)!!.genre)
        repo.refresh(id).getOrThrow()
        assertEquals("Tech", db.podcastDao().getById(id)!!.genre)
    }

    @Test fun `refresh never changes genreOverride addedAt customOrder`() = runTest(dispatcher) {
        clock.now = 1_000L
        val id = subscribeOk()
        db.podcastDao().update(db.podcastDao().getById(id)!!.copy(customOrder = 7))
        repo.setGenreOverride(id, "Comedy")
        val before = db.podcastDao().getById(id)!!
        clock.now = 5_000L
        fetcher.responses[url] = plusOne
        repo.refresh(id).getOrThrow()
        val after = db.podcastDao().getById(id)!!
        assertEquals("Comedy", after.genreOverride)
        assertEquals(before.addedAt, after.addedAt)
        assertEquals(before.customOrder, after.customOrder)
        assertEquals(1_000L, after.addedAt)
        assertEquals(7, after.customOrder)
    }

    @Test fun `refresh updates lastUpdated only when new episodes arrive`() = runTest(dispatcher) {
        clock.now = 1_000L
        val id = subscribeOk()
        clock.now = 2_000L
        fetcher.responses[url] = plusOne
        repo.refresh(id).getOrThrow()
        assertEquals(2_000L, db.podcastDao().getById(id)!!.lastUpdated)
        clock.now = 3_000L
        repo.refresh(id).getOrThrow()
        assertEquals(2_000L, db.podcastDao().getById(id)!!.lastUpdated)
    }

    @Test fun `refresh preserves existing episode state`() = runTest(dispatcher) {
        val id = subscribeOk()
        val ep = repo.observeEpisodes(id).first().first()
        db.playbackStateDao().upsert(PlaybackStateEntity(ep.id, 4_000L, true, 9L))
        fetcher.responses[url] = plusOne
        repo.refresh(id).getOrThrow()
        val state = db.playbackStateDao().get(ep.id)
        assertNotNull(state)
        assertEquals(4_000L, state!!.positionMs)
        assertTrue(state.completed)
        assertEquals(ep, db.episodeDao().getById(ep.id)!!.let { it.toDomain() })
    }

    @Test fun `refresh unknown id returns NoSuchElementException failure without download`() = runTest(dispatcher) {
        val r = repo.refresh(42)
        assertTrue(r.exceptionOrNull() is NoSuchElementException)
        assertEquals(0, fetcher.requested.size)
    }

    @Test fun `refresh network error returns IOException failure and changes nothing`() = runTest(dispatcher) {
        val id = subscribeOk()
        val before = db.podcastDao().getById(id)
        fetcher.responses[url] = IOException("down")
        assertTrue(repo.refresh(id).exceptionOrNull() is IOException)
        assertEquals(before, db.podcastDao().getById(id))
    }

    @Test fun `refresh parse error returns failure and changes nothing`() = runTest(dispatcher) {
        val id = subscribeOk()
        val before = db.podcastDao().getById(id)
        fetcher.responses[url] = "rss/malformed.xml"
        assertTrue(repo.refresh(id).exceptionOrNull() is FeedParseException)
        assertEquals(before, db.podcastDao().getById(id))
    }

    @Test fun `refresh rethrows CancellationException`() = runTest(dispatcher) {
        val id = subscribeOk()
        fetcher.responses[url] = CancellationException("cancelled")
        try {
            repo.refresh(id)
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
    }

    // ---- search ----

    @Test fun `search returns client results wrapped in success, empty for none`() = runTest(dispatcher) {
        itunes.results = listOf(
            ItunesPodcastDto("A", "Au", "https://a/f", null, null, "News"),
            ItunesPodcastDto("B", "Bu", "https://b/f", null, null, "Tech"),
        )
        val r = repo.search("kotlin").getOrThrow()
        assertEquals(listOf("https://a/f", "https://b/f"), r.map { it.feedUrl })
        itunes.results = emptyList()
        assertTrue(repo.search("kotlin").getOrThrow().isEmpty())
    }

    @Test fun `search wraps exceptions in failure`() = runTest(dispatcher) {
        itunes.error = IOException("offline")
        assertTrue(repo.search("kotlin").exceptionOrNull() is IOException)
    }

    @Test fun `search rethrows CancellationException`() = runTest(dispatcher) {
        itunes.error = CancellationException("cancelled")
        try {
            repo.search("kotlin")
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
    }

    // ---- setGenreOverride ----

    @Test fun `setGenreOverride stores value, null and blank clear it`() = runTest(dispatcher) {
        val id = subscribeOk()
        repo.setGenreOverride(id, "Comedy")
        assertEquals("Comedy", db.podcastDao().getById(id)!!.genreOverride)
        repo.setGenreOverride(id, null)
        assertNull(db.podcastDao().getById(id)!!.genreOverride)
        repo.setGenreOverride(id, "Comedy")
        repo.setGenreOverride(id, "   ")
        assertNull(db.podcastDao().getById(id)!!.genreOverride)
    }

    // ---- observe ----

    @Test fun `observeLibrary emits empty, then re-emits on subscribe, override change and unsubscribe`() = runTest(dispatcher) {
        repo.observeLibrary().test {
            assertTrue(awaitItem().isEmpty())
            val id = subscribeOk()
            val added = awaitItem()
            assertEquals(1, added.size)
            assertEquals(id, added[0].id)
            assertEquals("Simple Show", added[0].title)
            assertNull(added[0].genreOverride)
            repo.setGenreOverride(id, "Comedy")
            assertEquals("Comedy", awaitItem()[0].genreOverride)
            repo.unsubscribe(id)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `observeEpisodes emits mapped episodes, re-emits after refresh, empty for unknown id`() = runTest(dispatcher) {
        repo.observeEpisodes(999).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        val id = subscribeOk()
        repo.observeEpisodes(id).test {
            val first = awaitItem()
            assertEquals(listOf("guid-1", "guid-2", "guid-3"), first.map { it.guid })
            assertEquals(listOf("Episode One", "Episode Two", "Episode Three"), first.map { it.title })
            assertTrue(first.all { it.podcastId == id })
            fetcher.responses[url] = plusOne
            repo.refresh(id).getOrThrow()
            val second = awaitItem()
            assertEquals(4, second.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
