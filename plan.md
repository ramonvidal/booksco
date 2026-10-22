# Booksco — Android Podcast App: Plan

> Living document. Update the checkboxes and the "Decisions log" as work lands.

## 1. Vision

A simple, robust, modern podcast player for Android. Subscribe to podcasts, organise them by genre,
sort them the way you like, and listen with reliable background playback and variable speed.

**Principles**
- **Robust first**: playback must survive screen-off, app backgrounding, process death and audio-focus
  changes. Position is never lost.
- **Offline-first**: the local database is the source of truth; network refreshes it.
- **Modern UI**: Material 3, Jetpack Compose, dynamic color (Material You), dark theme, edge-to-edge.
- **Small and testable**: every feature has unit tests for its ViewModel and repository logic.

## 2. Scope

### MVP (v0.1)
| Feature | Details |
|---|---|
| Discover / add podcasts | Search via the iTunes Search API (free, no key). Add by RSS URL as fallback. |
| Library | Grid/list of subscribed podcasts. |
| Group by genre | Genre comes from the feed (`itunes:category`) or search result; user can override. Collapsible genre sections. |
| Sort | Title A–Z, Recently updated, Date added, Custom (drag to reorder). Persisted per user. |
| Podcast detail | Artwork, description, episode list (newest first), played/in-progress markers. |
| Player | Play, Pause, Stop, seek bar, skip back 15s / forward 30s, speed **1.0x / 1.25x / 1.5x / 2.0x**. |
| Background playback | Media3 `MediaSessionService`: notification controls, lock screen, Bluetooth/headset buttons, Android Auto-ready session. |
| Resume | Saves playback position per episode; resumes where you left off. |
| Mini-player | Persistent bar above bottom navigation; tap to expand to full player. |

### Suggested for v0.2+ (proposals — confirm or drop)
- [ ] **Up Next queue** (play next / add to queue, auto-advance).
- [ ] **Sleep timer** (15/30/45/60 min, end of episode).
- [ ] **Downloads** for offline listening (WorkManager + Media3 download cache), auto-delete played.
- [ ] **Background feed refresh** with new-episode notifications (WorkManager, daily).
- [ ] **OPML import/export** (move subscriptions from other apps).
- [ ] **Per-podcast default speed** and **silence trimming** / volume boost.
- [ ] **Chapters** (Podcasting 2.0 `podcast:chapters`) and transcripts.
- [ ] **Home-screen widget** and **Android Auto**.
- [ ] Custom speed slider (0.5x–3.0x) in addition to presets.

### Out of scope (for now)
Accounts/cloud sync, video podcasts, social features, monetisation.

## 3. Interface design

Material 3 with dynamic color (falls back to a branded purple/teal palette on Android < 12).
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
- **Networking**: OkHttp + Retrofit (kotlinx.serialization) for iTunes Search; RSS parsed with
  `XmlPullParser` (no heavy dependency).
- **Playback**: AndroidX Media3 (ExoPlayer, MediaSessionService, MediaController).
- **Background work**: WorkManager (feed refresh, downloads — v0.2).
- **Testing**: JUnit4, kotlinx-coroutines-test, Turbine, MockK; Room in-memory tests; Compose UI tests for key flows.
- **Quality**: Android Lint, ktlint (via Spotless), GitHub Actions CI on every PR.
- **Min SDK 26 / Target SDK 35.**

### Package layout (single `app` module to start; modularise when it grows)
```
com.punyplatipus.booksco
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
- [ ] First green CI run

### M1 — Data layer
- [ ] Room DB: entities, DAOs, migrations strategy (exported schemas)
- [ ] iTunes Search client + RSS parser (with fixture-based tests)
- [ ] `PodcastRepository`: subscribe, unsubscribe, refresh, observe library
- [ ] Pure functions: `groupByGenre`, `sortPodcasts` (fully unit-tested)

### M2 — Library & Discover UI
- [ ] Navigation shell: bottom bar + mini-player slot
- [ ] Discover: search + subscribe + add by URL
- [ ] Library: genre chips, grouped sections, sort menu, grid/list, drag-reorder (custom sort)
- [ ] Change genre dialog

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
- [ ] Accessibility pass (TalkBack labels, touch targets, font scaling)
- [ ] App icon, splash screen, R8 release build, signing config
- [ ] Internal testing release (Play Console) — needs developer account

## 6. Open questions (need owner input)
1. Final **app name** (repo is `booksco`) and **package/applicationId** (currently `com.punyplatipus.booksco`).
2. Which v0.2 suggestions to adopt, and in what order.
3. Brand colours / logo, or rely on dynamic color + placeholder palette?
4. Distribution: Google Play (needs developer account), F-Droid, or sideload only?
5. Languages: English only, or also Portuguese/Spanish from day one?

## 7. Decisions log
| Date | Decision | Why |
|---|---|---|
| 2026-10-22 | Compose + Material 3, single-activity | Modern standard, least code |
| 2026-10-22 | Media3 for playback | Official, handles session/notification/focus |
| 2026-10-22 | iTunes Search API for discovery | Free, no API key, returns genre |
| 2026-10-22 | Single `app` module initially | Keep setup simple; split into modules after M3 if needed |
