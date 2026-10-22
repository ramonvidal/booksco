package com.punyplatipus.podouvir.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.cash.turbine.test
import com.punyplatipus.podouvir.domain.model.LibrarySettings
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreLibraryPreferencesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun newStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("t.preferences_pb").also { it.delete() } }

    @Test fun `fresh store emits default settings`() = runTest {
        val prefs = DataStoreLibraryPreferences(newStore(backgroundScope))
        val s = prefs.settings.first()
        assertEquals(LibrarySettings(), s)
        assertEquals(SortOrder.TITLE, s.sortOrder)
        assertEquals(ViewMode.GRID, s.viewMode)
        assertNull(s.selectedGenre)
    }

    @Test fun `each setter persists and re-emits without touching other fields`() = runTest {
        val prefs = DataStoreLibraryPreferences(newStore(backgroundScope))
        prefs.settings.test {
            assertEquals(LibrarySettings(), awaitItem())

            prefs.setSortOrder(SortOrder.CUSTOM)
            val a = LibrarySettings(sortOrder = SortOrder.CUSTOM)
            assertEquals(a, awaitItem())

            prefs.setViewMode(ViewMode.LIST)
            val b = a.copy(viewMode = ViewMode.LIST)
            assertEquals(b, awaitItem())

            prefs.setSelectedGenre("Comedy")
            val c = b.copy(selectedGenre = "Comedy")
            assertEquals(c, awaitItem())
            assertEquals(LibrarySettings(SortOrder.CUSTOM, ViewMode.LIST, "Comedy"), c)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `settings survive a new DataStoreLibraryPreferences over the same store`() = runTest {
        val store = newStore(backgroundScope)
        val first = DataStoreLibraryPreferences(store)
        first.setSortOrder(SortOrder.DATE_ADDED)
        first.setViewMode(ViewMode.LIST)
        first.setSelectedGenre("News")
        val second = DataStoreLibraryPreferences(store)
        assertEquals(LibrarySettings(SortOrder.DATE_ADDED, ViewMode.LIST, "News"), second.settings.first())
    }

    @Test fun `enums are stored by name`() = runTest {
        val store = newStore(backgroundScope)
        val prefs = DataStoreLibraryPreferences(store)
        prefs.setSortOrder(SortOrder.RECENTLY_UPDATED)
        prefs.setViewMode(ViewMode.LIST)
        val raw = store.data.first()
        assertEquals("RECENTLY_UPDATED", raw[SORT_ORDER_KEY])
        assertEquals(SortOrder.RECENTLY_UPDATED.name, raw[SORT_ORDER_KEY])
        assertEquals("LIST", raw[VIEW_MODE_KEY])
    }

    @Test fun `unknown stored enum names fall back to defaults without crashing`() = runTest {
        val store = newStore(backgroundScope)
        store.edit {
            it[SORT_ORDER_KEY] = "FUTURE_SORT"
            it[VIEW_MODE_KEY] = "CAROUSEL"
            it[SELECTED_GENRE_KEY] = "Comedy"
        }
        val s = DataStoreLibraryPreferences(store).settings.first()
        assertEquals(SortOrder.TITLE, s.sortOrder)
        assertEquals(ViewMode.GRID, s.viewMode)
        assertEquals("Comedy", s.selectedGenre)
    }

    @Test fun `null or blank genre clears the selection`() = runTest {
        val prefs = DataStoreLibraryPreferences(newStore(backgroundScope))
        prefs.setSortOrder(SortOrder.CUSTOM)
        prefs.setViewMode(ViewMode.LIST)
        prefs.setSelectedGenre("Comedy")
        assertEquals("Comedy", prefs.settings.first().selectedGenre)

        prefs.setSelectedGenre(null)
        var s = prefs.settings.first()
        assertNull(s.selectedGenre)
        assertEquals(SortOrder.CUSTOM, s.sortOrder)
        assertEquals(ViewMode.LIST, s.viewMode)

        prefs.setSelectedGenre("Comedy")
        assertEquals("Comedy", prefs.settings.first().selectedGenre)
        prefs.setSelectedGenre("  ")
        s = prefs.settings.first()
        assertNull(s.selectedGenre)
        assertEquals(SortOrder.CUSTOM, s.sortOrder)
        assertEquals(ViewMode.LIST, s.viewMode)
    }
}
