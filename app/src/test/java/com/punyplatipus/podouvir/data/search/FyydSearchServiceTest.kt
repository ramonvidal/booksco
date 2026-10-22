package com.punyplatipus.podouvir.data.search

import com.punyplatipus.podouvir.data.network.NetworkJson
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import com.punyplatipus.podouvir.testutil.loadFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FyydSearchServiceTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun TestScope.newService(): FyydSearchService {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FyydApi::class.java)
        return FyydSearchService(api, StandardTestDispatcher(testScheduler))
    }

    private fun enqueueFixture(name: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(loadFixture(name)))
    }

    @Test fun `search sends GET to 0_2 search podcast with encoded term and count`() = runTest {
        enqueueFixture("search/fyyd_ok.json")
        enqueueFixture("search/fyyd_ok.json")
        val service = newService()
        service.search("  c++ & café  ", limit = 10)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/0.2/search/podcast", req.requestUrl!!.encodedPath)
        assertEquals("c++ & café", req.requestUrl!!.queryParameter("term"))
        assertEquals("10", req.requestUrl!!.queryParameter("count"))
        val rawQuery = req.requestUrl!!.encodedQuery!!
        assertTrue("raw & must be encoded: $rawQuery", rawQuery.count { it == '&' } == 1)

        service.search("x")
        assertEquals("25", server.takeRequest().requestUrl!!.queryParameter("count"))
    }

    @Test fun `fyydGenreName maps every category id to its top-level name`() {
        val expected = mapOf(
            100 to "Arts", 107 to "Arts", 102 to "Arts", 108 to "Business", 203 to "Technology",
            204 to "True Crime", 205 to "TV & Film", 210 to "TV & Film",
        )
        for ((id, name) in expected) assertEquals("id $id", name, fyydGenreName(id))
        for (id in listOf(99, 211, 0, -1, Int.MAX_VALUE)) assertNull("id $id", fyydGenreName(id))
    }

    @Test fun `toSearchResultOrNull maps dto and uses first category for genre`() {
        val full = FyydPodcastDto("T", "https://f/x", "https://f/i.jpg", "Au", listOf(203, 100))
        assertEquals(
            PodcastSearchResult("https://f/x", "T", "Au", "https://f/i.jpg", "Technology"),
            full.toSearchResultOrNull(),
        )
        assertEquals("", FyydPodcastDto("T", "https://f/x", categories = emptyList()).toSearchResultOrNull()!!.genre)
        assertEquals("", FyydPodcastDto("T", "https://f/x", categories = listOf(999, 203)).toSearchResultOrNull()!!.genre)
        val bare = FyydPodcastDto("T", "https://f/x", imgURL = null, author = null).toSearchResultOrNull()!!
        assertNull(bare.artworkUrl)
        assertEquals("", bare.author)
        assertNull(FyydPodcastDto(title = " ", xmlURL = "https://f/x").toSearchResultOrNull())
        assertNull(FyydPodcastDto(title = null, xmlURL = "https://f/x").toSearchResultOrNull())
        assertNull(FyydPodcastDto(title = "T", xmlURL = null).toSearchResultOrNull())
    }

    @Test fun `search drops results without feed url or title, dedupes by feedUrl and short-circuits blank terms`() =
        runTest {
            enqueueFixture("search/fyyd_ok.json")
            val service = newService()
            val results = service.search("x")
            assertEquals(listOf("https://fyyd.example/full.xml", "https://fyyd.example/bare.xml"), results.map { it.feedUrl })
            assertEquals("Full Show", results[0].title)
            assertEquals("Alice", results[0].author)
            assertEquals("Technology", results[0].genre)
            assertNull(results[1].artworkUrl)

            val before = server.requestCount
            assertEquals(emptyList<PodcastSearchResult>(), service.search(""))
            assertEquals(emptyList<PodcastSearchResult>(), service.search("   "))
            assertEquals(before, server.requestCount)
        }

    @Test fun `search returns empty for empty data and throws on http error or malformed json`() = runTest {
        val service = newService()
        enqueueFixture("search/fyyd_empty.json")
        assertEquals(emptyList<PodcastSearchResult>(), service.search("x"))

        server.enqueue(MockResponse().setResponseCode(500))
        try {
            service.search("x"); fail("expected HttpException")
        } catch (e: HttpException) {
            assertEquals(500, e.code())
        }

        enqueueFixture("search/fyyd_malformed.json")
        try {
            service.search("x"); fail("expected SerializationException")
        } catch (e: SerializationException) {
            // expected
        }

        server.shutdown()
        try {
            service.search("x"); fail("expected IOException")
        } catch (e: IOException) {
            // expected
        }
    }
}
