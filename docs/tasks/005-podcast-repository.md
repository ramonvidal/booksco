# 005 PodcastRepository and feed fetching
Plan item: M1 — `PodcastRepository`: subscribe, unsubscribe, refresh, observe library
Status: done
Depends on: 001, 002, 003, 004

## Goal
Single entry point for the UI to search, subscribe, refresh and observe podcasts. Combines the iTunes
client, feed download, RSS parser and Room. Room stays the source of truth.

## Files
- `app/src/main/java/com/punyplatipus/podouvir/data/network/FeedFetcher.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/data/repository/PodcastRepository.kt` (new: interface)
- `app/src/main/java/com/punyplatipus/podouvir/data/repository/DefaultPodcastRepository.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/di/RepositoryModule.kt` (new: binds both interfaces)
- Tests: `app/src/test/java/com/punyplatipus/podouvir/data/repository/` — Robolectric with in-memory Room,
  real `RssParser`, a fake `FeedFetcher` serving fixtures, a fake `ItunesApi`, fixed `java.time.Clock`,
  `StandardTestDispatcher` as the IO dispatcher. `OkHttpFeedFetcher` tested with `MockWebServer`.

## Public API
```kotlin
package com.punyplatipus.podouvir.data.network

interface FeedFetcher {
    /** Downloads [url] and passes the body stream to [block]; closes it afterwards.
     *  Throws java.io.IOException on network failure or non-2xx status. */
    suspend fun <T> fetch(url: String, block: (java.io.InputStream) -> T): T
}

class OkHttpFeedFetcher @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FeedFetcher
```
```kotlin
package com.punyplatipus.podouvir.data.repository

interface PodcastRepository {
    fun observeLibrary(): Flow<List<Podcast>>
    fun observeEpisodes(podcastId: Long): Flow<List<Episode>>
    suspend fun search(term: String): Result<List<PodcastSearchResult>>
    /** Downloads + parses the feed, stores podcast and episodes. Returns the podcast id.
     *  feedUrl is normalised by trimming whitespace only (no case or trailing-slash changes).
     *  Already subscribed (same normalised feedUrl) → success with the existing id, nothing re-downloaded. */
    suspend fun subscribe(feedUrl: String, fallbackGenre: String? = null): Result<Long>
    suspend fun unsubscribe(podcastId: Long)
    /** Re-downloads the feed; inserts new episodes; updates title/author/description/artwork/genre
     *  (never genreOverride, addedAt or customOrder). Returns the number of new episodes. */
    suspend fun refresh(podcastId: Long): Result<Int>
    /** Pass null or blank to clear the override. */
    suspend fun setGenreOverride(podcastId: Long, genre: String?)
}

class DefaultPodcastRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val searchClient: ItunesSearchClient,
    private val feedFetcher: FeedFetcher,
    private val rssParser: RssParser,
    private val clock: java.time.Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PodcastRepository
```

## Acceptance criteria
- `subscribe`:
  - feedUrl is trimmed; `http://` / `https://` required (else failure with `IllegalArgumentException`, no network call).
  - genre = feed genre, else `fallbackGenre`, else `""`. `addedAt` = `lastUpdated` = `clock.millis()`.
  - `customOrder` = (max existing customOrder ?: -1) + 1, so new podcasts go to the end of the custom order.
  - Network error → `Result.failure(IOException)`; `FeedParseException` → failure; nothing is written in either case.
- `unsubscribe` deletes the podcast; its episodes disappear (cascade).
- `refresh`: unknown id → failure (`NoSuchElementException`); returns count of newly inserted episodes (0 on second refresh
  of an unchanged feed); `lastUpdated` = `clock.millis()` only when at least one new episode arrived.
- `refresh`: a blank feed genre keeps the stored genre.
- `search` wraps `ItunesSearchClient.search`; exceptions become `Result.failure`.
- `observeLibrary` / `observeEpisodes` map entities to domain models and re-emit on DB changes.
- `CancellationException` is never swallowed into a `Result.failure` (rethrow it).
- All DB/network work runs on the injected IO dispatcher.
- Hilt: `RepositoryModule` binds `DefaultPodcastRepository` → `PodcastRepository` and `OkHttpFeedFetcher` → `FeedFetcher` (`@Singleton`).

## Test plan

