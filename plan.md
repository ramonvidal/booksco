# PodOuvir — Android Podcast App: Plan

> Living document. Update the checkboxes and the "Decisions log" as work lands.

## 1. Vision

A simple, robust, modern podcast player for Android. Subscribe to podcasts, organise them by genre,
sort them the way you like, and listen with reliable background playback and variable speed.

**Principles**
- **Robust first**: playback must survive screen-off, app backgrounding, process death and audio-focus
  changes. Position is never lost.
- **Offline-first**: the local database is the source of truth; network refreshes it.
- **Modern UI**: Material 3, Jetpack Compose, red/yellow brand palette, dark theme, edge-to-edge.
- **Free software**: shipped on F-Droid — no proprietary libraries, no tracking.
- **Bilingual**: English and Portuguese from day one.
- **Small and testable**: every feature has unit tests for its ViewModel and repository logic.

## 2. Scope

### MVP (v0.1)
| Feature | Details |
|---|---|
| Discover / add podcasts | Search fyyd.de (open, no key) + Podcast Index (open, free key, optional at build time). Add by RSS URL as fallback. |
| Library | Grid/list of subscribed podcasts. |
| Group by genre | Genre comes from the feed (`itunes:category`) or search result; user can override. Collapsible genre sections. |
| Sort | Title A–Z, Recently updated, Date added, Custom (drag to reorder). Persisted per user. |
| Podcast detail | Artwork, description, episode list (newest first), played/in-progress markers. |
| Player | Play, Pause, Stop, seek bar, skip back 15s / forward 30s, speed **1.0x / 1.25x / 1.5x / 2.0x**. |
| Background playback | Media3 `MediaSessionService`: notification controls, lock screen, Bluetooth/headset buttons, Android Auto-ready session. |
| Resume | Saves playback position per episode; resumes where you left off. |
| Mini-player | Persistent bar above bottom navigation; tap to expand to full player. |

### v0.2+ roadmap (order decided 2026-10-22: most-requested listening features first, then reach)
1. [ ] **Up Next queue** (play next / add to queue, auto-advance) — fills the Queue tab already in the MVP shell.
2. [ ] **Sleep timer** (15/30/45/60 min, end of episode) — small, high value for podcast listeners.
3. [ ] **Background feed refresh** with new-episode notifications (WorkManager, daily).
4. [ ] **Downloads** for offline listening (WorkManager + Media3 download cache), auto-delete played.
5. [ ] **OPML import/export** (move subscriptions from other apps — key for F-Droid users switching apps).
6. [ ] **Per-podcast default speed**, custom speed slider (0.5x–3.0x), **silence trimming** / volume boost.
7. [ ] **Chapters** (Podcasting 2.0 `podcast:chapters`) and transcripts.
8. [ ] **Home-screen widget** and **Android Auto**.

### Out of scope (for now)
Accounts/cloud sync, video podcasts, social features, monetisation.

## 3. Interface design

Material 3 with the PodOuvir brand palette (warm red + yellow); wallpaper-based dynamic color is an opt-in setting.
Logo: a yellow ear receiving sound waves on a red→orange gradient (adaptive + monochrome icon).
Bottom navigation with 3 destinations + a persistent mini-player.

```
┌──────────────────────────────┐   ┌──────────────────────────────┐
│ Library          [⇅ Sort] [⊞]│   │ ⌄            Now Playing   ⋮ │
│ [All][Tech][News][Comedy]... │   │                              │
│ ▾ Technology (4)             │   │      ┌──────────────┐        │
│  ┌───┐ ┌───┐ ┌───┐ ┌───┐     │   │      │   ARTWORK    │        │
│  │art│ │art│ │art│ │art│     │   │      │  (blurred bg)│        │
│  └───┘ └───┘ └───┘ └───┘     │   │      └──────────────┘        │
│ ▾ News (2)                   │   │  Episode title               │
│  ┌───┐ ┌───┐                 │   │  Podcast name                │
│  └───┘ └───┘                 │   │  ●━━━━━━━━━━○──────── 12:31  │
│                              │   │                              │
├──────────────────────────────┤   │   ⟲15    ■    ▶/❚❚    30⟳    │
│ [art] Episode title   ▶  ■   │   │                              │
├──────────────────────────────┤   │  [1x] [1.25x] [1.5x] [2x]    │
│  Library   Discover   Queue  │   │   Sleep ☾        Queue ≡     │
└──────────────────────────────┘   └──────────────────────────────┘
```

