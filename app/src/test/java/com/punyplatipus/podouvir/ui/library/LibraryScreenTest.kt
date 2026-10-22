package com.punyplatipus.podouvir.ui.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.punyplatipus.podouvir.R
import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.UNKNOWN_GENRE
import com.punyplatipus.podouvir.domain.model.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class LibraryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val opened = mutableListOf<Long>()
    private var discover = 0
    private val genres = mutableListOf<String?>()
    private val sorts = mutableListOf<SortOrder>()
    private var toggles = 0
    private var startReorder = 0
    private var finishReorder = 0
    private val ups = mutableListOf<Long>()
    private val downs = mutableListOf<Long>()
    private val changed = mutableListOf<Pair<Long, String?>>()
    private val unsubscribed = mutableListOf<Long>()

    private fun str(id: Int, vararg args: Any) =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun podcast(id: Long, title: String, author: String = "", genre: String = "Technology", genreOverride: String? = null) =
        Podcast(id = id, feedUrl = "https://example.com/$id", title = title, author = author, genre = genre, genreOverride = genreOverride)

    private val a = podcast(1, "Alpha", "Ann")
    private val b = podcast(2, "Bravo", "Bob")
    private val c = podcast(3, "Charlie", "Cy")
    private val d = podcast(4, "Delta", "Di", genre = "News")
    private val e = podcast(5, "Echo", "Ed", genre = "News")

    private fun state(
        groups: List<GenreGroup> = listOf(GenreGroup("Technology", listOf(a, b, c)), GenreGroup("News", listOf(d, e))),
        genres: List<String> = listOf("Technology", "News"),
        selectedGenre: String? = null,
        viewMode: ViewMode = ViewMode.GRID,
        sortOrder: SortOrder = SortOrder.TITLE,
        isReordering: Boolean = false,
        isLoading: Boolean = false,
        totalCount: Int = groups.sumOf { it.podcasts.size },
    ) = LibraryUiState(
        isLoading = isLoading, totalCount = totalCount, genres = genres, selectedGenre = selectedGenre,
        sortOrder = sortOrder, viewMode = viewMode, groups = groups, isReordering = isReordering,
    )

    private fun render(state: LibraryUiState) {
        rule.setContent { MaterialTheme { screen(state) } }
    }

    @androidx.compose.runtime.Composable
    private fun screen(state: LibraryUiState) {
        LibraryScreen(
            state = state,
            onOpenPodcast = { opened += it },
            onDiscover = { discover++ },
            onSelectGenre = { genres += it },
            onSortOrder = { sorts += it },
            onToggleViewMode = { toggles++ },
            onStartReorder = { startReorder++ },
            onFinishReorder = { finishReorder++ },
            onMoveUp = { ups += it },
            onMoveDown = { downs += it },
            onChangeGenre = { id, g -> changed += id to g },
            onUnsubscribe = { unsubscribed += it },
        )
    }

    private fun openSortMenu() = rule.onNodeWithContentDescription(str(R.string.library_sort)).performClick()

    @Test
    fun `top bar shows title and grid-mode actions`() {
        render(state(viewMode = ViewMode.GRID))
        rule.onNodeWithText(str(R.string.library_title)).assertExists()
        rule.onNodeWithContentDescription(str(R.string.library_view_switch_to_list)).assertExists()
        rule.onNodeWithContentDescription(str(R.string.library_sort)).assertExists()
        rule.onAllNodesWithText(str(R.string.library_done)).assertCountEquals(0)
    }

    @Test
    fun `view toggle invokes onToggleViewMode and its description reflects the target mode`() {
        var st by mutableStateOf(state(viewMode = ViewMode.GRID))
        rule.setContent { MaterialTheme { screen(st) } }
        val toList = str(R.string.library_view_switch_to_list)
        val toGrid = str(R.string.library_view_switch_to_grid)
        assertNotEquals(toList, toGrid)
        rule.onNodeWithContentDescription(toList).performClick()
        assertEquals(1, toggles)
        rule.onAllNodesWithContentDescription(toGrid).assertCountEquals(0)
        rule.runOnIdle { st = state(viewMode = ViewMode.LIST) }
        rule.onNodeWithContentDescription(toGrid).assertExists()
        rule.onAllNodesWithContentDescription(toList).assertCountEquals(0)
    }

    @Test
    fun `sort menu lists four options plus reorder and selecting one calls onSortOrder`() {
        render(state(sortOrder = SortOrder.TITLE))
        openSortMenu()
        listOf(
            R.string.library_sort_title, R.string.library_sort_recently_updated, R.string.library_sort_date_added,
            R.string.library_sort_custom, R.string.library_reorder,
        ).forEach { rule.onNodeWithText(str(it)).assertExists() }
        rule.onNodeWithText(str(R.string.library_sort_title)).assertIsSelected()
        rule.onNodeWithText(str(R.string.library_sort_custom)).assertIsNotSelected()
        rule.onNodeWithText(str(R.string.library_sort_recently_updated)).performClick()
        openSortMenu()
        rule.onNodeWithText(str(R.string.library_sort_custom)).performClick()
        assertEquals(listOf(SortOrder.RECENTLY_UPDATED, SortOrder.CUSTOM), sorts)
    }

    @Test
    fun `reorder menu item calls onStartReorder`() {
        render(state())
        openSortMenu()
        rule.onNodeWithText(str(R.string.library_reorder)).performClick()
        assertEquals(1, startReorder)
    }

    @Test
    fun `reorder mode replaces actions with Done and hides chips`() {
        render(state(isReordering = true))
        rule.onAllNodesWithContentDescription(str(R.string.library_view_switch_to_list)).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(str(R.string.library_view_switch_to_grid)).assertCountEquals(0)
        rule.onAllNodesWithContentDescription(str(R.string.library_sort)).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.library_all)).assertCountEquals(0)
        rule.onNodeWithText(str(R.string.library_done)).performClick()
        assertEquals(1, finishReorder)
    }

    @Test
    fun `genre chips show All plus genres, reflect selection and report taps`() {
        render(state(selectedGenre = "News"))
        rule.onNodeWithText(str(R.string.library_all)).assertIsNotSelected()
        rule.onNodeWithText("News").assertIsSelected()
        rule.onNodeWithText("Technology").assertIsNotSelected()
        rule.onNodeWithText(str(R.string.library_all)).performClick()
        rule.onNodeWithText("Technology").performClick()
        assertEquals(listOf<String?>(null, "Technology"), genres)
    }

    @Test
    fun `genre chips hidden when library is empty`() {
        render(state(groups = emptyList(), genres = emptyList(), totalCount = 0, isLoading = false))
        rule.onAllNodesWithText(str(R.string.library_all)).assertCountEquals(0)
    }

    @Test
    fun `group headers show label and count and tapping collapses and expands the group`() {
        render(state(viewMode = ViewMode.LIST))
        rule.onNodeWithText("Technology (3)").assertExists()
        rule.onNodeWithText("News (2)").assertExists()
        rule.onNodeWithText("Alpha").assertExists()
        rule.onNodeWithText("Technology (3)").performClick()
        listOf("Alpha", "Bravo", "Charlie").forEach { rule.onAllNodesWithText(it).assertCountEquals(0) }
        rule.onNodeWithText("Delta").assertExists()
        rule.onNodeWithText("Echo").assertExists()
        rule.onNodeWithText("Technology (3)").performClick()
        listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo").forEach { rule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun `unknown genre header uses localised label`() {
        val x = podcast(9, "Zulu", genre = "")
        render(state(groups = listOf(GenreGroup(UNKNOWN_GENRE, listOf(x))), genres = listOf(UNKNOWN_GENRE)))
        val label = str(R.string.components_genre_unknown)
        rule.onNodeWithText(str(R.string.library_group_header, label, 1)).assertExists()
    }

    @Test
    fun `grid mode shows titles and tapping a tile opens the podcast`() {
        val p = podcast(7, "Alpha")
        render(state(groups = listOf(GenreGroup("Technology", listOf(p))), viewMode = ViewMode.GRID))
        rule.onNodeWithText("Alpha").assertExists()
        rule.onNodeWithText("Alpha").performClick()
        assertEquals(listOf(7L), opened)
    }

    @Test
    fun `list mode shows title and author and tapping a row opens the podcast`() {
        val p = podcast(7, "Alpha", author = "Ann Author")
        render(state(groups = listOf(GenreGroup("Technology", listOf(p))), viewMode = ViewMode.LIST))
        rule.onNodeWithText("Alpha").assertExists()
        rule.onNodeWithText("Ann Author").assertExists()
        rule.onNodeWithText("Alpha").performClick()
        assertEquals(listOf(7L), opened)
    }

    @Test
    fun `reorder mode move buttons call callbacks and are disabled at group edges`() {
        render(
            state(
                groups = listOf(GenreGroup("Technology", listOf(a, b, c))),
                viewMode = ViewMode.LIST, isReordering = true,
            ),
        )
        val up = str(R.string.library_move_up)
        val down = str(R.string.library_move_down)
        rule.onAllNodesWithContentDescription(up).assertCountEquals(3)
        rule.onAllNodesWithContentDescription(down).assertCountEquals(3)
        rule.onAllNodesWithContentDescription(down)[0].performClick()
        rule.onAllNodesWithContentDescription(up)[2].performClick()
        assertEquals(listOf(a.id), downs)
        assertEquals(listOf(c.id), ups)
        rule.onAllNodesWithContentDescription(up)[0].assertIsNotEnabled()
        rule.onAllNodesWithContentDescription(down)[2].assertIsNotEnabled()
        rule.onAllNodesWithContentDescription(up)[1].assertIsEnabled()
        rule.onAllNodesWithContentDescription(down)[1].assertIsEnabled()
    }

    @Test
    fun `long press opens menu and Change genre shows dialog and confirms with override`() {
        val p = podcast(7, "Alpha", genre = "Technology")
        render(
            state(
                groups = listOf(GenreGroup("Technology", listOf(p))), genres = listOf("Technology", "News"),
                viewMode = ViewMode.LIST,
            ),
        )
        rule.onNodeWithText("Alpha").performTouchInput { longClick() }
        rule.onNodeWithText(str(R.string.library_change_genre)).assertExists()
        rule.onNodeWithText(str(R.string.library_unsubscribe)).assertExists()
        rule.onNodeWithText(str(R.string.library_change_genre)).performClick()
        // Dialog is open: text field present, menu items gone.
        rule.onNode(hasSetTextAction()).assertExists()
        rule.onAllNodesWithText(str(R.string.library_unsubscribe)).assertCountEquals(0)
        rule.onNode(hasSetTextAction()).performTextInput("Comedy")
        rule.onNodeWithText(str(R.string.components_save)).performClick()
        assertEquals(listOf(7L to "Comedy"), changed)
    }

    @Test
    fun `unsubscribe asks for confirmation naming the podcast before calling onUnsubscribe`() {
        val p = podcast(7, "Alpha")
        render(state(groups = listOf(GenreGroup("Technology", listOf(p))), viewMode = ViewMode.LIST))

        rule.onNodeWithText("Alpha").performTouchInput { longClick() }
        rule.onNodeWithText(str(R.string.library_unsubscribe)).performClick()
        rule.onNodeWithText(str(R.string.library_unsubscribe_title)).assertExists()
        rule.onNodeWithText(str(R.string.library_unsubscribe_message, "Alpha")).assertExists()
        rule.onNodeWithText(str(R.string.library_cancel)).performClick()
        assertEquals(emptyList<Long>(), unsubscribed)
        rule.onAllNodesWithText(str(R.string.library_unsubscribe_title)).assertCountEquals(0)

        rule.onNodeWithText("Alpha").performTouchInput { longClick() }
        rule.onNodeWithText(str(R.string.library_unsubscribe)).performClick()
        rule.onNodeWithText(str(R.string.library_unsubscribe)).performClick() // confirm button (menu is closed)
        assertEquals(listOf(7L), unsubscribed)
    }

    @Test
    fun `empty state shows message and Discover button`() {
        render(state(groups = emptyList(), genres = emptyList(), totalCount = 0, isLoading = false))
        rule.onNodeWithText(str(R.string.library_empty)).assertExists()
        rule.onNodeWithText(str(R.string.library_discover)).performClick()
        assertEquals(1, discover)
        rule.onAllNodesWithText("Alpha").assertCountEquals(0)
    }

    @Test
    fun `loading state shows progress indicator and no empty text`() {
        rule.mainClock.autoAdvance = false
        render(state(groups = emptyList(), genres = emptyList(), totalCount = 0, isLoading = true))
        rule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        ).assertExists()
        rule.onAllNodesWithText(str(R.string.library_empty)).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.library_discover)).assertCountEquals(0)
    }
}
