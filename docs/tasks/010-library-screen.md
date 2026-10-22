# 010 Library screen
Plan item: M2 — Library: genre chips, grouped sections, sort menu, grid/list, custom reorder — UI part; Change genre dialog wiring
Status: done
Depends on: 007, 009. Read `docs/tasks/M2-conventions.md` and `.claude/skills/compose-ui/SKILL.md`.

## Goal
Replace the placeholder `ui/library/LibraryScreen.kt` with the real Library UI (see plan.md §3 wireframe).

## Files
- `app/src/main/java/com/punyplatipus/podouvir/ui/library/LibraryScreen.kt` (rewrite: `LibraryRoute` + `LibraryScreen`)
- `app/src/main/java/com/punyplatipus/podouvir/ui/library/LibraryComponents.kt` (new: header, grid row, list row, menus)
- `res/values/strings.xml` + `res/values-pt/strings.xml`: MOVE `library_title`/`library_empty` out into
  `strings_library.xml` (EN + PT); keep only `app_name` in strings.xml. (Only this task touches strings.xml.)
- `MainActivity.kt`: update the call if the placeholder's signature changes (task 011 rewrites MainActivity later).
- Tests: `ui/library/LibraryScreenTest.kt` (Robolectric compose, stateless screen with hand-built state).

## Public API
```kotlin
@Composable fun LibraryRoute(
    onOpenPodcast: (Long) -> Unit,
    onDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
)
@Composable fun LibraryScreen(
    state: LibraryUiState,
    onOpenPodcast: (Long) -> Unit,
    onDiscover: () -> Unit,
    onSelectGenre: (String?) -> Unit,
    onSortOrder: (SortOrder) -> Unit,
    onToggleViewMode: () -> Unit,
    onStartReorder: () -> Unit,
    onFinishReorder: () -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onChangeGenre: (podcastId: Long, genre: String?) -> Unit,
    onUnsubscribe: (Long) -> Unit,
    modifier: Modifier = Modifier,
)
```

## Acceptance criteria
- **Top bar**: title "Library"/"Biblioteca"; actions: view toggle (icon GridView/ViewList, contentDescription
  describes the action), sort menu (icon Sort) with 4 radio-style items Title / Recently updated / Date added / Custom
  plus a "Reorder" item. In reorder mode the actions are replaced by a "Done" button (`onFinishReorder`).
- **Genre chips**: horizontal row of FilterChips: "All" + `state.genres` (via `genreLabel`); selected chip reflects
  `selectedGenre`; tapping "All" → `onSelectGenre(null)`. Hidden when the library is empty or reordering.
- **Groups**: LazyColumn with a `stickyHeader` per group showing "<genre label> (<count>)" and a chevron; tapping the
  header collapses/expands that group (local `rememberSaveable` set of collapsed genres).
- **Grid mode**: rows of artwork tiles (title below, max 2 lines), column count = max(2, availableWidth / 128.dp).
  **List mode**: ListItem with 56dp artwork, title, author. Reorder mode always uses list rows with up/down IconButtons
  (contentDescriptions "Move up"/"Move down"; disabled at group edges).
- Tap a podcast → `onOpenPodcast(id)`. Long-press → DropdownMenu with "Change genre" (opens `ChangeGenreDialog`
  with suggestions = `state.genres`, feedGenre = podcast.genre, currentOverride = podcast.genreOverride) and
  "Unsubscribe" (opens a confirm AlertDialog naming the podcast).
- **Empty state** (`totalCount == 0`, not loading): icon, `library_empty` text and a "Discover podcasts" button → `onDiscover`.
  **Loading**: centred CircularProgressIndicator.
- Items keyed by podcast id; headers keyed by genre. All text EN + PT. Previews: empty, grid, list, reorder (light+dark).

## Test plan

**Test file**: `app/src/test/java/com/punyplatipus/podouvir/ui/library/LibraryScreenTest.kt`
(`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`, `createComposeRule()`).
**Level**: all cases are `ui` (Robolectric Compose, JVM; no emulator). Justified because the screen is stateless and the criteria are interaction/visibility behaviour.

