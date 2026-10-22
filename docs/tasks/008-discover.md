# 008 Discover screen: search, subscribe, add by URL
Plan item: M2 — Discover: search + subscribe + add by URL
Status: done
Depends on: 007 (uses PodcastArtwork, genreLabel). Read `docs/tasks/M2-conventions.md`.

## Goal
Let the user search podcasts (iTunes via `PodcastRepository.search`), subscribe with one tap, and add a feed by URL.

## Files
- `app/src/main/java/com/punyplatipus/podouvir/ui/discover/DiscoverViewModel.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/ui/discover/DiscoverScreen.kt` (new: `DiscoverRoute` + `DiscoverScreen` + `AddByUrlDialog`)
- `res/values/strings_discover.xml`, `res/values-pt/strings_discover.xml` (new)
- Tests: `ui/discover/DiscoverViewModelTest.kt` (JVM, FakePodcastRepository, MainDispatcherRule, virtual time for debounce),
  `ui/discover/DiscoverScreenTest.kt` (Robolectric compose, stateless screen)

## Public API
```kotlin
package com.punyplatipus.podouvir.ui.discover

enum class SubscriptionStatus { NOT_SUBSCRIBED, SUBSCRIBING, SUBSCRIBED }
data class DiscoverItem(val result: PodcastSearchResult, val status: SubscriptionStatus)
enum class DiscoverMessage { SEARCH_FAILED, SUBSCRIBE_FAILED, SUBSCRIBED, INVALID_URL }

data class DiscoverUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,      // true after the first completed search for the current non-blank query
    val items: List<DiscoverItem> = emptyList(),
    val isAddingByUrl: Boolean = false,
    val message: DiscoverMessage? = null,
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(private val repository: PodcastRepository) : ViewModel() {
    val uiState: StateFlow<DiscoverUiState>
    fun onQueryChange(query: String)
    fun subscribe(result: PodcastSearchResult)
    fun addByUrl(url: String)
    fun messageShown()
}

@Composable fun DiscoverRoute(modifier: Modifier = Modifier, viewModel: DiscoverViewModel = hiltViewModel())
@Composable fun DiscoverScreen(
    state: DiscoverUiState,
    onQueryChange: (String) -> Unit,
    onSubscribe: (PodcastSearchResult) -> Unit,
    onAddByUrl: (String) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
)
```

## Acceptance criteria
- `onQueryChange` updates `query` immediately; search runs 400 ms after the last change (debounce) with the trimmed query;
  a newer query cancels an in-flight search; blank query clears items, `hasSearched=false`, no repository call.
- Same trimmed query twice in a row does not search again.
- Search failure → `message = SEARCH_FAILED`, items unchanged.
- `status` is SUBSCRIBED for results whose feedUrl is in `observeLibrary()` (live: unsubscribing elsewhere flips it back).
- `subscribe`: status SUBSCRIBING while running → repository.subscribe(feedUrl, result.genre) → SUBSCRIBED + `message=SUBSCRIBED`;
  failure → back to NOT_SUBSCRIBED + `SUBSCRIBE_FAILED`. Double tap while SUBSCRIBING is ignored.
- `addByUrl`: trimmed; not starting with http(s):// → `INVALID_URL` without calling the repository; otherwise
  `repository.subscribe(url, null)` (no fallback genre);
  `isAddingByUrl` true while running, then SUBSCRIBED or SUBSCRIBE_FAILED.
- Screen: search field with leading search icon and trailing clear button (when non-empty); top-bar action
  "Add by URL" opens `AddByUrlDialog`; states: initial hint (no query), progress indicator while searching,
  "No podcasts found" when `hasSearched && items.isEmpty()`, results list (artwork 56dp, title, author · genreLabel,
  trailing: "Subscribe" FilledTonalButton / CircularProgressIndicator / check icon with "Subscribed" contentDescription).
- `message` shown once in a Snackbar, then `onMessageShown()`.
- All text EN + PT.

## Test plan

### Test files
- `app/src/test/java/com/punyplatipus/podouvir/ui/discover/DiscoverViewModelTest.kt` (11 cases, `unit`)
- `app/src/test/java/com/punyplatipus/podouvir/ui/discover/DiscoverScreenTest.kt` (4 cases, `ui`)

