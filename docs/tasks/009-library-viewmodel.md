# 009 Library ViewModel: grouping, filtering, sorting, reorder
Plan item: M2 — Library: genre chips, grouped sections, sort menu, grid/list, custom reorder — state part
Status: done
Depends on: 006 (LibraryPreferences, LibrarySettings, setCustomOrder). Read `docs/tasks/M2-conventions.md`.

## Goal
All Library screen logic in a testable ViewModel.

## Files
- `app/src/main/java/com/punyplatipus/podouvir/ui/library/LibraryViewModel.kt` (new)
- Tests: `ui/library/LibraryViewModelTest.kt` (JVM; FakePodcastRepository; write a `FakeLibraryPreferences` in `testutil/`).

## Public API
```kotlin
package com.punyplatipus.podouvir.ui.library

data class GenreGroup(val genre: String, val podcasts: List<Podcast>)

data class LibraryUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val genres: List<String> = emptyList(),        // all genres in library, same order as groupByGenre keys
    val selectedGenre: String? = null,             // null = All
    val sortOrder: SortOrder = SortOrder.TITLE,
    val viewMode: ViewMode = ViewMode.GRID,
    val groups: List<GenreGroup> = emptyList(),    // filtered by selectedGenre
    val isReordering: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: PodcastRepository,
    private val preferences: LibraryPreferences,
) : ViewModel() {
    val uiState: StateFlow<LibraryUiState>
    fun selectGenre(genre: String?)
    fun setSortOrder(order: SortOrder)
    fun toggleViewMode()
    fun startReorder()
    fun finishReorder()
    fun moveUp(podcastId: Long)
    fun moveDown(podcastId: Long)
    fun changeGenre(podcastId: Long, genre: String?)
    fun unsubscribe(podcastId: Long)
}
```

## Acceptance criteria
- `isLoading` true until the first library + settings emission, then false.
- `groups` = `groupByGenre(sortOrder)` of the library; when `selectedGenre` is non-null only that group is present.
- A persisted `selectedGenre` that matches no current group (case-insensitive) is treated as null (All) in the state.
- `selectGenre`, `setSortOrder`, `toggleViewMode` (GRID↔LIST) write to `LibraryPreferences`; state follows the preferences flow.
- `startReorder()` sets sort order to CUSTOM (persisted) and `isReordering=true`; `finishReorder()` sets it false.
- `moveUp`/`moveDown` (only while reordering; otherwise ignored): swap the podcast with its previous/next neighbour
  **in the same genre group** (CUSTOM order); first/last in group → no-op (no repository call). The repository receives the
  full list of ALL podcast ids in the new custom order via `setCustomOrder`.
- `changeGenre` → `repository.setGenreOverride`; `unsubscribe` → `repository.unsubscribe`.

## Test plan

**Test files**
- `app/src/test/java/com/punyplatipus/podouvir/ui/library/LibraryViewModelTest.kt` (all cases)
- `app/src/test/java/com/punyplatipus/podouvir/testutil/FakeLibraryPreferences.kt` (new fake, see below)

**Level**: all `unit` (JVM). No Room/UI needed: repository and preferences are faked; `MainDispatcherRule` (StandardTestDispatcher) + Turbine on `uiState`; call `advanceUntilIdle()` after events. Collect `uiState` (WhileSubscribed) in every test.

**Fixtures/fakes**
- `FakePodcastRepository` (provided): drive library via `podcasts` MutableStateFlow; assert via `customOrderCalls`, `genreOverrideCalls`, `unsubscribeCalls`.
- `FakeLibraryPreferences(initial: LibrarySettings = LibrarySettings())`: `MutableStateFlow<LibrarySettings>` exposed as `settings`; setters update the flow (blank genre to null) and record calls in `sortOrderCalls`, `viewModeCalls`, `selectedGenreCalls`. A `Channel`/gate or a flow that is not yet emitting (e.g. `MutableSharedFlow` variant, constructor flag `emitInitial = false`) to test the loading state.
- Podcast builders: a small helper in the test creating `Podcast(id, title, genre, genreOverride, customOrder)` for e.g. Technology (ids 1,2,3 customOrder 0,1,2), News (ids 4,5), and one with blank genre (UNKNOWN_GENRE group).

**Cases** (`LibraryViewModel`; 14 total)

