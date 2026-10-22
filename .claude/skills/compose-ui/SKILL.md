---
name: compose-ui
description: Conventions for building Booksco screens and components in Jetpack Compose with Material 3. Use when creating or changing any UI, theme, navigation or component.
---

# Compose UI conventions

## Structure
- `ui/<feature>/XxxRoute.kt`: `@Composable fun XxxRoute(viewModel: XxxViewModel = hiltViewModel(), …)` collects
  `uiState` with `collectAsStateWithLifecycle()` and passes it down.
- `ui/<feature>/XxxScreen.kt`: stateless `XxxScreen(state, onEvent…, modifier)`. Add `@Preview` for
  loading, empty, content and error states, light and dark (`uiMode = UI_MODE_NIGHT_YES`).
- Shared pieces go in `ui/components/` (e.g. `PodcastArtwork`, `GenreChip`, `SpeedSelector`, `MiniPlayer`).

## Design system
- Only use `MaterialTheme.colorScheme`, `.typography`, `.shapes` — no hardcoded colors or sp sizes.
- Dynamic color on Android 12+, fallback palette in `ui/theme/Color.kt`.
- Edge-to-edge: `enableEdgeToEdge()` in the activity; screens handle insets via `Scaffold` padding.
- Spacing scale: 4, 8, 12, 16, 24, 32 dp.
- Artwork: `AsyncImage` (Coil) with rounded `MaterialTheme.shapes.medium`, placeholder, `contentDescription` = podcast title.
- Player speeds use `SingleChoiceSegmentedButtonRow` with 1x / 1.25x / 1.5x / 2x.
- Library genre filter uses `FilterChip`s in a `LazyRow`; grouped sections use `stickyHeader`.

## Accessibility
- Every icon-only button has a `contentDescription` from `strings.xml`.
- Min touch target 48dp. Test at 200% font scale.
- Player controls get `semantics { stateDescription = … }` for play/pause state.

## Performance
- Pass stable/immutable models to composables (`@Immutable` data classes or `kotlinx.collections.immutable`).
- Give `LazyColumn`/`LazyVerticalGrid` items a `key`.
