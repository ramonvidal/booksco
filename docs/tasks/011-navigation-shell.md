# 011 Navigation shell: bottom bar, routes, mini-player slot
Plan item: M2 — Navigation shell: bottom bar + mini-player slot
Status: done
Depends on: 008, 010. Read `docs/tasks/M2-conventions.md`.

## Goal
Single-activity navigation: Library / Discover / Queue tabs, a podcast detail route (placeholder until M4) and an
empty mini-player slot above the bottom bar (filled in M3).

## Files
- `app/src/main/java/com/punyplatipus/podouvir/ui/navigation/Destinations.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/ui/navigation/PodOuvirNavHost.kt` (new: `MainScaffold`, `BottomBar`, NavHost)
- `app/src/main/java/com/punyplatipus/podouvir/ui/queue/QueueScreen.kt` (new placeholder "Up Next — coming soon")
- `app/src/main/java/com/punyplatipus/podouvir/ui/podcast/PodcastDetailPlaceholder.kt` (new: top bar with back arrow, "Details coming soon")
- `app/src/main/java/com/punyplatipus/podouvir/ui/player/MiniPlayerSlot.kt` (new: `@Composable fun MiniPlayerSlot(modifier)` that renders nothing yet)
- `app/src/main/java/com/punyplatipus/podouvir/MainActivity.kt` (modify: `PodOuvirTheme { MainScaffold() }`)
- `res/values/strings_navigation.xml`, `res/values-pt/strings_navigation.xml` (new)
- Tests: `ui/navigation/BottomBarTest.kt` (Robolectric compose, stateless `BottomBar`), `ui/navigation/DestinationsTest.kt` (JVM).

## Public API
```kotlin
package com.punyplatipus.podouvir.ui.navigation

@Serializable data object LibraryDestination
@Serializable data object DiscoverDestination
@Serializable data object QueueDestination
@Serializable data class PodcastDestination(val podcastId: Long)

enum class TopLevelTab(val destination: Any, @StringRes val labelRes: Int, val icon: ImageVector) {
    LIBRARY(...Icons.Rounded.LibraryMusic), DISCOVER(...Icons.Rounded.Search), QUEUE(...Icons.AutoMirrored.Rounded.QueueMusic)
}

@Composable fun MainScaffold(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController())

/** Stateless; [selected] null = no tab highlighted (e.g. on the detail screen). */
@Composable fun BottomBar(selected: TopLevelTab?, onSelect: (TopLevelTab) -> Unit, modifier: Modifier = Modifier)
```

## Acceptance criteria
- Start destination: Library. Bottom bar = `Column { MiniPlayerSlot(); BottomBar(...) }` inside the Scaffold `bottomBar`.
- Selecting a tab navigates with `popUpTo(graph.findStartDestination().id) { saveState = true }`, `launchSingleTop = true`,
  `restoreState = true`; re-selecting the current tab does nothing harmful.
- Selected tab is derived from `currentBackStackEntry.destination.hasRoute(...)` (hierarchy); on PodcastDestination no tab
  is selected but the bottom bar stays visible.
- Library `onOpenPodcast(id)` → `PodcastDestination(id)`; Library `onDiscover` → Discover tab. Detail back arrow → `popBackStack()`.
- Screens receive the Scaffold's inner padding (no content under the bottom bar); edge-to-edge kept.
- Tab labels EN/PT: Library/Biblioteca, Discover/Descobrir, Queue/Fila.

## Test plan

### Test files
- `app/src/test/java/com/punyplatipus/podouvir/ui/navigation/DestinationsTest.kt` (JVM, `unit`)
- `app/src/test/java/com/punyplatipus/podouvir/ui/navigation/BottomBarTest.kt` (Robolectric + `createComposeRule()`,
  `@Config(sdk = [34])`, `ui`)

### Fixtures/fakes
None. Labels are resolved via `InstrumentationRegistry`/`composeRule.activity.getString(TopLevelTab.X.labelRes)`
(or `stringResource` equivalents); no Hilt, NavController or repository is needed because `BottomBar` is stateless.