| # | name | given | when | then |
|---|------|-------|------|------|
| 1 | `initial state is loading until library and settings first emit` | prefs not yet emitting, repo has podcasts | collect `uiState`; then prefs emit | first item `isLoading=true`, `groups` empty; after emission `isLoading=false` |
| 2 | `empty library yields not loading, zero count and no groups` | repo emits empty list | collect | `isLoading=false`, `totalCount=0`, `genres` and `groups` empty |
| 3 | `groups equal groupByGenre of the library for the current sort order` | library of 5 podcasts in 2 genres, settings sort=TITLE | collect | `groups` matches `library.groupByGenre(TITLE)` (same genre order and podcast order), `totalCount=5`, `genres` = group keys in the same order, `viewMode`/`sortOrder` mirror settings |
| 4 | `selected genre keeps only that group while genres still lists all` | settings selectedGenre="News" | collect | `groups` has a single `News` group; `genres` contains all genres; `selectedGenre="News"` |
| 5 | `selected genre matches case-insensitively` | settings selectedGenre="news", library genre "News" | collect | single News group present, state not reset to All |
| 6 | `stale selected genre is treated as All` | settings selectedGenre="Comedy" (not in library) | collect; then library gains a Comedy podcast | initially `selectedGenre=null` and all groups shown; after gain, selection takes effect (Comedy only). Preferences are not rewritten by the VM for the stale case |
| 7 | `selectGenre, setSortOrder and toggleViewMode write to preferences and state follows` | defaults | `selectGenre("News")`, `setSortOrder(NEWEST...)` (any non-default SortOrder), `toggleViewMode()` | fake prefs record each call; state emits `selectedGenre="News"`, new `sortOrder`, `viewMode=LIST`; `selectGenre(null)` returns to All |
| 8 | `toggleViewMode flips between grid and list` | viewMode GRID | toggle twice | prefs get LIST then GRID; state ends GRID |
| 9 | `library changes re-emit grouped state` | collecting | `repo.podcasts` gains/loses a podcast (incl. removal of the last podcast in a genre) | `totalCount`, `genres`, `groups` update; empty group disappears |
| 10 | `startReorder sets custom sort order persisted and isReordering true; finishReorder clears it` | sort=TITLE | `startReorder()`, then `finishReorder()` | prefs `setSortOrder(CUSTOM)` called, state `sortOrder=CUSTOM`, `isReordering=true`; after finish `isReordering=false`, sortOrder stays CUSTOM |
| 11 | `moveDown swaps with next neighbour in same genre and sends all ids in new order` | reordering, CUSTOM; Technology [1,2,3], News [4,5] | `moveDown(1)` | exactly one `customOrderCalls` entry containing ALL ids (1..5) with 1 and 2 swapped and News order intact, e.g. `[2,1,3,4,5]`; state group order follows via fake update |
| 12 | `moveUp swaps with previous neighbour and never crosses genre groups` | reordering; Technology [1,2,3], News [4,5] | `moveUp(3)`; then `moveUp(4)` (first of News, previous overall is 3) | first yields full list with 3 and 2 swapped; second is a no-op (no extra `customOrderCalls`) |
| 13 | `moveUp on first, moveDown on last, unknown id, or when not reordering do not call the repository` | (a) reordering: `moveUp(1)`, `moveDown(3)`, `moveUp(999)`; (b) not reordering: `moveUp(2)`, `moveDown(2)` | invoke | `customOrderCalls` stays empty in both |
| 14 | `changeGenre and unsubscribe delegate to the repository` | library with podcast 1 | `changeGenre(1,"Comedy")`, `changeGenre(1,null)`, `unsubscribe(2)` | `genreOverrideCalls == [1 to "Comedy", 1 to null]`, `unsubscribeCalls == [2]`; state updates (podcast regrouped / removed) |

Acceptance coverage: loading -> 1,2; grouping/filtering -> 3,4,9; stale/case-insensitive genre -> 5,6; prefs writes -> 7,8; reorder start/finish -> 10; move up/down rules -> 11,12,13; changeGenre/unsubscribe -> 14.

**Out of scope**
- `groupByGenre` / `SortOrder` ordering internals (covered by existing domain tests; case 3 compares against it only).
- DataStore persistence (task 006 tests) and Library composables (task 010).
- Hilt wiring and `SharingStarted` timeout behaviour (framework behaviour).

## Implementation notes
- Added `ui/library/LibraryViewModel.kt` (GenreGroup, LibraryUiState, LibraryViewModel). Not built (no SDK).
- `uiState` = combine(repository.observeLibrary(), preferences.settings, local reordering flow). `selectedGenre` in state is the matching group's canonical name (case-insensitive match), null if stale; prefs never rewritten.
- `toggleViewMode` reads `preferences.settings.first()` then writes the flipped mode. Moves read `observeLibrary().first()`, swap within the CUSTOM-ordered genre group, and send all ids (groups flattened in group order) via `setCustomOrder`. Ignored unless `isReordering`.
- Tester: FakeLibraryPreferences.settings must emit for `first()` to complete (in toggle); with emitInitial=false toggling would suspend.

## Test results
Files: `app/src/test/java/com/punyplatipus/podouvir/ui/library/LibraryViewModelTest.kt`, `app/src/test/java/com/punyplatipus/podouvir/testutil/FakeLibraryPreferences.kt`.
Not run: no SDK/JDK (`java` not found). CI runs them. Reviewed by eye against the implementation: no spec violations found.

| # | case | result |
|---|------|--------|
| 1 | initial state is loading until library and settings first emit | NOT RUN |
| 2 | empty library yields not loading, zero count and no groups | NOT RUN |
| 3 | groups equal groupByGenre of the library for the current sort order | NOT RUN |
| 4 | selected genre keeps only that group while genres still lists all | NOT RUN |
| 5 | selected genre matches case-insensitively | NOT RUN |
| 6 | stale selected genre is treated as All | NOT RUN |
| 7 | selectGenre, setSortOrder and toggleViewMode write to preferences and state follows | NOT RUN |
| 8 | toggleViewMode flips between grid and list | NOT RUN |
| 9 | library changes re-emit grouped state | NOT RUN |
| 10 | startReorder / finishReorder | NOT RUN |
| 11 | moveDown swaps with next neighbour, sends all ids | NOT RUN |
| 12 | moveUp swaps with previous, never crosses groups | NOT RUN |
| 13 | no-op moves do not call the repository | NOT RUN |
| 14 | changeGenre and unsubscribe delegate | NOT RUN |
| 15 | podcasts without a genre land in last Other group (added by tester) | NOT RUN |

Notes: setCustomOrder gets ids flattened in group order (News before Technology, A-Z), so case 11 yields `[4,5,2,1,3]`, not the plan's example `[2,1,3,4,5]`; tests assert per-group relative order plus each id exactly once. MainDispatcherRule is given an explicit StandardTestDispatcher shared with runTest.