**Fixtures/helpers** (private in the test file; no resource files):
- `podcast(id, title, author, genre, genreOverride)` builder and `state(...)` builder producing `LibraryUiState` with `GenreGroup`s (e.g. Technology: A, B, C; News: D, E).
- `render(state, callbacks...)` helper that sets content with no-op defaults and records callback invocations in local vars/lists.
- Strings looked up via `ctx.getString(R.string.library_*)`; PodcastArtwork is expected to render a placeholder offline (no network/Coil fake needed).

| # | name | given | when | then |
|---|------|-------|------|------|
| 1 | `top bar shows title and grid-mode actions` | state with 2 groups, viewMode GRID | render | "Library" title shown; view-toggle, sort-menu buttons exist; no "Done" button |
| 2 | `view toggle invokes onToggleViewMode and its description reflects the target mode` | viewMode GRID, then LIST | click toggle; compare contentDescription between the two states | callback called once; descriptions differ (grid -> "switch to list", list -> "switch to grid") |
| 3 | `sort menu lists four options plus reorder and selecting one calls onSortOrder` | sortOrder = TITLE | open sort menu; click "Recently updated"; reopen, click "Custom" | Title/Recently updated/Date added/Custom/Reorder all displayed; Title radio selected; callbacks receive `UPDATED`-equivalent then `CUSTOM` SortOrder values |
| 4 | `reorder menu item calls onStartReorder` | not reordering | open sort menu, click "Reorder" | `onStartReorder` called once |
| 5 | `reorder mode replaces actions with Done and hides chips` | isReordering = true, genres non-empty | render; click "Done" | toggle and sort buttons absent; genre chips ("All") absent; `onFinishReorder` called once |
| 6 | `genre chips show All plus genres, reflect selection and report taps` | genres = [Technology, News], selectedGenre = News | render; click "All"; click "Technology" | "All" not selected, "News" selected; `onSelectGenre(null)` then `onSelectGenre("Technology")` |
| 7 | `genre chips hidden when library is empty` | totalCount = 0, isLoading = false | render | no "All" chip |
| 8 | `group headers show label and count and tapping collapses and expands the group` | groups Technology(3), News(2); list mode | assert headers "Technology (3)"/"News (2)"; click Technology header; click again | podcasts A-C disappear then reappear; News podcasts remain visible throughout |
| 9 | `unknown genre header uses localised label` | group with `UNKNOWN_GENRE` | render | header text equals `genreLabel(UNKNOWN_GENRE)` string plus count |
| 10 | `grid mode shows titles and tapping a tile opens the podcast` | viewMode GRID, podcast id 7 "Alpha" | click "Alpha" | title shown; `onOpenPodcast(7)` called |
| 11 | `list mode shows title and author and tapping a row opens the podcast` | viewMode LIST, podcast id 7 with author | assert title+author; click row | both texts shown; `onOpenPodcast(7)` called |
| 12 | `reorder mode move buttons call callbacks and are disabled at group edges` | isReordering, group of A,B,C | click "Move down" on A, "Move up" on C; inspect A's "Move up" and C's "Move down" | `onMoveDown(A.id)`, `onMoveUp(C.id)` called; edge buttons are disabled (assertIsNotEnabled); tapping a row does not matter (not asserted) |
| 13 | `long press opens menu and Change genre shows dialog and confirms with override` | list mode, podcast with genre "Technology", genreOverride null | long-press row; click "Change genre"; pick/confirm a genre in dialog | menu shows "Change genre" and "Unsubscribe"; dialog displayed with suggestions from `state.genres`; `onChangeGenre(id, <chosen>)` called (null for reset-to-feed if the dialog exposes it) |
| 14 | `unsubscribe asks for confirmation naming the podcast before calling onUnsubscribe` | podcast "Alpha" | long-press; click "Unsubscribe"; assert dialog; first dismiss (cancel), then repeat and confirm | dialog text contains "Alpha"; cancel -> `onUnsubscribe` not called; confirm -> `onUnsubscribe(id)` called once |
| 15 | `empty and loading states` | (a) totalCount 0, isLoading false; (b) isLoading true | render each | (a) `library_empty` text and "Discover podcasts" shown, click calls `onDiscover`, no grid/list content; (b) progress indicator present (`ProgressBarRangeInfo.Indeterminate`), empty text absent |

Not-yet-covered acceptance details are intentionally merged: PT strings (case 1-15 use resource lookups, so locale correctness is checked by the resource lint/build, not by a locale-switched test).

