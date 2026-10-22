# AGENTS.md

Guidance for AI coding agents (Claude Code, Codex, Copilot, etc.) working in this repo.
Read `plan.md` first: it holds the scope, milestones and open questions.

## Project
PodOuvir — an Android podcast player (F-Droid, English + Portuguese). Kotlin, Jetpack Compose (Material 3), Hilt, Room, DataStore,
Retrofit/OkHttp, AndroidX Media3. Single `app` module, package `com.punyplatipus.podouvir`.

## Commands
```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests (JVM)
./gradlew lintDebug              # Android Lint
./gradlew connectedDebugAndroidTest  # instrumented tests (needs emulator/device)
```
If `./gradlew` is missing, run `gradle wrapper --gradle-version 8.11.1` once (or open in Android Studio).
Before saying a change is done: build + unit tests must pass. If you cannot run them (no JDK/SDK),
say so explicitly.

## Architecture rules
- Layers: `ui` → `ViewModel` → `repository` → (`db` | `network`). UI never touches DAOs or network.
- Room is the source of truth; network results are written to Room, UI observes Flows.
- ViewModels expose a single `StateFlow<XxxUiState>` and accept events as functions. No `LiveData`.
- Pure logic (grouping, sorting, RSS parsing) lives in `domain`/`data` as plain functions and is unit-tested.
- Playback is only controlled through `PlayerController` (wraps Media3 `MediaController`). Never
  create an `ExoPlayer` outside `PlaybackService`.
- Inject dispatchers; never hardcode `Dispatchers.IO` in classes you want to test.

## Product constraints
- **F-Droid**: only free-software dependencies. No Google Play Services, Firebase, Crashlytics, analytics or ads.
- **Languages**: every user-visible string exists in `res/values/strings.xml` (EN) and
  `res/values-pt/strings.xml` (PT). Store text lives in `fastlane/metadata/android/{en-US,pt-BR}`.
- **Brand**: use theme colors (red/yellow palette in `ui/theme/Color.kt`), never raw hex in UI code.

## Code style
- Kotlin official style; 4-space indent; max line 120.
- Composables: `PascalCase`, first optional param is `modifier: Modifier = Modifier`.
- Screen = stateful `XxxRoute(viewModel)` + stateless `XxxScreen(state, onEvent…)` with `@Preview`s.
- Strings in `res/values/strings.xml`; no hardcoded user-visible text.
- Dependencies only via `gradle/libs.versions.toml`.

## Testing
- Every ViewModel and repository gets unit tests (JUnit4, coroutines-test, Turbine, MockK).
- RSS/API parsing tests use fixtures in `app/src/test/resources/`.
- Name tests `fun \`does X when Y\`()`.

## Workflow
- Small PRs, one milestone item each; tick the checkbox in `plan.md` in the same PR.
- Conventional commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`).
- Record notable decisions in the "Decisions log" in `plan.md`.

## Agent team (in `.claude/agents/`)
The `orchestrate` skill (or `claude --agent orchestrator`) drives the work: it splits a `plan.md` item into
tasks in `docs/tasks/`, then for each task runs `test-planner` → `coder` → `tester`, loops on failures,
reviews and commits. If you are one of those subagents, stay inside your role as described in your agent file.

## Skills (in `.claude/skills/`)
- `orchestrate` — the multi-agent workflow above.
- `android-build-verify` — how to build, test and lint; what to do without an SDK.
- `compose-ui` — design system, screen structure, previews, accessibility.
- `media3-playback` — rules for the playback service, speeds, positions, focus.
- `feature-slice` — step-by-step recipe for adding a feature end-to-end.