### Test files
- `app/src/test/java/com/punyplatipus/podouvir/data/network/OkHttpFeedFetcherTest.kt` (MockWebServer, plain JVM)
- `app/src/test/java/com/punyplatipus/podouvir/data/repository/DefaultPodcastRepositoryTest.kt` (Robolectric + in-memory Room)

### Fixtures / fakes
- `app/src/test/resources/rss/simple_feed.xml` (reuse from task 002 if present: 3 episodes, genre, artwork) and `simple_feed_plus_one.xml` (same feed + 1 new episode, changed title/author/description/artwork/genre).
- `app/src/test/resources/rss/no_genre_feed.xml` (feed without genre) and `malformed_feed.xml` (not valid RSS).
- `FakeFeedFetcher` (map url → fixture resource or exception; records requested urls; runs `block` on the stream).
- `FakeItunesApi` / fake `ItunesSearchClient` backing (returns canned results or throws).
- Fixed `Clock` (`Clock.fixed`, mutable holder to advance time), `StandardTestDispatcher` as IO dispatcher, real `RssParser`, in-memory `AppDatabase` (DAOs real).

### OkHttpFeedFetcher (level: unit, MockWebServer)
| name | given | when | then |
|---|---|---|---|
| `fetch passes body stream to block and returns its result` | server returns 200 with body "abc" | `fetch(url) { it.readBytes().decodeToString() }` | returns "abc" |
| `fetch closes response after block completes` | 200 response | fetch finishes | connection released (second request on same server succeeds; no leaked-response warning) |
| `fetch throws IOException on non-2xx status` | server returns 404 / 500 | fetch | `IOException` thrown, block never invoked |
| `fetch throws IOException on network failure` | server shut down / socket disconnect at start | fetch | `IOException` |
| `fetch propagates exception thrown by block and still closes body` | 200 response, block throws | fetch | same exception surfaces |
| `fetch runs on injected io dispatcher` | test dispatcher | fetch launched, dispatcher not advanced | not complete until dispatcher runs |

### DefaultPodcastRepository.subscribe (level: room)
| name | given | when | then |
|---|---|---|---|
| `subscribe stores podcast and episodes and returns id` | fake fetcher serves simple_feed | `subscribe(url)` | success(id); podcastDao has 1 podcast, episodeDao has 3 episodes for id |
| `subscribe trims feedUrl` | url with surrounding whitespace | subscribe | fetcher saw trimmed url; stored feedUrl trimmed |
| `subscribe rejects non http(s) url without network call` | "ftp://x", "example.com/feed", "" | subscribe | failure with `IllegalArgumentException`; fetcher request count 0 |
| `subscribe uses feed genre over fallback` | feed has genre, fallback "News" | subscribe | stored genre = feed genre |
| `subscribe uses fallbackGenre when feed has none` | no_genre_feed, fallback "News" | subscribe | genre "News" |
| `subscribe uses empty genre when neither present` | no_genre_feed, fallback null | subscribe | genre "" |
| `subscribe sets addedAt and lastUpdated from clock` | clock fixed at T | subscribe | both == T |
| `subscribe assigns customOrder 0 for first podcast` | empty DB | subscribe | customOrder 0 |
| `subscribe assigns customOrder max plus one` | existing podcasts with customOrder 0 and 5 | subscribe new feed | customOrder 6 |
| `subscribe on already subscribed url returns existing id without download` | podcast with same feedUrl exists | subscribe again | success(existing id); fetcher count unchanged; episode count unchanged |
| `subscribe treats url differing only by normalisation as same` | stored via "https://Example.com/feed/ " style variant (per normaliser from tasks 001/003) | subscribe equivalent url | existing id returned |
| `subscribe returns IOException failure on network error and writes nothing` | fetcher throws IOException | subscribe | failure(IOException); DB empty |
| `subscribe returns failure on FeedParseException and writes nothing` | malformed_feed | subscribe | failure(`FeedParseException`); DB empty |
| `subscribe rethrows CancellationException` | fetcher throws CancellationException | subscribe | CancellationException propagates, not wrapped in Result |