- **Library**: genre filter chips on top (single-select "All" + genres), collapsible genre sections,
  grid/list toggle, sort menu. Long-press a podcast → change genre / unsubscribe.
- **Discover**: search field, results list with "Subscribe" button, "Add by RSS URL" action.
- **Podcast detail**: large header with artwork and colour extracted from it (Palette), episode list.
- **Player**: full-screen sheet; speed as a segmented button row; Stop resets to start of episode
  and dismisses the notification.
- **Queue**: placeholder in MVP ("coming soon") — becomes Up Next in v0.2.

## 4. Architecture

```
UI (Compose screens) ──► ViewModel (StateFlow<UiState>) ──► Repository ──► Room DB (source of truth)
                                                              │
                                                              └─► Network (Ktor/OkHttp: iTunes API, RSS)
PlayerController (MediaController) ◄──► PlaybackService (Media3 MediaSessionService + ExoPlayer)
```

- **Language**: Kotlin 2.x, coroutines + Flow.
- **UI**: Jetpack Compose, Material 3, Navigation Compose (type-safe routes), Coil for images.
- **DI**: Hilt.
- **Persistence**: Room (podcasts, episodes, playback positions); DataStore for preferences (sort, view mode, speed).
- **Networking**: OkHttp + Retrofit (kotlinx.serialization) for fyyd.de / Podcast Index search; RSS parsed with
  `XmlPullParser` (no heavy dependency).
- **Playback**: AndroidX Media3 (ExoPlayer, MediaSessionService, MediaController).
- **Background work**: WorkManager (feed refresh, downloads — v0.2).
- **Testing**: JUnit4, kotlinx-coroutines-test, Turbine, MockK; Room in-memory tests; Compose UI tests for key flows.
- **Quality**: Android Lint, ktlint (via Spotless), GitHub Actions CI on every PR.
- **Min SDK 26 / Target SDK 35.**
- **Distribution**: F-Droid (store text in `fastlane/metadata/android/{en-US,pt-BR}`).

### Package layout (single `app` module to start; modularise when it grows)
```
com.punyplatipus.podouvir
├── data/        db/ (Room entities, DAOs), network/ (iTunes, RSS), repository/
├── domain/      model/ (Podcast, Episode, Genre, SortOrder), pure logic (grouping, sorting)
├── playback/    PlaybackService, PlayerController, position persistence
├── ui/          theme/, components/, library/, discover/, podcast/, player/, navigation/
└── di/          Hilt modules
```

### Data model (initial)
- `Podcast(id, feedUrl, title, author, artworkUrl, description, genre, genreOverride, addedAt, lastUpdated, customOrder)`
- `Episode(id, podcastId, guid, title, audioUrl, durationMs, publishedAt, description)`
- `PlaybackState(episodeId, positionMs, completed, updatedAt)`

## 5. Milestones

### M0 — Project setup ✅ (this commit)
- [x] Repo, plan.md, AGENTS.md / CLAUDE.md, Claude skills
- [x] Gradle (Kotlin DSL + version catalog), Compose + Hilt skeleton, theme
- [x] GitHub Actions CI (build, unit tests, lint)
- [ ] Generate Gradle wrapper (`gradle wrapper`) — needs a machine with Gradle/Android Studio
- [x] First green CI run

### M0.5 — Agent team ✅
- [x] Orchestrator workflow (`orchestrate` skill, `orchestrator` agent)
- [x] Subagents: `test-planner`, `coder` (cheaper model), `tester`
- [x] Rename to PodOuvir, brand palette + ear logo, EN/PT strings, F-Droid metadata

### M1 — Data layer ✅ (165 tests green in CI)
- [x] Room DB: entities, DAOs, migrations strategy (exported schemas) — #003; commit `app/schemas/.../1.json` after the first local build
- [x] iTunes Search client + RSS parser (with fixture-based tests) — #002, #004
- [x] `PodcastRepository`: subscribe, unsubscribe, refresh, observe library — #005
- [x] Pure functions: `groupByGenre`, `sortPodcasts` (fully unit-tested) — #001
- [ ] Follow-up: wrap subscribe's podcast + episode inserts in one Room transaction

