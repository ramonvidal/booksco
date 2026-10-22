# 001 Domain models, sorting and genre grouping
Plan item: M1 — Pure functions: `groupByGenre`, `sortPodcasts` (fully unit-tested)
Status: done
Depends on: none

## Goal
Complete the domain layer that every other M1 task builds on: models for podcasts, episodes, playback
state and search results, plus sorting and genre grouping that honour the user's genre override.
Pure Kotlin — no Android imports.

## Files
- `app/src/main/java/com/punyplatipus/podouvir/domain/model/Podcast.kt` (modify: extend model, keep `SortOrder`, `sortPodcasts`, `groupByGenre`)
- `app/src/main/java/com/punyplatipus/podouvir/domain/model/Episode.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/domain/model/PlaybackState.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/domain/model/PodcastSearchResult.kt` (new)
- Existing test `app/src/test/java/com/punyplatipus/podouvir/domain/PodcastSortingTest.kt` must be updated by the tester (not the coder) to the new constructor.

## Public API
```kotlin
package com.punyplatipus.podouvir.domain.model

/** Shown when a podcast has no genre; the UI maps it to a localised label. */
const val UNKNOWN_GENRE = "Other"

data class Podcast(
    val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val artworkUrl: String? = null,
    val genre: String = "",
    val genreOverride: String? = null,
    val addedAt: Long = 0,
    val lastUpdated: Long = 0,
    val customOrder: Int = 0,
) {
    /** genreOverride (trimmed, if not blank), else genre (trimmed, if not blank), else UNKNOWN_GENRE. */
    val effectiveGenre: String
}

data class Episode(
    val id: Long = 0,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val description: String = "",
    val audioUrl: String,
    val durationMs: Long? = null,
    val publishedAt: Long? = null,
)

data class PlaybackState(
    val episodeId: Long,
    val positionMs: Long,
    val completed: Boolean,
    val updatedAt: Long,
)

data class PodcastSearchResult(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val genre: String,
)

enum class SortOrder { TITLE, RECENTLY_UPDATED, DATE_ADDED, CUSTOM }

fun List<Podcast>.sortPodcasts(order: SortOrder): List<Podcast>

fun List<Podcast>.groupByGenre(order: SortOrder): Map<String, List<Podcast>>
```

## Acceptance criteria
- `effectiveGenre` follows the rule in its KDoc (blank/whitespace override is ignored).
- `sortPodcasts`:
  - TITLE: case-insensitive A→Z.
  - RECENTLY_UPDATED: `lastUpdated` descending. DATE_ADDED: `addedAt` descending. CUSTOM: `customOrder` ascending.
  - Ties in any order are broken by `id` ascending (deterministic output).
  - Returns a new list; input unchanged. Empty list → empty list.
- `groupByGenre`:
  - Groups by `effectiveGenre`. Genres differing only by case are ONE group, named with the spelling of
    the first podcast (in sorted order) that has it.
  - Map iteration order: genre names case-insensitive A→Z, except `UNKNOWN_GENRE`, which is always last.
  - A genre equal to `UNKNOWN_GENRE` ignoring case (e.g. "other", "OTHER") joins the `UNKNOWN_GENRE` group,
    whose key is always exactly `UNKNOWN_GENRE` ("Other").
  - Within each group, podcasts keep the `order` sort.
  - Empty list → empty map.
- No Android imports in `domain/`.

## Test plan

### Test files
- `app/src/test/java/com/punyplatipus/podouvir/domain/PodcastSortingTest.kt` (existing; update to the new constructor, covers `sortPodcasts`)
- `app/src/test/java/com/punyplatipus/podouvir/domain/PodcastGenreTest.kt` (new; `effectiveGenre` and `groupByGenre`)
- `app/src/test/java/com/punyplatipus/podouvir/domain/model/DomainModelsTest.kt` (new; defaults and equality of the plain data classes)

### Fixtures/fakes
No resource files or fakes. Use a private helper in each test file, for example
`podcast(id, title = "t", genre = "", override = null, addedAt = 0, lastUpdated = 0, customOrder = 0)`,
which fills `feedUrl = "https://example.com/$id"`. All cases are pure JVM. Nothing needs mocking or coroutines.

