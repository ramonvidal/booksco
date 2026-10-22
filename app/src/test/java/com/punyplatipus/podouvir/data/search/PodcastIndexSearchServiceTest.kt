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
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastIndexSearchServiceTest {
    private lateinit var server: MockWebServer
    private val clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC)
    private val creds = PodcastIndexCredentials("key", "secret")

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun TestScope.newService(credentials: PodcastIndexCredentials = creds): PodcastIndexSearchService {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PodcastIndexApi::class.java)
        return PodcastIndexSearchService(api, credentials, StandardTestDispatcher(testScheduler))
    }

    private fun enqueueFixture(name: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(loadFixture(name)))
    }

    private fun sha1Hex(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test fun `podcastIndexAuthorization returns lowercase hex sha1 of key secret and date`() {
        val actual = podcastIndexAuthorization("K", "S", 1_700_000_000)
        assertEquals(sha1Hex("KS1700000000"), actual)
        assertEquals(40, actual.length)
        assertTrue(actual.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(actual, podcastIndexAuthorization("K2", "S", 1_700_000_000))
        assertNotEquals(actual, podcastIndexAuthorization("K", "S2", 1_700_000_000))
        assertNotEquals(actual, podcastIndexAuthorization("K", "S", 1_700_000_001))
        // always 40 chars even when the digest has leading zero bytes
        for (i in 0L..3000L) {
            assertEquals(40, podcastIndexAuthorization("K", "S", i).length)
        }
    }

    @Test fun `auth interceptor adds X-Auth-Key, X-Auth-Date and Authorization from the clock`() {
        server.enqueue(MockResponse().setBody("{}"))
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "PodOuvir/test").build())
            }
            .addInterceptor(PodcastIndexAuthInterceptor(creds, clock))
            .build()
        client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        val req = server.takeRequest()
        assertEquals("key", req.getHeader("X-Auth-Key"))
        assertEquals("1700000000", req.getHeader("X-Auth-Date"))
        assertEquals(sha1Hex("keysecret1700000000"), req.getHeader("Authorization"))
        assertEquals("PodOuvir/test", req.getHeader("User-Agent"))
    }

    @Test fun `search sends GET to api 1_0 search byterm and maps feeds`() = runTest {
        enqueueFixture("search/podcastindex_ok.json")
        val service = newService()
        val results = service.search(" a b ", limit = 7)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/api/1.0/search/byterm", req.requestUrl!!.encodedPath)
        assertEquals("a b", req.requestUrl!!.queryParameter("q"))
        assertEquals("7", req.requestUrl!!.queryParameter("max"))
        assertFalse(req.requestUrl!!.encodedQuery!!.contains(' '))

        assertEquals(
            listOf(
                "https://pi.example/tech.xml", "https://pi.example/nocats.xml",
                "https://pi.example/blank.xml", "https://pi.example/noart.xml",
            ),
            results.map { it.feedUrl },
        )
        val byUrl = results.associateBy { it.feedUrl }
        assertEquals("Technology", byUrl["https://pi.example/tech.xml"]!!.genre)
        assertEquals("https://pi.example/a.jpg", byUrl["https://pi.example/tech.xml"]!!.artworkUrl)
        assertEquals("Carol", byUrl["https://pi.example/tech.xml"]!!.author)
        assertEquals("Tech Show", byUrl["https://pi.example/tech.xml"]!!.title)
        assertEquals("", byUrl["https://pi.example/nocats.xml"]!!.genre)
        assertEquals("https://pi.example/n.jpg", byUrl["https://pi.example/nocats.xml"]!!.artworkUrl)
        assertEquals("https://pi.example/b.jpg", byUrl["https://pi.example/blank.xml"]!!.artworkUrl)
        assertEquals("Seven", byUrl["https://pi.example/blank.xml"]!!.genre)
        assertNull(byUrl["https://pi.example/noart.xml"]!!.artworkUrl)
        assertEquals("", byUrl["https://pi.example/noart.xml"]!!.genre)

        val before = server.requestCount
        assertEquals(emptyList<PodcastSearchResult>(), service.search("  "))
        assertEquals(before, server.requestCount)
    }

    @Test fun `isConfigured is true only when key and secret are both non-blank, otherwise search makes no request`() =
        runTest {
            assertTrue(PodcastIndexCredentials("k", "s").isConfigured)
            val bad = listOf("" to "s", "k" to "", "  " to "s", "" to "")
            for ((k, s) in bad) {
                val c = PodcastIndexCredentials(k, s)
                assertFalse("($k,$s)", c.isConfigured)
                assertEquals(emptyList<PodcastSearchResult>(), newService(c).search("x"))
            }
            assertEquals(0, server.requestCount)
        }

    @Test fun `search returns empty for empty feeds and throws on http error or malformed json`() = runTest {
        val service = newService()
        enqueueFixture("search/podcastindex_empty.json")
        assertEquals(emptyList<PodcastSearchResult>(), service.search("x"))

        server.enqueue(MockResponse().setBody("""{"status":"true","count":0}"""))
        assertEquals(emptyList<PodcastSearchResult>(), service.search("x"))

        for (code in listOf(401, 500)) {
            server.enqueue(MockResponse().setResponseCode(code))
            try {
                service.search("x"); fail("expected HttpException $code")
            } catch (e: HttpException) {
                assertEquals(code, e.code())
            }
        }

        server.enqueue(MockResponse().setBody("""{"feeds":[{"title":"Cut","ur"""))
        try {
            service.search("x"); fail("expected SerializationException")
        } catch (e: SerializationException) {
            // expected
        }
    }
}
