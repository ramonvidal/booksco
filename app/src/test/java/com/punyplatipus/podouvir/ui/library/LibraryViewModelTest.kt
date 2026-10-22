package com.punyplatipus.podouvir.ui.library

import app.cash.turbine.test
import com.punyplatipus.podouvir.domain.model.LibrarySettings
import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.UNKNOWN_GENRE
import com.punyplatipus.podouvir.domain.model.ViewMode
import com.punyplatipus.podouvir.domain.model.groupByGenre
import com.punyplatipus.podouvir.testutil.FakeLibraryPreferences
import com.punyplatipus.podouvir.testutil.FakePodcastRepository
import com.punyplatipus.podouvir.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainRule = MainDispatcherRule(dispatcher)

    private fun podcast(id: Long, title: String, genre: String, order: Int, override: String? = null) =
        Podcast(
            id = id,
            feedUrl = "https://example.com/$id",
            title = title,
            genre = genre,
            genreOverride = override,
            customOrder = order,
        )

    // Technology ids 1,2,3 (customOrder 0,1,2); News ids 4,5.
    private fun library() = listOf(
        podcast(1, "Gamma", "Technology", 0),
        podcast(2, "Alpha", "Technology", 1),
        podcast(3, "Beta", "Technology", 2),
        podcast(4, "Delta", "News", 3),
        podcast(5, "Epsilon", "News", 4),
    )

    private fun vm(repo: FakePodcastRepository, prefs: FakeLibraryPreferences) = LibraryViewModel(repo, prefs)

    private fun LibraryUiState.ids(genre: String): List<Long> =
        groups.first { it.genre == genre }.podcasts.map { it.id }

    @Test
    fun `initial state is loading until library and settings first emit`() = runTest(dispatcher) {
        val repo = FakePodcastRepository(library())
        val prefs = FakeLibraryPreferences(emitInitial = false)
        val vm = vm(repo, prefs)
        vm.uiState.test {
            val first = awaitItem()
            assertTrue(first.isLoading)
            assertTrue(first.groups.isEmpty())
            advanceUntilIdle()
            expectNoEvents()

            prefs.release()
            advanceUntilIdle()
            val loaded = expectMostRecentItem()
            assertFalse(loaded.isLoading)
            assertEquals(2, loaded.groups.size)
        }
    }

    @Test
    fun `empty library yields not loading, zero count and no groups`() = runTest(dispatcher) {
        val vm = vm(FakePodcastRepository(emptyList()), FakeLibraryPreferences())
        vm.uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertFalse(s.isLoading)
            assertEquals(0, s.totalCount)
            assertTrue(s.genres.isEmpty())
            assertTrue(s.groups.isEmpty())
        }
    }

    @Test
    fun `groups equal groupByGenre of the library for the current sort order`() = runTest(dispatcher) {
        val lib = library()
        val settings = LibrarySettings(sortOrder = SortOrder.TITLE, viewMode = ViewMode.LIST)
        val vm = vm(FakePodcastRepository(lib), FakeLibraryPreferences(settings))
        vm.uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            val expected = lib.groupByGenre(SortOrder.TITLE)
            assertEquals(expected.map { GenreGroup(it.key, it.value) }, s.groups)
            assertEquals(expected.keys.toList(), s.genres)
            assertEquals(5, s.totalCount)
            assertEquals(SortOrder.TITLE, s.sortOrder)
            assertEquals(ViewMode.LIST, s.viewMode)
        }
    }

    @Test
    fun `selected genre keeps only that group while genres still lists all`() = runTest(dispatcher) {
        val prefs = FakeLibraryPreferences(LibrarySettings(selectedGenre = "News"))
        val vm = vm(FakePodcastRepository(library()), prefs)
        vm.uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(listOf("News"), s.groups.map { it.genre })
            assertEquals(listOf("News", "Technology"), s.genres)
            assertEquals("News", s.selectedGenre)
        }
    }

    @Test
    fun `selected genre matches case-insensitively`() = runTest(dispatcher) {
        val prefs = FakeLibraryPreferences(LibrarySettings(selectedGenre = "news"))
        val vm = vm(FakePodcastRepository(library()), prefs)
        vm.uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(listOf("News"), s.groups.map { it.genre })
            assertEquals("News", s.selectedGenre)
        }
    }

    @Test
    fun `stale selected genre is treated as All`() = runTest(dispatcher) {
        val repo = FakePodcastRepository(library())
        val prefs = FakeLibraryPreferences(LibrarySettings(selectedGenre = "Comedy"))
        val vm = vm(repo, prefs)
        vm.uiState.test {
            advanceUntilIdle()
            val stale = expectMostRecentItem()
            assertNull(stale.selectedGenre)
            assertEquals(2, stale.groups.size)

            repo.podcasts.value = library() + podcast(6, "Zed", "Comedy", 5)
            advanceUntilIdle()
            val now = expectMostRecentItem()
            assertEquals("Comedy", now.selectedGenre)
            assertEquals(listOf("Comedy"), now.groups.map { it.genre })
            assertTrue("VM must not rewrite prefs", prefs.selectedGenreCalls.isEmpty())
        }
    }

    @Test
    fun `selectGenre, setSortOrder and toggleViewMode write to preferences and state follows`() = runTest(dispatcher) {
        val prefs = FakeLibraryPreferences()
        val vm = vm(FakePodcastRepository(library()), prefs)
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()

            vm.selectGenre("News")
            vm.setSortOrder(SortOrder.DATE_ADDED)
            vm.toggleViewMode()
            advanceUntilIdle()

            assertEquals(listOf<String?>("News"), prefs.selectedGenreCalls)
            assertEquals(listOf(SortOrder.DATE_ADDED), prefs.sortOrderCalls)
            assertEquals(listOf(ViewMode.LIST), prefs.viewModeCalls)
            val s = expectMostRecentItem()
            assertEquals("News", s.selectedGenre)
            assertEquals(SortOrder.DATE_ADDED, s.sortOrder)
            assertEquals(ViewMode.LIST, s.viewMode)

            vm.selectGenre(null)
            advanceUntilIdle()
            assertNull(expectMostRecentItem().selectedGenre)
            assertEquals(listOf<String?>("News", null), prefs.selectedGenreCalls)
        }
    }

    @Test
    fun `toggleViewMode flips between grid and list`() = runTest(dispatcher) {
        val prefs = FakeLibraryPreferences(LibrarySettings(viewMode = ViewMode.GRID))
        val vm = vm(FakePodcastRepository(library()), prefs)
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()
            vm.toggleViewMode()
            advanceUntilIdle()
            vm.toggleViewMode()
            advanceUntilIdle()
            assertEquals(listOf(ViewMode.LIST, ViewMode.GRID), prefs.viewModeCalls)
            assertEquals(ViewMode.GRID, expectMostRecentItem().viewMode)
        }
    }

    @Test
    fun `library changes re-emit grouped state`() = runTest(dispatcher) {
        val repo = FakePodcastRepository(library())
        val vm = vm(repo, FakeLibraryPreferences())
        vm.uiState.test {
            advanceUntilIdle()
            assertEquals(5, expectMostRecentItem().totalCount)

            repo.podcasts.value = library() + podcast(6, "Zed", "Comedy", 5)
            advanceUntilIdle()
            val added = expectMostRecentItem()
            assertEquals(6, added.totalCount)
            assertEquals(listOf("Comedy", "News", "Technology"), added.genres)
            assertEquals(3, added.groups.size)

            repo.podcasts.value = library().filter { it.id !in listOf(4L, 5L) }
            advanceUntilIdle()
            val removed = expectMostRecentItem()
            assertEquals(3, removed.totalCount)
            assertEquals(listOf("Technology"), removed.genres)
            assertEquals(listOf("Technology"), removed.groups.map { it.genre })
        }
    }

    @Test
    fun `startReorder sets custom sort order persisted and isReordering true, finishReorder clears it`() =
        runTest(dispatcher) {
            val prefs = FakeLibraryPreferences(LibrarySettings(sortOrder = SortOrder.TITLE))
            val vm = vm(FakePodcastRepository(library()), prefs)
            vm.uiState.test {
                advanceUntilIdle()
                assertFalse(expectMostRecentItem().isReordering)

                vm.startReorder()
                advanceUntilIdle()
                assertEquals(listOf(SortOrder.CUSTOM), prefs.sortOrderCalls)
                val started = expectMostRecentItem()
                assertEquals(SortOrder.CUSTOM, started.sortOrder)
                assertTrue(started.isReordering)

                vm.finishReorder()
                advanceUntilIdle()
                val finished = expectMostRecentItem()
                assertFalse(finished.isReordering)
                assertEquals(SortOrder.CUSTOM, finished.sortOrder)
            }
        }

    @Test
    fun `moveDown swaps with next neighbour in same genre and sends all ids in new order`() = runTest(dispatcher) {
        val repo = FakePodcastRepository(library())
        val vm = vm(repo, FakeLibraryPreferences(LibrarySettings(sortOrder = SortOrder.CUSTOM)))
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()
            vm.startReorder()
            advanceUntilIdle()
            vm.moveDown(1)
            advanceUntilIdle()

            assertEquals(1, repo.customOrderCalls.size)
            val sent = repo.customOrderCalls.single()
            // Flattened in group order (News, Technology); assert per-group relative order + each id once.
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sent.sorted())
            assertEquals(listOf(2L, 1L, 3L), sent.filter { it in listOf(1L, 2L, 3L) })
            assertEquals(listOf(4L, 5L), sent.filter { it in listOf(4L, 5L) })

            val s = expectMostRecentItem()
            assertEquals(listOf(2L, 1L, 3L), s.ids("Technology"))
            assertEquals(listOf(4L, 5L), s.ids("News"))
        }
    }

    @Test
    fun `moveUp swaps with previous neighbour and never crosses genre groups`() = runTest(dispatcher) {
        val repo = FakePodcastRepository(library())
        val vm = vm(repo, FakeLibraryPreferences(LibrarySettings(sortOrder = SortOrder.CUSTOM)))
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()
            vm.startReorder()
            advanceUntilIdle()

            vm.moveUp(3)
            advanceUntilIdle()
            assertEquals(1, repo.customOrderCalls.size)
            val sent = repo.customOrderCalls.single()
            expectMostRecentItem()
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L), sent.sorted())
            assertEquals(listOf(1L, 3L, 2L), sent.filter { it in listOf(1L, 2L, 3L) })
            assertEquals(listOf(4L, 5L), sent.filter { it in listOf(4L, 5L) })

            vm.moveUp(4)
            advanceUntilIdle()
            assertEquals(1, repo.customOrderCalls.size)
        }
    }

    @Test
    fun `moveUp on first, moveDown on last, unknown id, or when not reordering do not call the repository`() =
        runTest(dispatcher) {
            val repo = FakePodcastRepository(library())
            val vm = vm(repo, FakeLibraryPreferences(LibrarySettings(sortOrder = SortOrder.CUSTOM)))
            vm.uiState.test {
                advanceUntilIdle()
                expectMostRecentItem()

                // (b) not reordering
                vm.moveUp(2)
                vm.moveDown(2)
                advanceUntilIdle()
                assertTrue(repo.customOrderCalls.isEmpty())

                // (a) reordering
                vm.startReorder()
                advanceUntilIdle()
                vm.moveUp(1)
                vm.moveDown(3)
                vm.moveUp(999)
                advanceUntilIdle()
                assertTrue(repo.customOrderCalls.isEmpty())
                expectMostRecentItem()
            }
        }

    @Test
    fun `changeGenre and unsubscribe delegate to the repository`() = runTest(dispatcher) {
        val repo = FakePodcastRepository(library())
        val vm = vm(repo, FakeLibraryPreferences())
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()

            vm.changeGenre(1, "Comedy")
            advanceUntilIdle()
            val regrouped = expectMostRecentItem()
            assertTrue(regrouped.genres.contains("Comedy"))
            assertEquals(listOf(1L), regrouped.ids("Comedy"))

            vm.changeGenre(1, null)
            vm.unsubscribe(2)
            advanceUntilIdle()
            assertEquals(listOf<Pair<Long, String?>>(1L to "Comedy", 1L to null), repo.genreOverrideCalls)
            assertEquals(listOf(2L), repo.unsubscribeCalls)
            val s = expectMostRecentItem()
            assertEquals(4, s.totalCount)
            assertFalse(s.genres.contains("Comedy"))
            assertFalse(s.groups.flatMap { it.podcasts }.any { it.id == 2L })
        }
    }

    @Test
    fun `podcasts without a genre land in the last Other group (added by tester)`() = runTest(dispatcher) {
        val lib = library() + podcast(6, "Aaa", "", 5)
        val vm = vm(FakePodcastRepository(lib), FakeLibraryPreferences())
        vm.uiState.test {
            advanceUntilIdle()
            val s = expectMostRecentItem()
            assertEquals(UNKNOWN_GENRE, s.genres.last())
            assertEquals(listOf(6L), s.ids(UNKNOWN_GENRE))
        }
    }
}