### unsubscribe (level: room)
| name | given | when | then |
|---|---|---|---|
| `unsubscribe removes podcast and cascades episodes` | subscribed podcast with episodes | `unsubscribe(id)` | podcast gone from DAO; episodes empty |
| `unsubscribe unknown id is a no-op` | empty DB | unsubscribe(999) | no exception |
| `unsubscribe leaves other podcasts intact` | two podcasts | unsubscribe one | other and its episodes remain |

### refresh (level: room)
| name | given | when | then |
|---|---|---|---|
| `refresh inserts only new episodes and returns count` | subscribed to simple_feed; fetcher now serves simple_feed_plus_one | `refresh(id)` | success(1); 4 episodes total, no duplicates |
| `refresh twice on unchanged feed returns 0` | refreshed once already | refresh again | success(0); episode count unchanged |
| `refresh updates title author description artwork genre` | changed metadata in plus_one feed | refresh | stored fields match new feed |
| `refresh never changes genreOverride addedAt customOrder` | override set, custom order set | refresh | those three unchanged |
| `refresh updates lastUpdated only when new episodes arrive` | clock advanced to T2 | refresh with new episode, then refresh unchanged with clock T3 | lastUpdated == T2 after first, still T2 after second |
| `refresh preserves existing episode state` | episode with playback position/played flag | refresh | state of existing episodes untouched |
| `refresh unknown id returns NoSuchElementException failure` | empty DB | refresh(42) | failure(`NoSuchElementException`); fetcher count 0 |
| `refresh network error returns IOException failure and changes nothing` | fetcher throws | refresh | failure(IOException); DB unchanged |
| `refresh parse error returns failure and changes nothing` | malformed feed | refresh | failure(`FeedParseException`); DB unchanged |
| `refresh rethrows CancellationException` | fetcher throws CancellationException | refresh | propagates |

### search (level: unit)
| name | given | when | then |
|---|---|---|---|
| `search returns client results wrapped in success` | fake returns 2 results | `search("kotlin")` | success with those 2 |
| `search returns empty list for no results` | fake returns empty | search | success(empty) |
| `search wraps exceptions in failure` | fake throws IOException | search | failure(IOException) |
| `search rethrows CancellationException` | fake throws CancellationException | search | propagates |

### setGenreOverride (level: room)
| name | given | when | then |
|---|---|---|---|
| `setGenreOverride stores value` | subscribed podcast | `setGenreOverride(id, "Comedy")` | stored genreOverride "Comedy" |
| `setGenreOverride null clears override` | override set | `setGenreOverride(id, null)` | genreOverride null |
| `setGenreOverride blank clears override` | override set | `setGenreOverride(id, "   ")` | genreOverride null |

### observeLibrary / observeEpisodes (level: room, Turbine)
| name | given | when | then |
|---|---|---|---|
| `observeLibrary emits empty list initially` | empty DB | collect | first emission empty |
| `observeLibrary re-emits on subscribe and unsubscribe` | collecting | subscribe, then unsubscribe | emissions: [], [podcast], [] with domain `Podcast` models |
| `observeLibrary reflects genre override change` | subscribed | setGenreOverride | new emission with updated field |
| `observeEpisodes emits episodes mapped to domain models` | subscribed podcast | collect | list of 3 `Episode` with expected titles/guids |
| `observeEpisodes re-emits after refresh adds episodes` | collecting | refresh with new episode | new emission with 4 episodes |
| `observeEpisodes emits empty for unknown podcast id` | empty DB | collect(999) | empty list |

### Dispatcher behaviour (level: room)
| name | given | when | then |
|---|---|---|---|
| `subscribe does work on injected io dispatcher` | `StandardTestDispatcher` not advanced | launch subscribe | not complete until dispatcher advanced; then completes |

### Hilt wiring
`RepositoryModule` is covered by compile (kapt/ksp Hilt graph) via the build; no dedicated test.

### Level count
- unit: 10 (6 OkHttpFeedFetcher via MockWebServer, 4 search)
- room: 40 (subscribe 14, unsubscribe 3, refresh 10, setGenreOverride 3, observe 6, dispatcher 1, plus feed-level cases counted once)
- ui: 0

Robolectric/room is justified because Room in-memory requires an Android context on the JVM and cascade/ordering behaviour must be checked against real SQLite.

### Out of scope
- Real network and real iTunes API (covered by task 004 tests).
- RSS parsing details (task 002) and DAO query correctness in isolation (task 003); only exercised through the repository.
- Hilt graph instantiation test and `OkHttpClient` configuration (timeouts, user agent).
- Scheduling of periodic refresh and UI behaviour (later tasks).