### `Podcast.effectiveGenre` (`PodcastGenreTest`)
| name | given | when | then | level |
|---|---|---|---|---|
| `effectiveGenre uses override when set` | genre "News", override "Tech" | read | "Tech" | unit |
| `effectiveGenre falls back to genre when override is null` | genre "News", override null | read | "News" | unit |
| `effectiveGenre ignores blank override` | genre "News", override "   " (and "") | read | "News" | unit |
| `effectiveGenre trims override and genre` | override "  Tech ", or genre " News " with null override | read | "Tech" / "News" | unit |
| `effectiveGenre returns UNKNOWN_GENRE when both are empty or blank` | genre "" or "  ", override null or blank | read | `UNKNOWN_GENRE` ("Other") | unit |
| `effectiveGenre uses genre when override is blank and genre is set` | override " ", genre "Comedy" | read | "Comedy" | unit |

### `List<Podcast>.sortPodcasts(order)` (`PodcastSortingTest`)
| name | given | when | then | level |
|---|---|---|---|---|
| `sort by TITLE is case-insensitive A to Z` | titles "banana", "Apple", "cherry" | sortPodcasts(TITLE) | Apple, banana, cherry | unit |
| `sort by RECENTLY_UPDATED puts newest lastUpdated first` | lastUpdated 10, 30, 20 | sortPodcasts(RECENTLY_UPDATED) | 30, 20, 10 | unit |
| `sort by DATE_ADDED puts newest addedAt first` | addedAt 5, 50, 25 | sortPodcasts(DATE_ADDED) | 50, 25, 5 | unit |
| `sort by CUSTOM orders by customOrder ascending` | customOrder 2, 0, 1 | sortPodcasts(CUSTOM) | 0, 1, 2 | unit |
| `ties are broken by id ascending for every order` | equal keys, ids 3, 1, 2, per order (TITLE uses "a", "A", "a") | sortPodcasts(order) | ids 1, 2, 3 | unit |
| `sort returns a new list and leaves input unchanged` | a mutable or read-only list in non-sorted order | sortPodcasts(any) | input order unchanged; result is not the same instance | unit |
| `sort of empty list returns empty list` | emptyList | sortPodcasts for each of the 4 orders | empty | unit |
| `sort of single element returns that element` | one podcast | sortPodcasts(any) | list of one | unit |
| `sort TITLE handles non-ASCII and leading digits deterministically` | "Éclair", "eclair", "10 Things", "Zed" | sortPodcasts(TITLE) | stable, repeatable order (assert only that two calls give equal results and "10 Things" precedes "Zed"; do not pin accent handling) | unit |

### `List<Podcast>.groupByGenre(order)` (`PodcastGenreTest`)
| name | given | when | then | level |
|---|---|---|---|---|
| `groupByGenre groups by effectiveGenre` | genres News, Tech, News | groupByGenre(TITLE) | two keys, News holds 2 podcasts | unit |
| `groupByGenre honours genreOverride over genre` | genre "News" with override "Tech" | groupByGenre | podcast sits under "Tech", no "News" key | unit |
| `groupByGenre merges genres differing only by case` | "Tech", "tech", "TECH" | groupByGenre | one group | unit |
| `merged group is named after the first podcast in sorted order` | "tech" (title "A"), "Tech" (title "B"), order TITLE; and again with order DATE_ADDED where "Tech" is newer | groupByGenre | key spelling follows whichever podcast comes first under that `order` | unit |
| `groupByGenre orders keys case-insensitively A to Z` | genres "zen", "Alpha", "beta" | groupByGenre; read `keys.toList()` | Alpha, beta, zen | unit |
| `UNKNOWN_GENRE group is always last` | genres "Zebra", "", "Alpha" (the blank one becomes "Other") | groupByGenre; read `keys.toList()` | Alpha, Zebra, Other | unit |
| `UNKNOWN_GENRE is last even when it sorts before other genres` | genre "" plus "Zoo" and "Another" (all would sort before or after "Other") | keys order | "Other" last | unit |
| `groupByGenre keeps order sort within each group` | 3 podcasts in one genre, each order in turn | groupByGenre(order) | group list equals `sortPodcasts(order)` of that subset, including id tie-break | unit |
| `groupByGenre of empty list returns empty map` | emptyList | groupByGenre | empty map | unit |
| `groupByGenre trims genre before grouping` | " News " and "News" | groupByGenre | one group "News" | unit |
| `groupByGenre merges a literal "other" genre with UNKNOWN_GENRE` | one podcast with genre "other", one with blank genre | groupByGenre | one group, placed last. The spec does not say how a case-variant of `UNKNOWN_GENRE` is named. The coder must decide; the test asserts only a single, last group | unit |

