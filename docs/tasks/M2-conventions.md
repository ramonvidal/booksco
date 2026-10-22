# M2 shared conventions (read before any M2 task)

- **Strings**: each feature has its OWN resource files so parallel tasks never edit the same file:
  `res/values/strings_<feature>.xml` and `res/values-pt/strings_<feature>.xml` (Portuguese, pt-BR wording).
  Prefix string names with the feature: `discover_search_hint`, `library_sort_title`, ...
  Do not edit `res/values/strings.xml` except where a task says so.
- **Screen structure** (see `.claude/skills/compose-ui/SKILL.md`): `XxxRoute(viewModel = hiltViewModel(), ...)` collects
  `uiState` with `collectAsStateWithLifecycle()`; stateless `XxxScreen(state, callbacks..., modifier)` with `@Preview`s.
- **ViewModels**: `@HiltViewModel`, one `val uiState: StateFlow<XxxUiState>` built with
  `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`, event functions, no Android `Context`.
  One-off failures are exposed as a nullable `message`/`error` field in the state plus a `messageShown()` event.
- **Images**: use `ui/components/PodcastArtwork` (task 007) — never call `AsyncImage` directly elsewhere.
- **Genre label**: show genres via `ui/components/genreLabel(genre)` (task 007), which localises `UNKNOWN_GENRE`.
- **Tests**:
  - ViewModels: JVM unit tests with a fake `PodcastRepository` / fake `LibraryPreferences`, `StandardTestDispatcher`
    set as Main via `Dispatchers.setMain` in a shared `MainDispatcherRule` (`app/src/test/.../testutil/MainDispatcherRule.kt`, already provided), Turbine.
  - Composables: Robolectric + `createComposeRule()` (`@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`),
    test the stateless `XxxScreen` with hand-built state; find nodes by text/contentDescription from string resources.
  - Fakes shared across tasks live in `app/src/test/.../testutil/` (FakePodcastRepository is provided).
- No new dependencies. Available: Compose Material3 (BOM 2024.12.01 → material3 1.3.x), material-icons-extended,
  Navigation Compose 2.8 (type-safe `@Serializable` routes), hilt-navigation-compose, Coil 3, DataStore Preferences.
