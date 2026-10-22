package com.punyplatipus.podouvir.testutil

import com.punyplatipus.podouvir.data.prefs.LibraryPreferences
import com.punyplatipus.podouvir.domain.model.LibrarySettings
import com.punyplatipus.podouvir.domain.model.SortOrder
import com.punyplatipus.podouvir.domain.model.ViewMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * In-memory LibraryPreferences. With emitInitial = false, [settings] emits nothing until [release] is called
 * (used to test the loading state). Note: anything awaiting settings.first() suspends until released.
 */
class FakeLibraryPreferences(
    initial: LibrarySettings = LibrarySettings(),
    emitInitial: Boolean = true,
) : LibraryPreferences {
    private val state = MutableStateFlow(initial)
    private val gate = CompletableDeferred<Unit>().also { if (emitInitial) it.complete(Unit) }

    override val settings: Flow<LibrarySettings> = flow {
        gate.await()
        emitAll(state)
    }

    val current: LibrarySettings get() = state.value
    val sortOrderCalls = mutableListOf<SortOrder>()
    val viewModeCalls = mutableListOf<ViewMode>()
    val selectedGenreCalls = mutableListOf<String?>()

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun setSortOrder(order: SortOrder) {
        sortOrderCalls += order
        state.value = state.value.copy(sortOrder = order)
    }

    override suspend fun setViewMode(mode: ViewMode) {
        viewModeCalls += mode
        state.value = state.value.copy(viewMode = mode)
    }

    override suspend fun setSelectedGenre(genre: String?) {
        selectedGenreCalls += genre
        state.value = state.value.copy(selectedGenre = genre?.takeIf { it.isNotBlank() })
    }
}