### M2 — Library & Discover UI
- [ ] Navigation shell: bottom bar + mini-player slot (#011)
- [ ] Discover: search + subscribe + add by URL (#008)
- [ ] Search via fyyd.de + Podcast Index, iTunes removed (#012)
- [ ] Library: genre chips, grouped sections, sort menu, grid/list, custom reorder (#006, #009, #010)
- [ ] Change genre dialog (#007)

- [ ] Manual check on a device: type a new genre in the Library long-press → Change genre dialog (Robolectric hung on this path; the automated test uses the reset button instead)

### M3 — Playback
- [ ] `PlaybackService` (Media3) with notification + media session
- [ ] `PlayerController` exposing `StateFlow<PlayerUiState>`
- [ ] Play / Pause / Stop / seek / skip ±; speeds 1x, 1.25x, 1.5x, 2x (persisted)
- [ ] Position save every 10s + on pause/stop; resume
- [ ] Audio focus, becoming-noisy (headphones unplugged) handling
- [ ] Mini-player + full player screen

### M4 — Polish & release v0.1
- [ ] Podcast detail with Palette-tinted header, played markers
- [ ] Empty/error/loading states, pull-to-refresh
- [ ] Settings: theme (system/light/dark), dynamic color toggle, language picker
- [ ] Accessibility pass (TalkBack labels, touch targets, font scaling)
- [ ] App icon, splash screen, R8 release build, signing config
- [ ] F-Droid: tag `v0.1.0`, open a merge request to `fdroiddata` with the build recipe
- [ ] Screenshots (EN + PT) in fastlane metadata

## 6. Open questions (need owner input)
Answered 2026-10-22: search **fyyd.de + Podcast Index** (no iTunes); Portuguese **pt-BR only**; v0.2 order decided by the agent team (§2); licence **GPL-3.0-or-later**; name **PodOuvir**, package `com.punyplatipus.podouvir`; ear logo in red/yellow;
F-Droid; English + Portuguese.

Still open:
1. **Podcast Index key** — register (free) at https://api.podcastindex.org and put `podcastIndex.key` /
   `podcastIndex.secret` in `local.properties` (never committed). Without it the app searches fyyd only.
   For F-Droid builds: commit the key in the build recipe (common practice, e.g. AntennaPod) or ship fyyd-only?
2. **GitHub repo name** — owner will rename `ramonvidal/booksco` → `podouvir` on GitHub (Settings → Repository name);
   then run `git remote set-url origin git@github.com:ramonvidal/podouvir.git` (old URL keeps redirecting).

## 7. Decisions log
| Date | Decision | Why |
|---|---|---|
| 2026-10-22 | Compose + Material 3, single-activity | Modern standard, least code |
| 2026-10-22 | Media3 for playback | Official, handles session/notification/focus |
| 2026-10-22 | ~~iTunes Search API~~ → fyyd.de + Podcast Index | Owner: open directories only (avoids F-Droid NonFreeNet) |
| 2026-10-22 | Single `app` module initially | Keep setup simple; split into modules after M3 if needed |
| 2026-10-22 | Renamed to PodOuvir, package `com.punyplatipus.podouvir` | Owner's choice |
| 2026-10-22 | Brand red/yellow palette by default; dynamic color opt-in | Owner wants a recognisable red/yellow identity |
| 2026-10-22 | F-Droid distribution; fastlane metadata in repo | Owner's choice; F-Droid reads this layout |
| 2026-10-22 | Portuguese = Brazilian only; resources in `values-pt` (pt-PT users also get pt-BR text), store text `pt-BR` | Owner: Brazil only |
| 2026-10-22 | Orchestrator + test-planner / coder / tester subagents | Cheaper model writes code; tests planned independently of the implementation |
| 2026-10-22 | Licence GPL-3.0-or-later | Owner's choice; required by F-Droid, keeps forks free |
| 2026-10-22 | Robolectric for RSS (XmlPullParser) and Room tests | Both need Android runtime classes; keeps tests on the JVM in CI |
| 2026-10-22 | Feed URL identity = trimmed string | Simple and predictable; revisit if duplicates appear |
| 2026-10-22 | Library reorder via up/down buttons in a reorder mode (not drag) | No extra dependency, accessible with TalkBack, simpler for the coder model |
| 2026-10-22 | Per-feature string files (`strings_<feature>.xml`) | Parallel agents never edit the same resource file |
| 2026-10-22 | Podcast Index credentials from local.properties/env into BuildConfig | Keys never committed; fyyd works without a key |
