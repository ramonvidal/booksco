# 006 Library preferences and custom reorder
Plan item: M2 — Library: sort menu (persisted), grid/list, custom sort — data part
Status: done
Depends on: none (M1 done). Read `docs/tasks/M2-conventions.md`.

## Goal
Persist the user's library settings (sort order, view mode, selected genre chip) with DataStore, and
implement `PodcastRepository.setCustomOrder` (interface method already declared) atomically.

## Files
- `app/src/main/java/com/punyplatipus/podouvir/domain/model/LibrarySettings.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/data/prefs/LibraryPreferences.kt` (new: interface + DataStore impl)
- `app/src/main/java/com/punyplatipus/podouvir/di/PreferencesModule.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/data/db/PodcastDao.kt` (modify: add 2 methods)
- `app/src/main/java/com/punyplatipus/podouvir/data/repository/DefaultPodcastRepository.kt` (modify: implement `setCustomOrder`)
- Tests: `data/prefs/DataStoreLibraryPreferencesTest.kt` (JVM: `PreferenceDataStoreFactory.create(scope = TestScope(...).backgroundScope) { tmpFolder.newFile("t.preferences_pb") }`),
  add reorder cases to the existing `data/db/PodcastDaoTest.kt` and `data/repository/DefaultPodcastRepositoryTest.kt`.

## Public API
```kotlin
package com.punyplatipus.podouvir.domain.model
enum class ViewMode { GRID, LIST }
data class LibrarySettings(
    val sortOrder: SortOrder = SortOrder.TITLE,
    val viewMode: ViewMode = ViewMode.GRID,
    val selectedGenre: String? = null,   // null = "All"
)
```
```kotlin
package com.punyplatipus.podouvir.data.prefs
interface LibraryPreferences {
    val settings: Flow<LibrarySettings>
    suspend fun setSortOrder(order: SortOrder)
    suspend fun setViewMode(mode: ViewMode)
    /** null or blank clears the selection ("All"). */
    suspend fun setSelectedGenre(genre: String?)
}
class DataStoreLibraryPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LibraryPreferences
```
```kotlin
// PodcastDao additions
@Query("UPDATE podcasts SET customOrder = :order WHERE id = :id")
suspend fun setCustomOrder(id: Long, order: Int)

/** customOrder = index for each id, in one transaction. */
@Transaction
suspend fun reorder(orderedIds: List<Long>) { orderedIds.forEachIndexed { index, id -> setCustomOrder(id, index) } }
```
`PreferencesModule` (SingletonComponent): `@Singleton DataStore<Preferences>` via
`PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("library_settings") }` (`@ApplicationContext`),
and `@Binds @Singleton LibraryPreferences`.

## Acceptance criteria
- Fresh store emits `LibrarySettings()` defaults.
- Each setter persists and `settings` re-emits the new value; other fields unchanged.
- Enums stored by `name`; an unknown stored name (e.g. from a future version) falls back to the default, no crash.
- `setSelectedGenre(null)` and `setSelectedGenre("  ")` both clear the selection.
- `PodcastDao.reorder` sets `customOrder` to each id's index; ids not in the list keep their value; unknown ids ignored.
- `DefaultPodcastRepository.setCustomOrder` delegates to `reorder` on the IO dispatcher; `observeLibrary` re-emits.

## Test plan

### Test files
- `app/src/test/java/com/punyplatipus/podouvir/data/prefs/DataStoreLibraryPreferencesTest.kt` (new)
- `app/src/test/java/com/punyplatipus/podouvir/data/db/PodcastDaoTest.kt` (add cases; existing file)
- `app/src/test/java/com/punyplatipus/podouvir/data/repository/DefaultPodcastRepositoryTest.kt` (add cases; existing file)

