package com.punyplatipus.podouvir.ui.discover

import com.punyplatipus.podouvir.data.repository.PodcastRepository
import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import com.punyplatipus.podouvir.testutil.FakePodcastRepository
import com.punyplatipus.podouvir.testutil.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Wraps a fake repository; search/subscribe first await an optional gate and record started/completed calls. */
class GatedPodcastRepository(
    val delegate: FakePodcastRepository,
) : PodcastRepository by delegate {
    val searchGates = mutableMapOf<String, CompletableDeferred<Unit>>()
    var subscribeGate: CompletableDeferred<Unit>? = null
    val searchStarted = mutableListOf<String>()
    val searchCompleted = mutableListOf<String>()
    val subscribeStarted = mutableListOf<Pair<String, String?>>()
    val subscribeCompleted = mutableListOf<Pair<String, String?>>()

    override suspend fun search(term: String): Result<List<PodcastSearchResult>> {
        searchStarted += term
        searchGates[term]?.await()
        val r = delegate.search(term)
        searchCompleted += term
        return r
    }

    override suspend fun subscribe(feedUrl: String, fallbackGenre: String?): Result<Long> {
        subscribeStarted += feedUrl to fallbackGenre
        subscribeGate?.await()
        val r = delegate.subscribe(feedUrl, fallbackGenre)
        subscribeCompleted += feedUrl to fallbackGenre
        return r
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    @get:Rule
    val rule = MainDispatcherRule(StandardTestDispatcher())

    private fun result(feedUrl: String, title: String = "T $feedUrl", genre: String = "Technology") =
        PodcastSearchResult(feedUrl, title, "Author", null, genre)

    private fun podcast(id: Long, feedUrl: String) = Podcast(id = id, feedUrl = feedUrl, title = "P$id")

    private val a = result("https://a/feed", genre = "Technology")
    private val b = result("https://b/feed", genre = "History")

    /** Creates the VM, keeps uiState hot and lets init coroutines start. */
    private fun TestScope.start(repo: PodcastRepository): DiscoverViewModel {
        val vm = DiscoverViewModel(repo)
        backgroundScope.launch { vm.uiState.collect { } }
        runCurrent()
        return vm
    }

    private fun TestScope.search(vm: DiscoverViewModel, text: String) {
        vm.onQueryChange(text)
        advanceTimeBy(400)
        runCurrent()
    }

    @Test
    fun `onQueryChange updates query immediately and searches only after 400 ms debounce with trimmed query`() =
        runTest(rule.dispatcher) {
            val fake = FakePodcastRepository()
            fake.searchResult = Result.success(listOf(a, b))
            val vm = start(fake)

            vm.onQueryChange("  cast  ")
            runCurrent()
            assertEquals("  cast  ", vm.uiState.value.query)
            advanceTimeBy(399)
            runCurrent()
            assertTrue(fake.searchCalls.isEmpty())

            advanceTimeBy(1)
            runCurrent()
            assertEquals(listOf("cast"), fake.searchCalls)
            val s = vm.uiState.value
            assertEquals(2, s.items.size)
            assertFalse(s.isSearching)
            assertTrue(s.hasSearched)
        }

    @Test
    fun `rapid typing searches only the last query`() = runTest(rule.dispatcher) {
        val fake = FakePodcastRepository()
        val vm = start(fake)

        vm.onQueryChange("a")
        advanceTimeBy(200)
        vm.onQueryChange("ab")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(listOf("ab"), fake.searchCalls)
    }

    @Test
    fun `newer query cancels in-flight search`() = runTest(rule.dispatcher) {
        val fake = FakePodcastRepository()
        val gated = GatedPodcastRepository(fake)
        val gateA = CompletableDeferred<Unit>()
        gated.searchGates["a"] = gateA
        fake.searchResult = Result.success(listOf(a))
        val vm = start(gated)

        search(vm, "a")
        assertEquals(listOf("a"), gated.searchStarted)
        assertTrue(vm.uiState.value.isSearching)

        fake.searchResult = Result.success(listOf(b))
        search(vm, "b")
        gateA.complete(Unit)
        advanceUntilIdle()

        assertFalse("a must not complete", "a" in gated.searchCompleted)
        assertEquals(listOf(b), vm.uiState.value.items.map { it.result })
        assertFalse(vm.uiState.value.isSearching)
    }

    @Test
    fun `blank query clears items and hasSearched without calling the repository`() = runTest(rule.dispatcher) {
        val fake = FakePodcastRepository()
        fake.searchResult = Result.success(listOf(a))
        val vm = start(fake)
        search(vm, "cast")
        assertEquals(1, vm.uiState.value.items.size)
        assertTrue(vm.uiState.value.hasSearched)

        vm.onQueryChange("   ")
        advanceTimeBy(1000)
        runCurrent()

        val s = vm.uiState.value
        assertTrue(s.items.isEmpty())
        assertFalse(s.hasSearched)
        assertFalse(s.isSearching)
        assertEquals(listOf("cast"), fake.searchCalls)
    }

    @Test
    fun `blank query cancels a pending debounced search`() = runTest(rule.dispatcher) {
        // (added by tester)
        val fake = FakePodcastRepository()
        val vm = start(fake)

        vm.onQueryChange("cast")
        advanceTimeBy(200)
        vm.onQueryChange("")
        advanceTimeBy(1000)
        runCurrent()

        assertTrue(fake.searchCalls.isEmpty())
    }

    @Test
    fun `same trimmed query twice in a row does not search again`() = runTest(rule.dispatcher) {
        val fake = FakePodcastRepository()
        fake.searchResult = Result.success(listOf(a, b))
        val vm = start(fake)
        search(vm, "cast")
        val before = vm.uiState.value.items

        vm.onQueryChange("cast ")
        advanceTimeBy(1000)
        runCurrent()

        assertEquals(1, fake.searchCalls.size)
        assertEquals("cast ", vm.uiState.value.query)
        assertEquals(before, vm.uiState.value.items)
    }

    @Test
    fun `search failure sets SEARCH_FAILED and keeps previous items`() = runTest(rule.dispatcher) {
        val fake = FakePodcastRepository()
        fake.searchResult = Result.success(listOf(a, b))
        val vm = start(fake)
        search(vm, "first")
        val previous = vm.uiState.value.items
        assertEquals(2, previous.size)

        fake.searchResult = Result.failure(IOException())
        search(vm, "new")

        val s = vm.uiState.value
        assertEquals(DiscoverMessage.SEARCH_FAILED, s.message)
        assertEquals(previous, s.items)
        assertFalse(s.isSearching)

        // Boundary: empty result list -> empty items, hasSearched, no message.
        vm.messageShown()
        fake.searchResult = Result.success(emptyList())
        search(vm, "nothing")
        val e = vm.uiState.value
        assertTrue(e.items.isEmpty())
        assertTrue(e.hasSearched)
        assertNull(e.message)
    }

    @Test
    fun `item status reflects observeLibrary live`() = runTest(rule.dispatcher) {
        val fake = FakePodcastRepository(listOf(podcast(1, a.feedUrl)))
        fake.searchResult = Result.success(listOf(a, b))
        val vm = start(fake)
        search(vm, "x")

        fun statuses() = vm.uiState.value.items.associate { it.result.feedUrl to it.status }
        assertEquals(SubscriptionStatus.SUBSCRIBED, statuses()[a.feedUrl])
        assertEquals(SubscriptionStatus.NOT_SUBSCRIBED, statuses()[b.feedUrl])

        fake.unsubscribe(1)
        runCurrent()
        assertEquals(SubscriptionStatus.NOT_SUBSCRIBED, statuses()[a.feedUrl])
        assertEquals(1, fake.searchCalls.size)

        fake.subscribe(b.feedUrl)
        runCurrent()
        assertEquals(SubscriptionStatus.SUBSCRIBED, statuses()[b.feedUrl])
    }

    @Test
    fun `subscribe goes SUBSCRIBING then SUBSCRIBED and emits message`() = runTest(rule.dispatcher) {
        val fake = FakePodcastRepository()
        val gated = GatedPodcastRepository(fake)
        val r = result("https://r/feed", genre = "Technology")
        fake.searchResult = Result.success(listOf(r))
        val vm = start(gated)
        search(vm, "r")
        val gate = CompletableDeferred<Unit>()
        gated.subscribeGate = gate

        vm.subscribe(r)
        runCurrent()
        assertEquals(SubscriptionStatus.SUBSCRIBING, vm.uiState.value.items.single().status)
        assertEquals(listOf(r.feedUrl to "Technology"), gated.subscribeStarted)

        gate.complete(Unit)
        runCurrent()
        assertEquals(SubscriptionStatus.SUBSCRIBED, vm.uiState.value.items.single().status)
        assertEquals(DiscoverMessage.SUBSCRIBED, vm.uiState.value.message)
        assertEquals(listOf(r.feedUrl to "Technology"), fake.subscribeCalls)
    }

    @Test
    fun `subscribe failure reverts to NOT_SUBSCRIBED with SUBSCRIBE_FAILED`() = runTest(rule.dispatcher) {
        val fake = FakePodcastRepository()
        fake.searchResult = Result.success(listOf(a))
        fake.subscribeResult = { Result.failure(IOException()) }
        val vm = start(fake)
        search(vm, "a")

        vm.subscribe(a)
        advanceUntilIdle()

        assertEquals(SubscriptionStatus.NOT_SUBSCRIBED, vm.uiState.value.items.single().status)
        assertEquals(DiscoverMessage.SUBSCRIBE_FAILED, vm.uiState.value.message)
    }

    @Test
    fun `second subscribe while SUBSCRIBING is ignored`() = runTest(rule.dispatcher) {
        val fake = FakePodcastRepository()
        val gated = GatedPodcastRepository(fake)
        fake.searchResult = Result.success(listOf(a))
        val vm = start(gated)
        search(vm, "a")
        val gate = CompletableDeferred<Unit>()
        gated.subscribeGate = gate

        vm.subscribe(a)
        runCurrent()
        vm.subscribe(a)
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, gated.subscribeStarted.size)
        assertEquals(1, fake.subscribeCalls.size)
        assertEquals(DiscoverMessage.SUBSCRIBED, vm.uiState.value.message)
        vm.messageShown()
        runCurrent()
        assertNull("no duplicated message", vm.uiState.value.message)
    }

    @Test
    fun `addByUrl validates, trims, tracks isAddingByUrl and reports outcome, and messageShown clears message`() =
        runTest(rule.dispatcher) {
            val fake = FakePodcastRepository()
            val gated = GatedPodcastRepository(fake)
            val vm = start(gated)

            // (a) invalid urls
            vm.addByUrl("  ftp://x/feed ")
            runCurrent()
            assertEquals(DiscoverMessage.INVALID_URL, vm.uiState.value.message)
            vm.messageShown()
            runCurrent()
            vm.addByUrl("example.com/feed")
            runCurrent()
            assertEquals(DiscoverMessage.INVALID_URL, vm.uiState.value.message)
            assertTrue(gated.subscribeStarted.isEmpty())
            assertFalse(vm.uiState.value.isAddingByUrl)
            vm.messageShown()
            runCurrent()

            // (b) valid, gated
            val gate = CompletableDeferred<Unit>()
            gated.subscribeGate = gate
            vm.addByUrl("  https://example.com/feed  ")
            runCurrent()
            assertTrue(vm.uiState.value.isAddingByUrl)
            assertEquals(listOf("https://example.com/feed" to null), gated.subscribeStarted)
            gate.complete(Unit)
            runCurrent()
            assertFalse(vm.uiState.value.isAddingByUrl)
            assertEquals(DiscoverMessage.SUBSCRIBED, vm.uiState.value.message)
            vm.messageShown()
            runCurrent()

            // (c) failure
            fake.subscribeResult = { Result.failure(IOException()) }
            vm.addByUrl("http://bad/feed")
            advanceUntilIdle()
            assertEquals(DiscoverMessage.SUBSCRIBE_FAILED, vm.uiState.value.message)
            assertFalse(vm.uiState.value.isAddingByUrl)

            // (d) messageShown
            vm.messageShown()
            runCurrent()
            assertNull(vm.uiState.value.message)
        }
}
