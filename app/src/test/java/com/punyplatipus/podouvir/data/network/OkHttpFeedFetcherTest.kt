package com.punyplatipus.podouvir.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class OkHttpFeedFetcherTest {
    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()
    private lateinit var fetcher: OkHttpFeedFetcher

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        fetcher = OkHttpFeedFetcher(OkHttpClient(), dispatcher)
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun url() = server.url("/feed.xml").toString()

    @Test fun `fetch passes body stream to block, returns its result and releases connection`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("abc"))
        server.enqueue(MockResponse().setBody("def"))
        assertEquals("abc", fetcher.fetch(url()) { it.readBytes().decodeToString() })
        assertEquals("def", fetcher.fetch(url()) { it.readBytes().decodeToString() })
        assertEquals(2, server.requestCount)
    }

    @Test fun `fetch throws IOException on non-2xx status without invoking block`() = runTest(dispatcher) {
        for (code in listOf(404, 500)) {
            server.enqueue(MockResponse().setResponseCode(code))
            var invoked = false
            try {
                fetcher.fetch(url()) { invoked = true }
                fail("expected IOException for $code")
            } catch (e: IOException) {
                // expected
            }
            assertFalse(invoked)
        }
    }

    @Test fun `fetch throws IOException on network failure`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            fetcher.fetch(url()) { it.readBytes() }
            fail("expected IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test fun `fetch throws IOException on invalid url`() = runTest(dispatcher) {
        try {
            fetcher.fetch("not a url") { it.readBytes() }
            fail("expected IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test fun `fetch propagates exception thrown by block and connection stays usable`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("abc"))
        server.enqueue(MockResponse().setBody("ok"))
        try {
            fetcher.fetch(url()) { throw IllegalStateException("boom") }
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        assertEquals("ok", fetcher.fetch(url()) { it.readBytes().decodeToString() })
    }

    @Test fun `fetch runs on injected io dispatcher`() = runTest {
        server.enqueue(MockResponse().setBody("abc"))
        val result = async(Dispatchers.Unconfined) { fetcher.fetch(url()) { it.readBytes().decodeToString() } }
        assertFalse(result.isCompleted)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(result.isCompleted)
        assertEquals("abc", result.await())
    }
}
