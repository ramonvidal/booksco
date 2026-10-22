package com.punyplatipus.podouvir.data.search

import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CompositeSearchServiceTest {
    private val fyyd = mockk<FyydSearchService>()
    private val pi = mockk<PodcastIndexSearchService>()
    private val configured = PodcastIndexCredentials("k", "s")
    private val notConfigured = PodcastIndexCredentials("", "")

    private fun r(url: String, title: String = url) = PodcastSearchResult(url, title, "", null, "")
    private val a = r("a")
    private val b = r("b", "B from PI")
    private val bFyyd = r("b", "B from fyyd")
    private val c = r("c")
    private val d = r("d")

    private fun service(creds: PodcastIndexCredentials = configured) = CompositeSearchService(fyyd, pi, creds)

    @Test fun `blank term returns empty without calling any provider`() = runTest {
        val s = service()
        assertEquals(emptyList<PodcastSearchResult>(), s.search(""))
        assertEquals(emptyList<PodcastSearchResult>(), s.search("  "))
        coVerify(exactly = 0) { fyyd.search(any(), any()) }
        coVerify(exactly = 0) { pi.search(any(), any()) }
    }

    @Test fun `configured search merges podcast index first then new fyyd results truncated to limit`() = runTest {
        coEvery { pi.search(any(), any()) } returns listOf(a, b)
        coEvery { fyyd.search(any(), any()) } returns listOf(bFyyd, c, d)
        val s = service()
        assertEquals(listOf(a, b, c), s.search(" x ", 3))
        assertEquals(listOf(a, b, c, d), s.search("x", 25))
        assertEquals(listOf(a), s.search("x", 1))
        coVerify { pi.search("x", any()) }
        coVerify { fyyd.search("x", any()) }
        coVerify(exactly = 0) { pi.search(" x ", any()) }
        coVerify(exactly = 0) { fyyd.search(" x ", any()) }
    }

    @Test fun `configured providers run concurrently`() = runTest {
        val piGate = CompletableDeferred<Unit>()
        val fyydGate = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        coEvery { pi.search(any(), any()) } coAnswers { started += "pi"; piGate.await(); listOf(a, b) }
        coEvery { fyyd.search(any(), any()) } coAnswers { started += "fyyd"; fyydGate.await(); listOf(bFyyd, c, d) }
        val result = async { service().search("x", 25) }
        runCurrent()
        assertEquals(setOf("pi", "fyyd"), started.toSet())
        assertFalse(result.isCompleted)
        piGate.complete(Unit)
        fyydGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(a, b, c, d), result.await())
    }

    @Test fun `one provider failing returns the other's results, both failing throws the fyyd exception`() = runTest {
        coEvery { pi.search(any(), any()) } throws IOException("pi")
        coEvery { fyyd.search(any(), any()) } returns listOf(c, d)
        assertEquals(listOf(c, d), service().search("x"))

        coEvery { pi.search(any(), any()) } returns listOf(a, b)
        val fyydError = IOException("fyyd")
        coEvery { fyyd.search(any(), any()) } throws fyydError
        assertEquals(listOf(a, b), service().search("x"))

        coEvery { pi.search(any(), any()) } throws IOException("pi")
        try {
            service().search("x"); fail("expected IOException")
        } catch (e: IOException) {
            assertSame(fyydError, e)
            assertEquals("fyyd", e.message)
        }
    }

    @Test fun `not configured uses fyyd only and propagates its exception`() = runTest {
        coEvery { fyyd.search(any(), any()) } returns listOf(c, c, d)
        val s = service(notConfigured)
        assertEquals(listOf(c, d), s.search("x"))
        assertEquals(listOf(c), s.search("x", 1))
        coVerify(exactly = 0) { pi.search(any(), any()) }

        val err = IOException("fyyd")
        coEvery { fyyd.search(any(), any()) } throws err
        try {
            s.search("x"); fail("expected IOException")
        } catch (e: IOException) {
            assertSame(err, e)
        }
        coVerify(exactly = 0) { pi.search(any(), any()) }
    }

    @Test fun `CancellationException is never swallowed`() = runTest {
        coEvery { pi.search(any(), any()) } throws CancellationException("pi cancelled")
        coEvery { fyyd.search(any(), any()) } returns listOf(c)
        try {
            service().search("x"); fail("expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }

        coEvery { pi.search(any(), any()) } returns listOf(a)
        coEvery { fyyd.search(any(), any()) } throws CancellationException("fyyd cancelled")
        try {
            service().search("x"); fail("expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }

        // cancelling the caller cancels both child calls
        var piCancelled = false
        var fyydCancelled = false
        coEvery { pi.search(any(), any()) } coAnswers {
            try { awaitCancellation() } finally { piCancelled = true }
        }
        coEvery { fyyd.search(any(), any()) } coAnswers {
            try { awaitCancellation() } finally { fyydCancelled = true }
        }
        val job = async { service().search("x") }
        runCurrent()
        job.cancel()
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertTrue(piCancelled)
        assertTrue(fyydCancelled)
    }
}