### Fixtures/fakes
- `TemporaryFolder` rule plus `PreferenceDataStoreFactory.create(scope = TestScope(...).backgroundScope) { tmp.newFile("t.preferences_pb") }` per test, wrapped in `DataStoreLibraryPreferences`.
- For the unknown-name cases, write raw values through the same `DataStore<Preferences>` with `stringPreferencesKey`. The key names must match the production keys, so either make them `internal` constants or discover them from the implementation. Do not hard-code guesses.
- Reuse the existing Podcast entity builders and in-memory Room setup in `PodcastDaoTest` and `DefaultPodcastRepositoryTest`.
- For the repository case, reuse the existing dispatcher-recording setup from #005 (a recording IO dispatcher). Use the in-memory DAO already used there.

### Cases

All cases are `unit` (JVM) unless marked `room`. The existing `PodcastDaoTest` and `DefaultPodcastRepositoryTest` already run Room in-memory on the JVM, so follow whatever level they use. If they use Robolectric, mark those cases `room`.

**DataStoreLibraryPreferences**

| # | name | given | when | then |
|---|------|-------|------|------|
| 1 | `fresh store emits default settings` | an empty store | collect `settings.first()` | equals `LibrarySettings()` (TITLE, GRID, null) |
| 2 | `each setter persists and re-emits without touching other fields` | a fresh store, `settings` collected with Turbine | call `setSortOrder(non-default)`, `setViewMode(LIST)`, `setSelectedGenre("Comedy")` in turn | each call emits a new value with only that field changed and the earlier fields kept; the final value has all three set |
| 3 | `settings survive a new DataStoreLibraryPreferences over the same store` | values set via the first instance | build a second instance on the same DataStore | `settings.first()` returns the persisted values |
| 4 | `enums are stored by name` | `setSortOrder(x)` and `setViewMode(LIST)` | read raw preferences | the stored strings equal `x.name` and `"LIST"` |
| 5 | `unknown stored enum names fall back to defaults without crashing` | raw prefs hold sort `"FUTURE_SORT"` and view mode `"CAROUSEL"` plus a valid genre | collect `settings.first()` | sortOrder is TITLE, viewMode is GRID, the genre is kept, and nothing is thrown |
| 6 | `null or blank genre clears the selection` | genre previously `"Comedy"` | call `setSelectedGenre(null)`; set `"Comedy"` again; call `setSelectedGenre("  ")` | `selectedGenre` is null both times, and sortOrder and viewMode are unchanged |

**PodcastDao.reorder / setCustomOrder** (`room`, added to the existing `PodcastDaoTest`)

| # | name | given | when | then |
|---|------|-------|------|------|
| 7 | `reorder assigns each id its list index` | podcasts A, B, C inserted | `reorder([C, A, B])` | C=0, A=1, B=2 |
| 8 | `reorder leaves ids not in the list unchanged` | A, B, C with known customOrder values | `reorder([B, A])` | B=0, A=1, and C keeps its original value |
| 9 | `reorder ignores unknown ids and does not throw` | A, B inserted | `reorder([A, 999, B])` | A=0, B=2, no row 999 is created, and no exception |
| 10 | `reorder with empty list is a no-op` | A, B with known orders | `reorder(emptyList())` | the orders are unchanged |
| 11 | `reorder is atomic` | A, B inserted, with a failure forced for the second update (a DAO subclass or spy that throws on the 2nd `setCustomOrder`) | `reorder([B, A])` | the exception propagates and A and B keep their original orders (rolled back). If a failure cannot be injected without a test-only seam, drop this case and note it under Out of scope. |
| 12 | `setCustomOrder updates only the target row` | A, B inserted | `setCustomOrder(A.id, 7)` | A=7, B unchanged |

**DefaultPodcastRepository.setCustomOrder** (same level as the existing repository tests)