### Cases: `TopLevelTab` / destinations (DestinationsTest, level `unit`)
| name | given | when | then |
|---|---|---|---|
| `tabs are ordered library discover queue` | `TopLevelTab.entries` | read order | `[LIBRARY, DISCOVER, QUEUE]` (bar order is stable) |
| `each tab maps to its destination object` | each tab | read `destination` | LIBRARY=`LibraryDestination`, DISCOVER=`DiscoverDestination`, QUEUE=`QueueDestination`; all distinct |
| `tabs have distinct non-zero label resources` | each tab | read `labelRes` | non-zero and pairwise distinct |
| `podcast destination is not a top-level tab` | `PodcastDestination(1)` | check against `TopLevelTab.entries.map { it.destination }` | not contained (so detail shows no selected tab) |
| `podcast destination keeps its id and has value equality` | `PodcastDestination(42)` and another with 42 | read `podcastId`, compare | id is 42; the two are equal; `PodcastDestination(7)` differs |

### Cases: `BottomBar` (BottomBarTest, level `ui`; Robolectric is justified because Compose needs a runtime and `BottomBar` has no JVM-only testable form)
| name | given | when | then |
|---|---|---|---|
| `shows one item per tab with its label` | `selected = LIBRARY` | render | three nodes with the Library, Discover and Queue labels exist (resource strings) |
| `marks only the selected tab as selected` | `selected = DISCOVER` | render | Discover node `assertIsSelected()`; Library and Queue `assertIsNotSelected()` |
| `highlights no tab when selected is null` | `selected = null` | render | all three nodes `assertIsNotSelected()`, all still displayed (detail screen case) |
| `clicking a tab invokes onSelect with that tab` | `selected = LIBRARY`, recording `onSelect` | click Queue label | recorded list is `[QUEUE]` |
| `clicking the already selected tab still reports it once` | `selected = LIBRARY`, recording `onSelect` | click Library label | recorded `[LIBRARY]`; the composable does not crash or filter (de-dup/no-op safety is the NavController's `launchSingleTop`) |

### Acceptance criteria coverage
- Tab labels EN/PT: labels are looked up via `labelRes`, so cases above verify the resources exist and are wired; the
  per-locale text (Library/Biblioteca, etc.) is not asserted in code, review the two `strings_navigation.xml` files.
- Selected-tab-from-hierarchy, start destination, popUpTo/saveState/launchSingleTop/restoreState, Library to Detail and
  Discover callbacks, back arrow, inner padding, `MiniPlayerSlot` placement: see out of scope.

### Out of scope
- `MainScaffold`/NavHost end-to-end tests (start destination, tab navigation flags, `hasRoute` selection, back stack
  restore, `onOpenPodcast`/`onDiscover` wiring, back arrow `popBackStack()`): the Library and Discover routes call
  `hiltViewModel()`, and the project has no Hilt test setup (`HiltAndroidRule`, test application) nor
  `navigation-testing` dependency, and no new dependencies are allowed. Verify manually / in a later milestone.
- `QueueScreen`, `PodcastDetailPlaceholder`, `MiniPlayerSlot`: static placeholders with no logic.
- Inset/edge-to-edge padding: visual, checked manually.
- Per-locale string text: resource files reviewed, not unit tested.

## Implementation notes
- Added ui/navigation/{Destinations,PodOuvirNavHost}.kt, ui/queue/QueueScreen.kt, ui/podcast/PodcastDetailPlaceholder.kt (extra `podcastId` param, unused for now), ui/player/MiniPlayerSlot.kt, strings_navigation.xml (EN/PT); MainActivity now renders `MainScaffold()`.
- NavHost gets `Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)`; inner Scaffolds untouched. Detail placeholder has its own TopAppBar; Queue uses safeDrawingPadding.
- `BottomBar` items: label Text only (icon contentDescription null) so label nodes are unique for tests.
- Not built: no SDK.

## Test results
Not run: no SDK/JDK (`java` not found). CI runs them. Reviewed by eye against the implementation; none expected to fail.

| case | result |
|---|---|
| DestinationsTest: tabs are ordered library discover queue | NOT RUN |
| DestinationsTest: each tab maps to its destination object | NOT RUN |
| DestinationsTest: tabs have distinct non-zero label resources | NOT RUN |
| DestinationsTest: podcast destination is not a top-level tab | NOT RUN |
| DestinationsTest: podcast destination keeps its id and has value equality | NOT RUN |
| BottomBarTest: shows one item per tab with its label | NOT RUN |
| BottomBarTest: marks only the selected tab as selected | NOT RUN |
| BottomBarTest: highlights no tab when selected is null | NOT RUN |
| BottomBarTest: clicking a tab invokes onSelect with that tab | NOT RUN |
| BottomBarTest: clicking the already selected tab still reports it once | NOT RUN |