**Out of scope**
- Grid column count (`max(2, width/128dp)`) and pixel layout, artwork loading, 2-line truncation: layout detail, brittle in Robolectric.
- Real drag/ordering logic, grouping, sorting, persistence: covered by `LibraryViewModelTest` (task 009).
- `LibraryRoute` (Hilt/`collectAsStateWithLifecycle` wiring) and navigation: trivial glue, covered by task 011 / manual run.
- `ChangeGenreDialog` internals: tested in task 007; here only its wiring (case 13).
- Previews, dark theme, PT translation text correctness, sticky-header pinning behaviour.
- Saved-state restoration of collapsed set (`rememberSaveable`); would need `StateRestorationTester`, low value.

**Total: 15 cases (15 `ui`, 0 `unit`, 0 `room`).**

## Implementation notes
Status: done
- Files: `ui/library/LibraryScreen.kt` (rewritten: `LibraryRoute`, `LibraryScreen`, previews), `ui/library/LibraryComponents.kt` (new),
  `res/values{,-pt}/strings_library.xml` (new), `res/values{,-pt}/strings.xml` (library strings removed; PT file now empty `<resources>`),
  `MainActivity.kt` (`LibraryRoute(onOpenPodcast = {}, onDiscover = {})`).
- Test hooks: view toggle contentDescription = `library_view_switch_to_list` (GRID) / `library_view_switch_to_grid` (LIST); sort button = `library_sort`;
  sort items = `library_sort_*` (semantics role RadioButton + selected) and `library_reorder`; "Done" = `library_done` text button; chip "All" = `library_all`.
  Group header is one clickable node with text "<label> (<count>)" (`library_group_header`); its chevron has contentDescription `library_group_expand/collapse`.
  Reorder buttons use `library_move_up`/`library_move_down` (same description on every row; disabled at group edges).
  Long-press menu: `library_change_genre`, `library_unsubscribe`; confirm dialog title `library_unsubscribe_title` ("Unsubscribe?"),
  message `library_unsubscribe_message` contains the title, confirm button text `library_unsubscribe`, cancel `library_cancel`.
  Note "Change genre" text also appears as the ChangeGenreDialog title (task 007) once the dialog is open.
- Grid tile artwork has null contentDescription (title shown as text). Genre chips are hidden while loading, when empty, and when reordering.
- Collapsed set is an `ArrayList<String>` in `rememberSaveable` (Bundle-safe).

## Test results
Written: `app/src/test/java/com/punyplatipus/podouvir/ui/library/LibraryScreenTest.kt` (Robolectric, `@Config(sdk=[34], qualifiers="w411dp-h1600dp")` so lazy lists compose all rows).
No JDK on this machine (`java: command not found`): nothing executed; CI runs them. Reviewed by eye against the implementation: no spec violation found.

| # | case | result |
|---|------|--------|
| 1 | top bar shows title and grid-mode actions | NOT RUN |
| 2 | view toggle invokes onToggleViewMode and its description reflects the target mode | NOT RUN |
| 3 | sort menu lists four options plus reorder and selecting one calls onSortOrder | NOT RUN |
| 4 | reorder menu item calls onStartReorder | NOT RUN |
| 5 | reorder mode replaces actions with Done and hides chips | NOT RUN |
| 6 | genre chips show All plus genres, reflect selection and report taps | NOT RUN |
| 7 | genre chips hidden when library is empty | NOT RUN |
| 8 | group headers show label and count and tapping collapses and expands the group | NOT RUN |
| 9 | unknown genre header uses localised label | NOT RUN |
| 10 | grid mode shows titles and tapping a tile opens the podcast | NOT RUN |
| 11 | list mode shows title and author and tapping a row opens the podcast | NOT RUN |
| 12 | reorder mode move buttons call callbacks and are disabled at group edges | NOT RUN |
| 13 | long press opens menu and Change genre shows dialog and confirms with override | NOT RUN |
| 14 | unsubscribe asks for confirmation naming the podcast before calling onUnsubscribe | NOT RUN |
| 15a | empty state shows message and Discover button | NOT RUN |
| 15b | loading state shows progress indicator and no empty text | NOT RUN |

Case 15 was split into two tests. Case 13 types "Comedy" into the dialog field and taps Save (rather than picking a chip) to avoid ambiguity with the identical genre chips behind the dialog.