| # | name | given | when | then |
|---|------|-------|------|------|
| 13 | `setCustomOrder persists the order and observeLibrary re-emits it` | A, B, C subscribed, `observeLibrary(SortOrder.CUSTOM)` collected with Turbine | `setCustomOrder([C, A, B])` | a new emission lists C, A, B |
| 14 | `setCustomOrder runs on the IO dispatcher` | the recording dispatcher from #005 | `setCustomOrder([...])` | the recorded dispatcher was used for the DAO write |
| 15 | `setCustomOrder with ids not in the library does not throw` | an empty library | `setCustomOrder([1, 2])` | completes normally and the library stays empty |

### Acceptance criteria coverage
- Defaults on a fresh store: 1.
- Each setter persists and re-emits, other fields unchanged: 2 and 3.
- Enums stored by name, unknown name falls back to the default: 4 and 5.
- Null or blank genre clears the selection: 6.
- `reorder` sets index, leaves other ids alone, ignores unknown ids: 7, 8, 9, plus 10, 11 and 12 for boundaries and atomicity.
- `setCustomOrder` delegates on IO and `observeLibrary` re-emits: 13, 14, 15.

### Level summary
- `unit`: 9 cases (1-6, 13-15). If the repository tests run Room under Robolectric, 13 and 15 move to `room`.
- `room`: 6 cases (7-12).
- `ui`: 0.

### Out of scope
- `PreferencesModule` Hilt wiring: it is declarative and gets covered by the app build and the M2 navigation and instrumented tests.
- Concurrent setters from several coroutines: DataStore guarantees atomic `edit`, and testing it only re-tests the library.
- UI and ViewModel use of the settings: covered in tasks 009 and 010.
- Real-device DataStore file corruption handling: the task does not specify it.

## Implementation notes
Status: done
- New: `domain/model/LibrarySettings.kt`, `data/prefs/LibraryPreferences.kt`, `di/PreferencesModule.kt`.
- Modified: `PodcastDao` (`setCustomOrder`, `reorder` with `@Transaction`), `DefaultPodcastRepository.setCustomOrder` (withContext(ioDispatcher) -> `reorder`).
- Tests can reference internal keys in `LibraryPreferences.kt`: `SORT_ORDER_KEY`, `VIEW_MODE_KEY`, `SELECTED_GENRE_KEY` (string keys named `library_sort_order`, `library_view_mode`, `library_selected_genre`; name constants `KEY_NAME_*`).
- Clearing the genre removes the key; a blank stored genre also reads as null. Unknown enum names fall back to defaults.
- `observeLibrary()` in the interface still has no `SortOrder` parameter (test-plan cases 13 mention `observeLibrary(SortOrder.CUSTOM)`); not changed, outside this task. Tester should adapt (e.g. sort via `sortPodcasts(CUSTOM)`).

## Test results
Not run: no JDK/SDK here (`java` not found); CI runs them. Reviewed by eye against the implementation: no expected failures.

| # | Case | Result |
|---|------|--------|
| 1 | fresh store emits default settings | NOT RUN |
| 2 | each setter persists and re-emits without touching other fields | NOT RUN |
| 3 | settings survive a new DataStoreLibraryPreferences over the same store | NOT RUN |
| 4 | enums are stored by name | NOT RUN |
| 5 | unknown stored enum names fall back to defaults without crashing | NOT RUN |
| 6 | null or blank genre clears the selection | NOT RUN |
| 7 | reorder assigns each id its list index | NOT RUN |
| 8 | reorder leaves ids not in the list unchanged | NOT RUN |
| 9 | reorder ignores unknown ids and does not throw | NOT RUN |
| 10 | reorder with empty list is a no-op | NOT RUN |
| 11 | reorder is atomic | DROPPED (needs a production seam to inject a failure; out of scope) |
| 12 | setCustomOrder updates only the target row | NOT RUN |
| 13 | setCustomOrder persists the order and observeLibrary re-emits it (uses `sortPodcasts(CUSTOM)`) | NOT RUN |
| 14 | setCustomOrder runs on the IO dispatcher (RecordingDispatcher) | NOT RUN |
| 15 | setCustomOrder with ids not in the library does not throw | NOT RUN |