### `Episode`, `PlaybackState`, `PodcastSearchResult`, `Podcast` defaults (`DomainModelsTest`)
| name | given | when | then | level |
|---|---|---|---|---|
| `Podcast defaults match the spec` | `Podcast(feedUrl = "u", title = "t")` | read fields | id 0, author "", description "", artworkUrl null, genre "", genreOverride null, addedAt 0, lastUpdated 0, customOrder 0 | unit |
| `Episode defaults match the spec` | `Episode(podcastId = 1, guid = "g", title = "t", audioUrl = "a")` | read fields | id 0, description "", durationMs null, publishedAt null | unit |
| `models are value-equal and copyable` | two equal instances of each model; `copy` with one field changed | `==` / `hashCode` | equal / not equal as expected | unit |
| `UNKNOWN_GENRE constant is "Other"` | none | read | "Other" | unit |
| `domain package has no Android imports` | grep of `app/src/main/java/com/punyplatipus/podouvir/domain` for `import android.` / `androidx.` | run by the tester (not a JUnit test) | no matches | unit (manual check) |

### Out of scope
- UI label localisation of `UNKNOWN_GENRE`: UI concern, covered by the screen tasks.
- Accent-aware or locale-aware title collation: the spec says only case-insensitive.
- Performance with very large lists: no requirement, and the functions are trivial `sortedWith` and `groupBy` calls.
- Room entity and mapper behaviour: covered by task 003.
- `PlaybackState`, `Episode` and `PodcastSearchResult` validation (for example negative positions): no validation exists in the spec.

### Summary of counts
31 cases in total, all `unit` level: 6 `effectiveGenre`, 9 `sortPodcasts`, 11 `groupByGenre`, 5 model checks (one of them a manual grep). `room` and `ui` levels are not needed for pure Kotlin.

## Implementation notes
- Files: `domain/model/Podcast.kt` (rewritten), new `Episode.kt`, `PlaybackState.kt`, `PodcastSearchResult.kt`.
- `sortPodcasts` uses a primary comparator then `thenBy { id }`; TITLE uses `lowercase()`.
- `groupByGenre`: groups by lowercased effective genre; a case-variant of "Other" maps to the key exactly
  `UNKNOWN_GENRE`; other groups are named after the first podcast in sorted order. Keys sorted by lowercase name,
  UNKNOWN_GENRE last. Result is a LinkedHashMap (iteration order preserved).
- Build: not run: no SDK. No other main code constructed `Podcast`, so no callers needed updating.
- Tester: update `PodcastSortingTest.kt` to the new constructor (`feedUrl` is required).

## Test results
Not run: no SDK (`java: command not found`). CI runs them. Reviewed by eye against the implementation; no EXPECTED FAIL found.

| case | result |
|---|---|
| PodcastGenreTest: 6 `effectiveGenre` cases | NOT RUN |
| PodcastSortingTest: 9 `sortPodcasts` cases | NOT RUN |
| PodcastGenreTest: 11 `groupByGenre` cases | NOT RUN |
| DomainModelsTest: Podcast defaults, Episode defaults, value-equal/copyable, UNKNOWN_GENRE constant | NOT RUN |
| No Android imports in `domain/` (manual grep) | PASS (no matches) |