## Implementation notes
Files: data/network/FeedFetcher.kt, data/repository/PodcastRepository.kt, DefaultPodcastRepository.kt, di/RepositoryModule.kt.
- Build not run: no SDK.
- Subscribe/refresh parse before any DB write, so failures write nothing. Podcast + episode inserts are not in one
  transaction (repo has no DB handle per the specified constructor).
- Invalid URL text in OkHttpFeedFetcher (Request.Builder.url) is rethrown as IOException.
- Only `Exception` is caught into Result (CancellationException rethrown); `refresh` update keeps genreOverride/addedAt/customOrder via copy.
- refresh keeps the stored genre when the feed genre is blank (`feed.genre.ifBlank { existing.genre }`).

## Test results
round 1: compile errors fixed (Turbine `test` returns Unit; replaced value-returning uses with `flow.first()`).
Not run: no JDK/Android SDK on this machine (`java` not found). CI runs them. Tests reviewed by eye against the implementation.
Plan's ~47 cases merged into 36 tests (near-duplicates combined; every acceptance criterion covered). Dropped: "url differing only by normalisation" (spec says trim only, so no normaliser exists; covered by the trim case).
Added fixtures: `rss/no_genre_feed.xml`, `rss/simple_feed_plus_one.xml`; existing `rss/simple_feed.xml` and `rss/malformed.xml` reused.

OkHttpFeedFetcherTest (MockWebServer)
| case | result |
|---|---|
| fetch passes body stream to block, returns its result and releases connection | NOT RUN |
| fetch throws IOException on non-2xx status without invoking block | NOT RUN |
| fetch throws IOException on network failure | NOT RUN |
| fetch throws IOException on invalid url (added by tester) | NOT RUN |
| fetch propagates exception thrown by block and connection stays usable | NOT RUN |
| fetch runs on injected io dispatcher | NOT RUN |

DefaultPodcastRepositoryTest (Robolectric + in-memory Room)
| case | result |
|---|---|
| subscribe stores podcast and episodes and returns id | NOT RUN |
| subscribe trims feedUrl | NOT RUN |
| subscribe rejects non http(s) url without network call | NOT RUN |
| subscribe genre is feed genre, else fallback, else empty | NOT RUN |
| subscribe sets addedAt and lastUpdated from clock | NOT RUN |
| subscribe assigns customOrder 0 first and max plus one afterwards | NOT RUN |
| subscribe on already subscribed url returns existing id without download | NOT RUN |
| subscribe returns IOException failure on network error and writes nothing | NOT RUN |
| subscribe returns failure on FeedParseException and writes nothing | NOT RUN |
| subscribe rethrows CancellationException | NOT RUN |
| subscribe does work on injected io dispatcher | NOT RUN |
| unsubscribe removes podcast and cascades episodes | NOT RUN |
| unsubscribe unknown id is a no-op and leaves other podcasts intact | NOT RUN |
| refresh inserts only new episodes, returns count, and second refresh returns 0 | NOT RUN |
| refresh updates title author description artwork genre | NOT RUN |
| refresh keeps stored genre when feed genre is blank (orchestrator clarification) | NOT RUN (implementation now has `feed.genre.ifBlank { existing.genre }`; should pass) |
| refresh never changes genreOverride addedAt customOrder | NOT RUN |
| refresh updates lastUpdated only when new episodes arrive | NOT RUN |
| refresh preserves existing episode state | NOT RUN |
| refresh unknown id returns NoSuchElementException failure without download | NOT RUN |
| refresh network error returns IOException failure and changes nothing | NOT RUN |
| refresh parse error returns failure and changes nothing | NOT RUN |
| refresh rethrows CancellationException | NOT RUN |
| search returns client results wrapped in success, empty for none | NOT RUN |
| search wraps exceptions in failure | NOT RUN |
| search rethrows CancellationException | NOT RUN |
| setGenreOverride stores value, null and blank clear it | NOT RUN |
| observeLibrary emits empty, then re-emits on subscribe, override change and unsubscribe | NOT RUN |
| observeEpisodes emits mapped episodes, re-emits after refresh, empty for unknown id | NOT RUN |
