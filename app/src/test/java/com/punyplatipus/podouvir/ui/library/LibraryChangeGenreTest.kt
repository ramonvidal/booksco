package com.punyplatipus.podouvir.ui.library

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.punyplatipus.podouvir.R
import com.punyplatipus.podouvir.domain.model.Podcast
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.ViewMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Kept apart from LibraryScreenTest: with its tall-screen qualifier this flow never went idle in Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryChangeGenreTest {
    @get:Rule
    val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any) =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    @Test
    fun `long press then Change genre opens the dialog and reset confirms null`() {
        val changed = mutableListOf<Pair<Long, String?>>()
        val podcast = Podcast(id = 7, feedUrl = "https://e.x/7", title = "Alpha", genre = "Technology", genreOverride = "Comedy")
        rule.setContent {
            LibraryScreen(
                state = LibraryUiState(
                    isLoading = false,
                    totalCount = 1,
                    genres = listOf("Comedy"),
                    sortOrder = SortOrder.TITLE,
                    viewMode = ViewMode.LIST,
                    groups = listOf(GenreGroup("Comedy", listOf(podcast))),
                ),
                onOpenPodcast = {}, onDiscover = {}, onSelectGenre = {}, onSortOrder = {}, onToggleViewMode = {},
                onStartReorder = {}, onFinishReorder = {}, onMoveUp = {}, onMoveDown = {},
                onChangeGenre = { id, genre -> changed += id to genre }, onUnsubscribe = {},
            )
        }

        rule.onNodeWithText("Alpha").performTouchInput { longClick() }
        rule.onNodeWithText(str(R.string.library_change_genre)).performClick()
        rule.onNode(hasSetTextAction()).assertExists()
        rule.onNodeWithText(str(R.string.components_change_genre_use_feed, "Technology")).performClick()

        assertEquals(listOf<Pair<Long, String?>>(7L to null), changed)
    }
}
