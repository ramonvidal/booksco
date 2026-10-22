package com.punyplatipus.podouvir.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastArtworkTest {
    @get:Rule
    val rule = createComposeRule()

    private val tag = "podcastArtworkPlaceholder"

    @Test
    fun `placeholder is shown with no own contentDescription when url is null`() {
        rule.setContent { PodcastArtwork(artworkUrl = null, contentDescription = "Cover") }
        rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        val config = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
        assertTrue(!config.contains(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `placeholder stays decorative and does not duplicate the supplied contentDescription`() {
        rule.setContent { PodcastArtwork(artworkUrl = null, contentDescription = "Cover") }
        val matches = rule.onAllNodesWithContentDescription("Cover", useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("at most one node may carry the description", matches.size <= 1)
        // The placeholder (and its icon child) must not carry it.
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().children.forEach {
            assertTrue(!it.config.contains(SemanticsProperties.ContentDescription))
        }
    }

    @Test
    fun `null contentDescription is accepted`() {
        rule.setContent { PodcastArtwork(artworkUrl = null, contentDescription = null) }
        rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Cover", useUnmergedTree = true).assertCountEquals(0)
    }
}
