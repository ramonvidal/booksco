package com.punyplatipus.podouvir.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.punyplatipus.podouvir.domain.model.LibrarySettings
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface LibraryPreferences {
    val settings: Flow<LibrarySettings>
    suspend fun setSortOrder(order: SortOrder)
    suspend fun setViewMode(mode: ViewMode)

    /** null or blank clears the selection ("All"). */
    suspend fun setSelectedGenre(genre: String?)
}

internal const val KEY_NAME_SORT_ORDER = "library_sort_order"
internal const val KEY_NAME_VIEW_MODE = "library_view_mode"
internal const val KEY_NAME_SELECTED_GENRE = "library_selected_genre"

internal val SORT_ORDER_KEY = stringPreferencesKey(KEY_NAME_SORT_ORDER)
internal val VIEW_MODE_KEY = stringPreferencesKey(KEY_NAME_VIEW_MODE)
internal val SELECTED_GENRE_KEY = stringPreferencesKey(KEY_NAME_SELECTED_GENRE)

class DataStoreLibraryPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LibraryPreferences {

    override val settings: Flow<LibrarySettings> = dataStore.data.map { prefs ->
        val defaults = LibrarySettings()
        LibrarySettings(
            sortOrder = enumOrDefault(prefs[SORT_ORDER_KEY], defaults.sortOrder),
            viewMode = enumOrDefault(prefs[VIEW_MODE_KEY], defaults.viewMode),
            selectedGenre = prefs[SELECTED_GENRE_KEY]?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun setSortOrder(order: SortOrder) {
        dataStore.edit { it[SORT_ORDER_KEY] = order.name }
    }

    override suspend fun setViewMode(mode: ViewMode) {
        dataStore.edit { it[VIEW_MODE_KEY] = mode.name }
    }

    override suspend fun setSelectedGenre(genre: String?) {
        dataStore.edit { prefs ->
            val value = genre?.takeIf { it.isNotBlank() }
            if (value == null) prefs.remove(SELECTED_GENRE_KEY) else prefs[SELECTED_GENRE_KEY] = value
        }
    }
}

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
