package com.punyplatipus.podouvir.data.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

internal fun loadFixture(name: String): String =
    requireNotNull(object {}.javaClass.classLoader!!.getResource("itunes/$name")) { "missing $name" }.readText()

class ItunesApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ItunesApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = createItunesApi(server.url("/").toString(), OkHttpClient())
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun enqueueFixture(name: String) {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/javascript; charset=utf-8")
                .setBody(loadFixture(name)),
        )
    }

    @Test
    fun `sends GET to search path with default params`() = runTest {
        enqueueFixture("search_ok.json")
        api.search("kotlin")
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/search", req.requestUrl!!.encodedPath)
        assertEquals("kotlin", req.requestUrl!!.queryParameter("term"))
        assertEquals("podcast", req.requestUrl!!.queryParameter("media"))
        assertEquals("podcast", req.requestUrl!!.queryParameter("entity"))
        assertEquals("25", req.requestUrl!!.queryParameter("limit"))
    }

    @Test
    fun `url-encodes term with spaces and special characters`() = runTest {
        enqueueFixture("search_ok.json")
        val term = "foo bar & baz"
        api.search(term)
        val req = server.takeRequest()
        val line = req.requestLine
        assertTrue(line, line.contains("foo%20bar") || line.contains("foo+bar"))
        assertTrue(line, line.contains("%26"))
        assertEquals(term, req.requestUrl!!.queryParameter("term"))
    }

    @Test
    fun `passes custom limit`() = runTest {
        enqueueFixture("search_ok.json")
        api.search("x", limit = 5)
        assertEquals("5", server.takeRequest().requestUrl!!.queryParameter("limit"))
    }

    @Test
    fun `parses text-javascript content type`() = runTest {
        enqueueFixture("search_ok.json")
        val r = api.search("kotlin")
        assertEquals(3, r.resultCount)
        assertEquals(3, r.results.size)
    }

    @Test
    fun `ignores unknown fields in results`() = runTest {
        enqueueFixture("search_ok.json")
        val r = api.search("kotlin")
        assertEquals("Kotlin Talks", r.results.first().collectionName)
    }

    @Test
    fun `parses empty results`() = runTest {
        enqueueFixture("search_empty.json")
        val r = api.search("nothing")
        assertEquals(0, r.resultCount)
        assertTrue(r.results.isEmpty())
    }

    @Test
    fun `http 500 throws HttpException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            api.search("x")
            fail("expected HttpException")
        } catch (e: HttpException) {
            assertEquals(500, e.code())
        }
    }

    @Test
    fun `malformed json throws`() = runTest {
        enqueueFixture("search_malformed.json")
        try {
            api.search("x")
            fail("expected exception")
        } catch (e: SerializationException) {
            // expected
        } catch (e: IOException) {
            // acceptable wrapping by the converter stack
        }
    }

    @Test
    fun `connection failure throws IOException`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            api.search("x")
            fail("expected IOException")
        } catch (e: IOException) {
            // expected
        }
    }
}
