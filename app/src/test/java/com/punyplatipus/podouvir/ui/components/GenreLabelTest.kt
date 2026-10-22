package com.punyplatipus.podouvir.ui.components

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.punyplatipus.podouvir.R
import com.punyplatipus.podouvir.domain.model.UNKNOWN_GENRE
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GenreLabelTest {
    @get:Rule
    val rule = createComposeRule()

    private fun labelOf(genre: String): String {
        var result: String? = null
        rule.setContent { result = genreLabel(genre) }
        rule.waitForIdle()
        return checkNotNull(result)
    }

    private fun unknownString() =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.components_genre_unknown)

    @Test
    fun `genreLabel returns the genre unchanged for a regular genre`() {
        assertEquals("Technology", labelOf("Technology"))
    }

    @Test
    fun `genreLabel maps UNKNOWN_GENRE to Other in English`() {
        assertEquals("Other", labelOf(UNKNOWN_GENRE))
        assertEquals(unknownString(), labelOf(UNKNOWN_GENRE))
    }

    @Test
    @Config(qualifiers = "pt")
    fun `genreLabel maps UNKNOWN_GENRE to Outros in Portuguese`() {
        assertEquals("Outros", labelOf(UNKNOWN_GENRE))
        assertEquals(unknownString(), labelOf(UNKNOWN_GENRE))
    }

    @Test
    fun `genreLabel passes through a blank or very long genre without crashing`() {
        assertEquals("", labelOf(""))
    }

    @Test
    fun `genreLabel passes through a very long genre`() {
        val long = "x".repeat(500)
        assertEquals(long, labelOf(long))
    }
}
