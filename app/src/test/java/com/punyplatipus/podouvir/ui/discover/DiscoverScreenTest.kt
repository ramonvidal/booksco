package com.punyplatipus.podouvir.ui.discover

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.punyplatipus.podouvir.R
import com.punyplatipus.podouvir.domain.model.PodcastSearchResult
import com.punyplatipus.podouvir.domain.model.UNKNOWN_GENRE
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoverScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any) =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private val hasProgress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private val queries = mutableListOf<String>()
    private val subscribed = mutableListOf<PodcastSearchResult>()
    private val urls = mutableListOf<String>()
    private var shown = 0

    private fun show(state: DiscoverUiState) {
        rule.setContent {
            DiscoverScreen(
                state = state,
                onQueryChange = { queries += it },
                onSubscribe = { subscribed += it },
                onAddByUrl = { urls += it },
                onMessageShown = { shown++ },
            )
        }
    }

    @Test
    fun `shows initial hint, progress, or empty state according to state`() {
        var state by mutableStateOf(DiscoverUiState())
        rule.setContent { DiscoverScreen(state, {}, {}, {}, {}) }

        // (a) default
        rule.onNodeWithText(str(R.string.discover_initial_hint)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.discover_no_results)).assertDoesNotExist()

        // (b) searching
        state = DiscoverUiState(query = "x", isSearching = true)
        rule.waitForIdle()
        rule.onAllNodes(hasProgress).assertCountEquals(1)
        rule.onNodeWithText(str(R.string.discover_no_results)).assertDoesNotExist()
        rule.onNodeWithText(str(R.string.discover_initial_hint)).assertDoesNotExist()

        // (c) searched, nothing found
        state = DiscoverUiState(query = "x", hasSearched = true)
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.discover_no_results)).assertIsDisplayed()
        rule.onAllNodes(hasProgress).assertCountEquals(0)
    }

    @Test
    fun `result rows render title, author and genre and the trailing control for each status`() {
        val r1 = PodcastSearchResult("https://1/feed", "Alpha Show", "Ana", null, "Technology")
        val r2 = PodcastSearchResult("https://2/feed", "Beta Show", "Bruno", null, "History")
        val r3 = PodcastSearchResult("https://3/feed", "Gamma Show", "Carla", null, UNKNOWN_GENRE)
        show(
            DiscoverUiState(
                query = "show",
                hasSearched = true,
                items = listOf(
                    DiscoverItem(r1, SubscriptionStatus.NOT_SUBSCRIBED),
                    DiscoverItem(r2, SubscriptionStatus.SUBSCRIBING),
                    DiscoverItem(r3, SubscriptionStatus.SUBSCRIBED),
                ),
            ),
        )

        rule.onNodeWithText("Alpha Show").assertIsDisplayed()
        rule.onNodeWithText("Beta Show").assertIsDisplayed()
        rule.onNodeWithText("Gamma Show").assertIsDisplayed()
        rule.onNodeWithText(str(R.string.discover_author_genre, "Ana", "Technology")).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.discover_author_genre, "Bruno", "History")).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.discover_author_genre, "Carla", str(R.string.components_genre_unknown)))
            .assertIsDisplayed()

        rule.onAllNodesWithText(str(R.string.discover_subscribe)).assertCountEquals(1)
        rule.onAllNodes(hasProgress).assertCountEquals(1)
        rule.onAllNodes(hasContentDescription(str(R.string.discover_subscribed)))
            .assertCountEquals(1)

        rule.onNodeWithText(str(R.string.discover_subscribe)).performClick()
        assertEquals(listOf(r1), subscribed)
    }

    @Test
    fun `search field shows hint, forwards typing, and shows clear button only when non-empty`() {
        var state by mutableStateOf(DiscoverUiState(query = ""))
        rule.setContent { DiscoverScreen(state, { queries += it }, {}, {}, {}) }
        val clear = str(R.string.discover_clear)

        // (a) empty
        rule.onNodeWithText(str(R.string.discover_search_hint)).assertIsDisplayed()
        rule.onNodeWithContentDescription(clear).assertDoesNotExist()
        rule.onNode(hasSetTextAction()).performTextInput("abc")
        assertEquals(listOf("abc"), queries)

        // (b) non-empty
        state = DiscoverUiState(query = "abc")
        rule.waitForIdle()
        rule.onNodeWithContentDescription(clear).assertIsDisplayed().performClick()
        assertEquals(listOf("abc", ""), queries)
    }

    @Test
    fun `add-by-URL action opens dialog, submits the entered URL, and cancel closes without a call`() {
        show(DiscoverUiState(query = "q"))
        val action = str(R.string.discover_add_by_url)
        val confirm = str(R.string.discover_add_by_url_confirm)
        val cancel = str(R.string.discover_add_by_url_cancel)

        rule.onNodeWithText(action).assertDoesNotExist()
        rule.onNodeWithContentDescription(action).performClick()
        rule.onNodeWithText(action).assertIsDisplayed() // dialog title

        // Cancel closes without a call.
        rule.onNodeWithText(cancel).performClick()
        rule.onNodeWithText(cancel).assertDoesNotExist()
        assertEquals(emptyList<String>(), urls)

        // Reopen, type, confirm.
        rule.onNodeWithContentDescription(action).performClick()
        rule.onNode(hasSetTextAction() and !hasText("q")).performTextInput("https://x.example/feed")
        rule.onNodeWithText(confirm).performClick()
        assertEquals(listOf("https://x.example/feed"), urls)
        rule.onNodeWithText(cancel).assertDoesNotExist()
    }

    @Test
    fun `message is shown once in a snackbar then onMessageShown is invoked once`() {
        var state by mutableStateOf(DiscoverUiState(message = DiscoverMessage.SUBSCRIBE_FAILED))
        // Keep the clock manual so the snackbar is not auto-dismissed before we can observe it.
        rule.mainClock.autoAdvance = false
        rule.setContent { DiscoverScreen(state, {}, {}, {}, { shown++ }) }

        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText(str(R.string.discover_message_subscribe_failed)).assertIsDisplayed()
        assertEquals(0, shown)

        rule.mainClock.advanceTimeBy(10_000)
        rule.waitForIdle()
        assertEquals(1, shown)

        // Further recompositions with the same message do not re-fire.
        rule.mainClock.advanceTimeBy(10_000)
        rule.waitForIdle()
        assertEquals(1, shown)

        state = DiscoverUiState(message = DiscoverMessage.INVALID_URL)
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText(str(R.string.discover_message_invalid_url)).assertIsDisplayed()
        rule.mainClock.advanceTimeBy(10_000)
        rule.waitForIdle()
        assertEquals(2, shown)
    }
}
