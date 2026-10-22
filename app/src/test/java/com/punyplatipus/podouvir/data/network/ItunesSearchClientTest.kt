package com.punyplatipus.podouvir.data.network

import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ItunesSearchClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun TestScope.newClient() = ItunesSearchClient(
        createItunesApi(server.url("/").toString(), OkHttpClient()),
        StandardTestDispatcher(testScheduler),
    )

    private fun enqueueFixture(name: String) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/javascript; charset=utf-8")
                .setBody(loadFixture(name)),
        )
    }

    @Test
    fun `returns mapped results for valid response`() = runTest {
        enqueueFixture("search_ok.json")
        val result = newClient().search("kotlin")
        assertEquals(
            listOf(
                PodcastSearchResult("https://example.com/kotlin.xml", "Kotlin Talks", "Ana", "https://img.example.com/kotlin600.jpg", "Technology"),
                PodcastSearchResult("https://example.com/small.xml", "Only Small Art", "Bob", "https://img.example.com/small100.jpg", "News"),
                PodcastSearchResult("https://example.com/both.xml", "Both Art", "Cy", "https://img.example.com/both600.jpg", "Comedy"),
            ),
            result,
        )
    }

    @Test
    fun `trims term before request`() = runTest {
        enqueueFixture("search_ok.json")
        newClient().search("  kotlin  ")
        assertEquals("kotlin", server.takeRequest().requestUrl!!.queryParameter("term"))
    }

    @Test
    fun `blank term returns empty list without network call`() = runTest {
        val client = newClient()
        for (t in listOf("", "   ", "\t\n")) {
            assertEquals(emptyList<PodcastSearchResult>(), client.search(t))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `drops results missing feedUrl or collectionName`() = runTest {
        enqueueFixture("search_mixed.json")
        val titles = newClient().search("x").map { it.title }
        assertEquals(listOf("Good One", "Dup First", "Good Two"), titles)
    }

    @Test
    fun `deduplicates by feedUrl keeping first`() = runTest {
        enqueueFixture("search_mixed.json")
        val dups = newClient().search("x").filter { it.feedUrl == "https://example.com/dup.xml" }
        assertEquals(1, dups.size)
        assertEquals("Dup First", dups.single().title)
        assertEquals("D", dups.single().author)
    }

    @Test
    fun `returns empty list when API returns no results`() = runTest {
        enqueueFixture("search_empty.json")
        assertEquals(emptyList<PodcastSearchResult>(), newClient().search("x"))
    }

    @Test
    fun `forwards limit to the API`() = runTest {
        enqueueFixture("search_ok.json")
        newClient().search("x", limit = 10)
        assertEquals("10", server.takeRequest().requestUrl!!.queryParameter("limit"))
    }

    @Test
    fun `http error propagates`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            newClient().search("x")
            fail("expected HttpException")
        } catch (e: HttpException) {
            assertEquals(500, e.code())
        }
    }

    @Test
    fun `io failure propagates`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            newClient().search("x")
            fail("expected IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test
    fun `runs on injected io dispatcher`() = runTest {
        enqueueFixture("search_ok.json")
        val client = newClient()
        val deferred = async(StandardTestDispatcher(testScheduler)) { client.search("kotlin") }
        // Nothing has been advanced yet: no request may have been made.
        assertEquals(0, server.requestCount)
        runCurrent()
        advanceUntilIdle()
        assertEquals(3, deferred.await().size)
        assertEquals(1, server.requestCount)
    }
}