### Fixtures/fakes
- `FakePodcastRepository` and `MainDispatcherRule` from `testutil/` (as is, no edits to shared files).
  Drive `searchResult`, `subscribeResult`, `podcasts`, and assert on `searchCalls` / `subscribeCalls`.
  `searchResult` is a plain `Result`, so a failure is `Result.failure(IOException())`.
- Local helper in `DiscoverViewModelTest.kt`: `GatedPodcastRepository(delegate: FakePodcastRepository)` implementing
  `PodcastRepository` by delegation (`by delegate`), overriding `search` / `subscribe` to first `await()` an optional
  `CompletableDeferred<Unit>` gate, then call the delegate. Needed to observe SUBSCRIBING / isAddingByUrl / in-flight
  search cancellation. The Fake itself returns immediately, so it cannot show intermediate states. It must record
  started and completed calls separately, so cancellation is observable.
- Helper `result(feedUrl, title, genre)` building `PodcastSearchResult`, and `Podcast` builders for library entries.
- ViewModel collection: Turbine `uiState.test { }` or a `backgroundScope.launch { uiState.collect() }`, because of
  `WhileSubscribed(5_000)`. Time via `advanceTimeBy(399)` / `advanceTimeBy(1)` / `advanceUntilIdle()` on the
  `StandardTestDispatcher` scheduler.
