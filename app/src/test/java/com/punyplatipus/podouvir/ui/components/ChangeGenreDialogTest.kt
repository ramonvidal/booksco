package com.punyplatipus.podouvir.ui.components

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.punyplatipus.podouvir.R
import com.punyplatipus.podouvir.domain.model.UNKNOWN_GENRE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChangeGenreDialogTest {
    @get:Rule
    val rule = createComposeRule()

    private val confirmed = mutableListOf<String?>()
    private var dismissed = 0

    private fun str(id: Int, vararg args: Any) =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show(
        podcastTitle: String = "My Show",
        feedGenre: String = "Technology",
        currentOverride: String? = null,
        suggestions: List<String> = emptyList(),
    ) {
        rule.setContent {
            MaterialTheme {
                ChangeGenreDialog(
                    podcastTitle = podcastTitle,
                    feedGenre = feedGenre,
                    currentOverride = currentOverride,
                    suggestions = suggestions,
                    onConfirm = { confirmed += it },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    private fun ComposeContentTestRule.assertShown(text: String) {
        assertTrue("expected '$text' to be shown",
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
    }

    private fun field() = rule.onNode(hasSetTextAction())

    private fun checkTitleAndButtons() {
        rule.assertShown(str(R.string.components_change_genre_title))
        rule.assertShown("My Show")
        rule.assertShown(str(R.string.components_change_genre_field))
        rule.assertShown(str(R.string.components_save))
        rule.assertShown(str(R.string.components_cancel))
    }

    @Test
    fun `shows localized title, podcast title as supporting text, field label, Save and Cancel`() {
        show()
        rule.assertShown("Change genre")
        rule.assertShown("Genre")
        rule.assertShown("Save")
        rule.assertShown("Cancel")
        checkTitleAndButtons()
    }

    @Test
    @Config(qualifiers = "pt")
    fun `shows localized title, podcast title as supporting text, field label, Save and Cancel in Portuguese`() {
        show()
        rule.assertShown("Alterar gênero")
        rule.assertShown("Gênero")
        rule.assertShown("Salvar")
        rule.assertShown("Cancelar")
        checkTitleAndButtons()
    }

    @Test
    fun `field is prefilled with the current override`() {
        show(currentOverride = "Comedy")
        field().assertTextEquals(str(R.string.components_change_genre_field), "Comedy")
    }

    @Test
    fun `field is empty when override is null`() {
        show(currentOverride = null)
        field().assertTextEquals(str(R.string.components_change_genre_field), "")
    }

    @Test
    fun `tapping a suggestion chip fills the field and excludes UNKNOWN_GENRE`() {
        show(suggestions = listOf("News", UNKNOWN_GENRE, "Comedy"))
        rule.onNodeWithText("Comedy").assertExists()
        rule.onAllNodesWithText(str(R.string.components_genre_unknown)).assertCountEquals(0)
        rule.onNodeWithText("News").performClick()
        field().assertTextEquals(str(R.string.components_change_genre_field), "News")
    }

    @Test
    fun `Save returns the trimmed text`() {
        show(currentOverride = null)
        field().performTextInput("  Jazz  ")
        rule.onNodeWithText(str(R.string.components_save)).performClick()
        assertEquals(listOf<String?>("Jazz"), confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    fun `Save with blank text returns null`() {
        show(currentOverride = "Comedy")
        field().performTextClearance()
        field().performTextInput("   ")
        rule.onNodeWithText(str(R.string.components_save)).performClick()
        assertEquals(listOf<String?>(null), confirmed)
    }

    @Test
    fun `Save is disabled while trimmed text equals the current override`() {
        show(currentOverride = "Comedy")
        val save = { rule.onNodeWithText(str(R.string.components_save)) }
        save().assertIsNotEnabled()
        field().performTextReplacement(" Comedy ")
        save().assertIsNotEnabled()
        field().performTextReplacement("Drama")
        save().assertIsEnabled()
    }

    @Test
    fun `Save is disabled with null override and empty field`() {
        show(currentOverride = null)
        rule.onNodeWithText(str(R.string.components_save)).assertIsNotEnabled()
    }

    @Test
    fun `Cancel invokes onDismiss without confirming`() {
        show(currentOverride = "Comedy")
        rule.onNodeWithText(str(R.string.components_cancel)).performClick()
        assertEquals(1, dismissed)
        assertTrue(confirmed.isEmpty())
    }

    @Test
    fun `Use feed genre button is absent without an override`() {
        show(currentOverride = null, feedGenre = "Technology")
        rule.onAllNodesWithText(str(R.string.components_change_genre_use_feed, "Technology"))
            .assertCountEquals(0)
    }

    @Test
    fun `Use feed genre button is shown with an override and calls onConfirm null`() {
        show(currentOverride = "Comedy", feedGenre = "Technology")
        val label = str(R.string.components_change_genre_use_feed, "Technology")
        rule.onNodeWithText(label).performClick()
        assertEquals(listOf<String?>(null), confirmed)
    }

    @Test
    @Config(qualifiers = "pt")
    fun `Use feed genre button is localized in Portuguese`() {
        show(currentOverride = "Comedy", feedGenre = "Technology")
        rule.onNodeWithText("Usar gênero do feed (Technology)").assertExists()
    }

    @Test
    fun `Use feed genre label localizes an unknown feed genre in English`() {
        show(currentOverride = "Comedy", feedGenre = UNKNOWN_GENRE)
        rule.onNodeWithText("Use feed genre (Other)").assertExists()
    }

    @Test
    @Config(qualifiers = "pt")
    fun `Use feed genre label localizes an unknown feed genre in Portuguese`() {
        show(currentOverride = "Comedy", feedGenre = UNKNOWN_GENRE)
        rule.onNodeWithText("Usar gênero do feed (Outros)").assertExists()
    }
}
