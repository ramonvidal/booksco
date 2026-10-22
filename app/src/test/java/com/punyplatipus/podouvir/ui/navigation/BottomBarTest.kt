package com.punyplatipus.podouvir.ui.navigation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BottomBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun label(tab: TopLevelTab): String =
        ApplicationProvider.getApplicationContext<Context>().getString(tab.labelRes)

    private fun node(tab: TopLevelTab) =
        composeRule.onNodeWithText(label(tab), useUnmergedTree = false)

    private fun render(selected: TopLevelTab?, onSelect: (TopLevelTab) -> Unit = {}) {
        composeRule.setContent { BottomBar(selected = selected, onSelect = onSelect) }
    }

    @Test
    fun `shows one item per tab with its label`() {
        render(TopLevelTab.LIBRARY)
        TopLevelTab.entries.forEach { node(it).assertIsDisplayed() }
    }

    @Test
    fun `marks only the selected tab as selected`() {
        render(TopLevelTab.DISCOVER)
        node(TopLevelTab.DISCOVER).assertIsSelected()
        node(TopLevelTab.LIBRARY).assertIsNotSelected()
        node(TopLevelTab.QUEUE).assertIsNotSelected()
    }

    @Test
    fun `highlights no tab when selected is null`() {
        render(null)
        TopLevelTab.entries.forEach {
            node(it).assertIsNotSelected()
            node(it).assertIsDisplayed()
        }
    }

    @Test
    fun `clicking a tab invokes onSelect with that tab`() {
        val recorded = mutableListOf<TopLevelTab>()
        render(TopLevelTab.LIBRARY) { recorded += it }
        node(TopLevelTab.QUEUE).performClick()
        assertEquals(listOf(TopLevelTab.QUEUE), recorded)
    }

    @Test
    fun `clicking the already selected tab still reports it once`() {
        val recorded = mutableListOf<TopLevelTab>()
        render(TopLevelTab.LIBRARY) { recorded += it }
        node(TopLevelTab.LIBRARY).performClick()
        assertEquals(listOf(TopLevelTab.LIBRARY), recorded)
    }
}