- Screen tests use Robolectric (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`) with `createComposeRule()`
  and strings via `ApplicationProvider` / `stringResource` lookups. Hand-built `DiscoverUiState`.

### DiscoverViewModel (level `unit` unless noted)
| # | name | given | when | then |
|---|------|-------|------|------|
| 1 | `onQueryChange updates query immediately and searches only after 400 ms debounce with trimmed query` | collecting uiState, `searchResult` = 2 results | `onQueryChange("  cast  ")`; advance 399 ms; then advance 1 ms more | `query` is `"  cast  "` right away (raw); `searchCalls` empty at 399 ms; after 400 ms `searchCalls == ["cast"]`, `items` has 2 entries, `isSearching` false, `hasSearched` true |
| 2 | `rapid typing searches only the last query` | gated repository | `onQueryChange("a")`, advance 200, `onQueryChange("ab")`, advance 400 | `searchCalls == ["ab"]` only |
| 3 | `newer query cancels in-flight search` | gated search for "a" started (gate closed, `isSearching` true) | `onQueryChange("b")`, advance 400, open gates | results of "a" never reach `items`; final items are those of "b"; `isSearching` false at the end |
| 4 | `blank query clears items and hasSearched without calling the repository` | previous search done, items non-empty, `hasSearched` true | `onQueryChange("   ")`, advance 1000 | `items` empty, `hasSearched` false, `isSearching` false, `searchCalls` has no new entry; a pending debounced search for the earlier query is cancelled |
| 5 | `same trimmed query twice in a row does not search again` | search for "cast" completed | `onQueryChange("cast ")`, advance 1000 | `searchCalls.size == 1`; `query` is updated to `"cast "`; items unchanged |
| 6 | `search failure sets SEARCH_FAILED and keeps previous items` | first search succeeded (items present), `searchResult = failure(IOException)` | `onQueryChange("new")`, advance 400 | `message == SEARCH_FAILED`, `items` equal to previous, `isSearching` false. Boundary in the same case: an empty result list gives `items` empty with `hasSearched` true and no message |
| 7 | `item status reflects observeLibrary live` | results A and B; library contains a podcast with A's feedUrl | search completes, then `fake.unsubscribe(idOfA)`, then `fake.subscribe(B.feedUrl)` | initially A=SUBSCRIBED, B=NOT_SUBSCRIBED; after unsubscribing A flips to NOT_SUBSCRIBED (no new search call); after subscribing B flips to SUBSCRIBED |
| 8 | `subscribe goes SUBSCRIBING then SUBSCRIBED and emits message` | gated repository, result R with `genre="Technology"`, nothing subscribed | `subscribe(R)`, let it start; then release the gate | during: R is SUBSCRIBING; `subscribeCalls == [(R.feedUrl, "Technology")]`; after: R is SUBSCRIBED (via library) and `message == SUBSCRIBED` |
| 9 | `subscribe failure reverts to NOT_SUBSCRIBED with SUBSCRIBE_FAILED` | `subscribeResult = { failure(IOException()) }` | `subscribe(R)`, advance idle | R is NOT_SUBSCRIBED, `message == SUBSCRIBE_FAILED` |
| 10 | `second subscribe while SUBSCRIBING is ignored` | gated subscribe for R in flight | `subscribe(R)` again, then release gate | exactly one `subscribeCalls` entry; no duplicated message |
| 11 | `addByUrl validates, trims, tracks isAddingByUrl and reports outcome, and messageShown clears message` | gated repository | (a) `addByUrl("  ftp://x/feed ")` and `addByUrl("example.com/feed")` -> INVALID_URL, repository not called, `isAddingByUrl` stays false. (b) `addByUrl("  https://example.com/feed  ")` with the gate closed -> then open it. (c) failing `subscribeResult` with `http://bad/feed`. (d) `messageShown()` | (a) `message == INVALID_URL`, `subscribeCalls` empty; (b) `isAddingByUrl` true while running with `subscribeCalls == [("https://example.com/feed", null)]` (fallback genre null; adjust if implementation passes a different value), then false and `message == SUBSCRIBED`; (c) `message == SUBSCRIBE_FAILED`, `isAddingByUrl` false; (d) `message` becomes null |

### DiscoverScreen (level `ui`, Robolectric Compose; stateless screen, no Hilt)
Chosen as `ui` because acceptance criteria are rendering and interaction. No instrumented test is needed.
| # | name | given | when | then |
|---|------|-------|------|------|
| 12 | `shows initial hint, progress, or empty state according to state` | (a) default state; (b) `query="x", isSearching=true`; (c) `query="x", hasSearched=true, items=empty` | render each state | (a) hint text visible, no "No podcasts found"; (b) a progress indicator exists (`ProgressBarRangeInfo` node), no empty text; (c) "No podcasts found" visible. Strings read from resources |
| 13 | `result rows render title, author · genre and the trailing control for each status` | items: NOT_SUBSCRIBED, SUBSCRIBING, SUBSCRIBED (distinct titles; one with `UNKNOWN_GENRE`) | render; click "Subscribe" button | titles and "author · genreLabel" text shown (unknown genre shown localised); exactly one "Subscribe" button, one progress indicator in the SUBSCRIBING row, one node with contentDescription "Subscribed"; click calls `onSubscribe` with the matching `PodcastSearchResult` only for the NOT_SUBSCRIBED row |
| 14 | `search field shows search icon, forwards typing, and shows clear button only when non-empty` | (a) `query=""`; (b) `query="abc"` | type text into the field; click clear button in (b) | (a) no clear button (by contentDescription); typing invokes `onQueryChange` with the text; (b) clear button present, click invokes `onQueryChange("")` |
| 15 | `add-by-URL action opens dialog, submits the entered URL, and message is shown once in a snackbar` | state with `message = SUBSCRIBE_FAILED`, then `INVALID_URL` | click top-bar "Add by URL", type a URL in the dialog, confirm; separately render with the message set | the dialog appears; confirm calls `onAddByUrl` with the typed text and closes (cancel closes without a call); the snackbar shows the localised text for the message and `onMessageShown` is invoked once (not repeatedly across recomposition) |

### Acceptance criteria coverage
- Debounce / trimmed query / cancel / blank: 1, 2, 3, 4. Duplicate query: 5. Search failure: 6 (plus empty result boundary).
- Live SUBSCRIBED status: 7. Subscribe flow, failure, double tap: 8, 9, 10. addByUrl: 11.
- Screen states, field, add dialog, row layout, snackbar: 12, 13, 14, 15.
- EN + PT text: not asserted per locale (see out of scope); tests resolve strings by resource id, so a missing resource would fail compilation.

### Out of scope
- `DiscoverRoute` and Hilt wiring (`hiltViewModel`, `collectAsStateWithLifecycle`): thin glue, covered by the navigation shell task (011) and compile checks.
- Actual iTunes/network and Room behavior: already covered by M1 repository tests; the fake is used here.
- PT translations and the pixel size of artwork (56dp): translation content is not logic, and `PodcastArtwork` / `genreLabel` are tested in task 007. `@Preview` functions are not tested.
- Rotation / process death and the `WhileSubscribed(5_000)` timeout: framework behavior, not feature behavior.

## Implementation notes
Files: `ui/discover/DiscoverViewModel.kt`, `ui/discover/DiscoverScreen.kt`, `res/values{,-pt}/strings_discover.xml`.
- ViewModel: query `MutableStateFlow` -> `map(trim)` -> `debounce(400)` -> `distinctUntilChanged` -> `collectLatest` (launched in `init`, independent of uiState subscribers). Blank term clears results/hasSearched. After `repository.search` an `ensureActive()` prevents stale results if the repo swallows cancellation into a failed Result. Failure keeps items, sets SEARCH_FAILED.
- `uiState` = combine(query, internal state, library feedUrls); SUBSCRIBED comes from `observeLibrary()` (exact feedUrl match), SUBSCRIBING from an internal in-flight set (double tap ignored). Success of `subscribe`/`addByUrl` sets `message = SUBSCRIBED`.
- `addByUrl`: trimmed, scheme check is case-insensitive; calls `repository.subscribe(url, null)`; a second call while `isAddingByUrl` is ignored.
- Screen: "Add by URL" is an icon-only top-bar action (Link icon) with contentDescription "Add by URL" (find by contentDescription, not text). The dialog title also reads "Add by URL" (text); confirm button "Add", cancel "Cancel". Dialog closes on confirm/cancel. Artwork has null contentDescription in rows (decorative; title is adjacent). With items present and a search running, a LinearProgressIndicator shows above the list; with no items, a CircularProgressIndicator is centred.
- Snackbar: effect keyed on `message`; calls `onMessageShown()` after the snackbar finishes (so it fires once).
- Build: not run: no SDK.

## Test results
Not run: no JDK/SDK here (`java` not found); CI runs them. Reviewed by eye against the implementation.
Files: `app/src/test/java/com/punyplatipus/podouvir/ui/discover/DiscoverViewModelTest.kt`, `DiscoverScreenTest.kt`.

| # | case | result |
|---|------|--------|
| 1 | onQueryChange updates query immediately ... 400 ms debounce with trimmed query | NOT RUN (expected pass) |
| 2 | rapid typing searches only the last query | NOT RUN (expected pass) |
| 3 | newer query cancels in-flight search | NOT RUN (expected pass) |
| 4 | blank query clears items and hasSearched without calling the repository | NOT RUN (expected pass) |
| 4b | blank query cancels a pending debounced search (added by tester) | NOT RUN (expected pass) |
| 5 | same trimmed query twice in a row does not search again | NOT RUN (expected pass) |
| 6 | search failure sets SEARCH_FAILED and keeps previous items (+ empty boundary) | NOT RUN (expected pass) |
| 7 | item status reflects observeLibrary live | NOT RUN (expected pass) |
| 8 | subscribe goes SUBSCRIBING then SUBSCRIBED and emits message | NOT RUN (expected pass) |
| 9 | subscribe failure reverts to NOT_SUBSCRIBED with SUBSCRIBE_FAILED | NOT RUN (expected pass) |
| 10 | second subscribe while SUBSCRIBING is ignored | NOT RUN (expected pass) |
| 11 | addByUrl validates, trims, tracks isAddingByUrl ... | NOT RUN (expected pass) |
| 12 | shows initial hint, progress, or empty state | NOT RUN (expected pass) |
| 13 | result rows render title, author and genre and trailing control | NOT RUN (expected pass) |
| 14 | search field shows hint, forwards typing, clear button only when non-empty | NOT RUN (expected pass) |
| 15 | add-by-URL action opens dialog, submits URL, cancel closes without call | NOT RUN (expected pass) |
| 15b | message shown once in snackbar, onMessageShown once | NOT RUN (expected pass; manual-clock timing is the likeliest thing to need adjusting) |

Notes: search icon (decorative, null contentDescription) is not asserted; hint placeholder is asserted instead. Test 15 was split into dialog and snackbar tests.
