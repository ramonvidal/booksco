# 012 Replace iTunes search with fyyd.de + Podcast Index
Plan item: M2 — Discover search uses free/open directories (owner decision 2026-10-22)
Status: done
Depends on: 006 (it also edits DefaultPodcastRepository — must be finished first). Read `docs/tasks/M2-conventions.md`.

## Goal
Drop the proprietary iTunes Search API (F-Droid *NonFreeNet*). Search **fyyd.de** (open, no key) always, and
**Podcast Index** (open, free key) when credentials are configured at build time (`BuildConfig.PODCAST_INDEX_KEY/SECRET`,
already wired in `app/build.gradle.kts`; empty strings when absent). Merge both result lists.

## Files
- DELETE `data/network/ItunesApi.kt`, `data/network/ItunesSearchClient.kt`, their tests
  (`ItunesApiTest`, `ItunesDtoMappingTest`, `ItunesSearchClientTest`) and `app/src/test/resources/itunes/`.
- `app/src/main/java/com/punyplatipus/podouvir/data/search/PodcastSearchService.kt` (new: interface + `CompositeSearchService`)
- `app/src/main/java/com/punyplatipus/podouvir/data/search/FyydSearchService.kt` (new: Retrofit API, DTOs, categories, service)
- `app/src/main/java/com/punyplatipus/podouvir/data/search/PodcastIndexSearchService.kt` (new: Retrofit API, DTOs, auth interceptor, service)
- `app/src/main/java/com/punyplatipus/podouvir/di/NetworkModule.kt` (modify: remove iTunes, provide the two APIs; `User-Agent: PodOuvir/${BuildConfig.VERSION_NAME}`)
- `app/src/main/java/com/punyplatipus/podouvir/di/RepositoryModule.kt` (modify: bind `CompositeSearchService` → `PodcastSearchService`)
- `app/src/main/java/com/punyplatipus/podouvir/data/repository/DefaultPodcastRepository.kt` (modify: constructor takes
  `searchService: PodcastSearchService` instead of `ItunesSearchClient`; `search` delegates to it)
- `NetworkJson` currently lives in ItunesApi.kt: move it to `data/network/NetworkJson.kt` (same name and settings).
- Tests (MockWebServer, JVM): `data/search/FyydSearchServiceTest.kt`, `PodcastIndexSearchServiceTest.kt`,
  `CompositeSearchServiceTest.kt`; fixtures in `app/src/test/resources/search/`. Update `DefaultPodcastRepositoryTest`
  to use a fake `PodcastSearchService` instead of the iTunes fake.

## Public API
```kotlin
package com.punyplatipus.podouvir.data.search

interface PodcastSearchService {
    /** Trimmed [term]; blank → empty list, no network. Results without feed URL or title are dropped; unique by feedUrl. */
    suspend fun search(term: String, limit: Int = 25): List<PodcastSearchResult>
}

// ---- fyyd: GET https://api.fyyd.de/0.2/search/podcast?term=<t>&count=<n>
// Response: {"status":1,"msg":"ok","data":[{"title","xmlURL","imgURL","author","categories":[203,...]}]}  (data = [] when none)
interface FyydApi {
    @GET("0.2/search/podcast")
    suspend fun searchPodcasts(@Query("term") term: String, @Query("count") count: Int): FyydResponse
}
@Serializable data class FyydResponse(val status: Int = 0, val data: List<FyydPodcastDto> = emptyList())
@Serializable data class FyydPodcastDto(
    val title: String? = null, val xmlURL: String? = null, val imgURL: String? = null,
    val author: String? = null, val categories: List<Int> = emptyList(),
)
/** Top-level fyyd category name for any category or subcategory id; null if unknown. */
fun fyydGenreName(categoryId: Int): String?
fun FyydPodcastDto.toSearchResultOrNull(): PodcastSearchResult?   // genre = fyydGenreName(first category) ?: ""
class FyydSearchService @Inject constructor(private val api: FyydApi, @IoDispatcher private val io: CoroutineDispatcher) : PodcastSearchService

// ---- Podcast Index: GET https://api.podcastindex.org/api/1.0/search/byterm?q=<t>&max=<n>
// Headers: User-Agent, X-Auth-Key: key, X-Auth-Date: unix seconds, Authorization: lowercase hex SHA-1 of (key + secret + date)
// Response: {"status":"true","feeds":[{"title","url","author","image","artwork","categories":{"102":"Technology"}|null}],"count":n}
data class PodcastIndexCredentials(val key: String, val secret: String) { val isConfigured: Boolean /* both non-blank */ }
class PodcastIndexAuthInterceptor(private val credentials: PodcastIndexCredentials, private val clock: java.time.Clock) : Interceptor
fun podcastIndexAuthorization(key: String, secret: String, epochSeconds: Long): String
interface PodcastIndexApi {
    @GET("api/1.0/search/byterm")
    suspend fun searchByTerm(@Query("q") term: String, @Query("max") max: Int): PodcastIndexResponse
}
@Serializable data class PodcastIndexResponse(val feeds: List<PodcastIndexFeedDto> = emptyList())
@Serializable data class PodcastIndexFeedDto(
    val title: String? = null, val url: String? = null, val author: String? = null,
    val image: String? = null, val artwork: String? = null, val categories: Map<String, String>? = null,
)
fun PodcastIndexFeedDto.toSearchResultOrNull(): PodcastSearchResult?  // artwork = artwork ?: image (blank → null); genre = value of the lowest numeric category key, else ""
class PodcastIndexSearchService @Inject constructor(
    private val api: PodcastIndexApi, private val credentials: PodcastIndexCredentials,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PodcastSearchService   // not configured → empty list, no request

class CompositeSearchService @Inject constructor(
    private val fyyd: FyydSearchService,
    private val podcastIndex: PodcastIndexSearchService,
    private val credentials: PodcastIndexCredentials,
) : PodcastSearchService
```
Top-level fyyd categories (ids; subcategory ids fall between a parent and the next parent):
100 Arts, 108 Business, 115 Comedy, 119 Education, 124 Fiction, 128 Government, 129 History, 130 Health & Fitness,
137 Kids & Family, 142 Leisure, 151 Music, 155 News, 163 Religion & Spirituality, 171 Science, 181 Society & Culture,
187 Sports, 203 Technology, 204 True Crime, 205 TV & Film (205 has subcategories up to 210). Ids < 100 or > 210 → null.
These names intentionally match Apple/Podcast Index category names so genres group together.

## Acceptance criteria
- iTunes code, tests, fixtures and base URL are gone; no reference to `itunes.apple.com` remains (grep).
- fyyd: request path `/0.2/search/podcast` with `term` (URL-encoded) and `count`; mapping and genre lookup as specified
  (e.g. 203 → Technology, 102 → Arts, 210 → TV & Film, 99 → null); `data: []` → empty list.
- Podcast Index: when configured, sends the 3 auth headers with a correct SHA-1 (test with a fixed clock and a known
  vector computed in the test via `MessageDigest`); not configured → no request, empty list.
- Composite: blank term → empty, no requests. Configured: both run concurrently; merged = Podcast Index results first,
  then fyyd results whose feedUrl is new; truncated to `limit`. One provider failing → the other's results; both failing
  → throws the fyyd exception. Not configured → fyyd only (its exception propagates). `CancellationException` is never swallowed.
- `DefaultPodcastRepository.search` still returns `Result`; its existing tests pass with the new fake.
- Hilt: `PodcastIndexCredentials(BuildConfig.PODCAST_INDEX_KEY, BuildConfig.PODCAST_INDEX_SECRET)` provided in NetworkModule;
  Podcast Index Retrofit uses a client derived from the shared OkHttpClient (`newBuilder()`) plus the auth interceptor.

## Test plan
All cases are `unit` (JVM, MockWebServer or hand-written fakes, `runTest`). Exception: the two repository cases stay `room`
(they live in the existing Robolectric class `DefaultPodcastRepositoryTest`, which already uses the in-memory DB; no new heavy test is added).
Total: 18 cases (16 unit, 2 room).

### Test files
- `app/src/test/java/com/punyplatipus/podouvir/data/search/FyydSearchServiceTest.kt`
- `app/src/test/java/com/punyplatipus/podouvir/data/search/PodcastIndexSearchServiceTest.kt`
- `app/src/test/java/com/punyplatipus/podouvir/data/search/CompositeSearchServiceTest.kt`
- `app/src/test/java/com/punyplatipus/podouvir/data/repository/DefaultPodcastRepositoryTest.kt` (modify)
- Deleted with the iTunes code: `ItunesApiTest`, `ItunesDtoMappingTest`, `ItunesSearchClientTest`, `src/test/resources/itunes/`.

### Fixtures and fakes
- `app/src/test/resources/search/fyyd_ok.json`: 4 entries: full (categories `[203,100]`), no `imgURL`/`author`/`categories`, blank `xmlURL`, duplicate `xmlURL` of the first. Plus `fyyd_empty.json` (`{"status":1,"msg":"ok","data":[]}`) and `fyyd_malformed.json` (truncated JSON).
- `app/src/test/resources/search/podcastindex_ok.json`: feeds with `categories` as map (`{"104":"X","102":"Technology"}`), `categories: null`, `artwork` blank with `image` set, missing `url`, missing `title`, duplicate `url`; plus `podcastindex_empty.json` (`{"status":"true","feeds":[],"count":0}`).
- `FakeSearchService` in `app/src/test/java/.../testutil/FakeSearchService.kt` implements `PodcastSearchService`: `var results: List<PodcastSearchResult>`, `var error: Throwable?`, optional `var gate: CompletableDeferred<Unit>?` awaited before returning, `val calls = mutableListOf<Pair<String, Int>>()` (term, limit). It replaces `FakeItunesApi` in `DefaultPodcastRepositoryTest`.
- The composite tests need three `FakeSearchService` instances. `CompositeSearchService` takes the concrete `FyydSearchService` and `PodcastIndexSearchService` (final Kotlin classes), so use MockK (`mockk<FyydSearchService>()` with `coEvery { search(any(), any()) }`), or the tasks's implementer may mark them `open`. Prefer MockK to keep production signatures unchanged.
- MockWebServer plus Retrofit built with `NetworkJson` converter, `StandardTestDispatcher(testScheduler)` as `io`; fixed `Clock.fixed(Instant.ofEpochSecond(1_700_000_000), UTC)`.

### FyydSearchServiceTest (and `fyydGenreName`, `toSearchResultOrNull`)
| name | level | given | when | then |
|---|---|---|---|---|
| `search sends GET to 0_2 search podcast with encoded term and count` | unit | server returns `fyyd_ok.json`; term `"  c++ & café  "` | `search(term, limit = 10)` | request path is `/0.2/search/podcast`; query `term` decodes to `"c++ & café"` (trimmed, URL-encoded on the wire, no raw `&`), `count=10`; default limit sends `count=25` |
| `fyydGenreName maps every category id to its top-level name` | unit | table of ids | `fyydGenreName(id)` | 100 and 107 Arts (a subcategory between parents), 203 Technology, 102 Arts, 108 Business, 210 TV & Film, 205 TV & Film, 204 True Crime; boundaries 99, 211, 0, -1 and `Int.MAX_VALUE` give null |
| `toSearchResultOrNull maps dto and uses first category for genre` | unit | full dto with categories `[203,100]`; dto with empty categories; dto with first category unknown (`[999,203]`); dto with null `author`/`imgURL` | map each | `PodcastSearchResult(feedUrl = xmlURL, title, author, artworkUrl = imgURL, genre)` with genre `Technology`; `""` for empty categories; `""` for unknown first category; null author and artwork preserved as the domain type allows (author per domain default) |
| `search drops results without feed url or title, dedupes by feedUrl and short-circuits blank terms` | unit | `fyyd_ok.json` (blank xmlURL, null/blank title, duplicate xmlURL); also terms `""`, `"   "` | `search` | invalid entries removed, first occurrence of a duplicate kept, order preserved; blank terms return `emptyList()` and `server.requestCount == 0` |
| `search returns empty for empty data and throws on http error or malformed json` | unit | `fyyd_empty.json`; separately HTTP 500; `fyyd_malformed.json`; server shutdown | `search("x")` | `[]` for empty data; `HttpException` for 500; `SerializationException` (or the library's decode error) for malformed; `IOException` for dead server; none of them returns a partial list |

### PodcastIndexSearchServiceTest (and auth, credentials, mapping)
| name | level | given | when | then |
|---|---|---|---|---|
| `podcastIndexAuthorization returns lowercase hex sha1 of key secret and date` | unit | key `"K"`, secret `"S"`, epoch `1_700_000_000` | call; compute expected via `MessageDigest.getInstance("SHA-1")` over `"KS1700000000"` | equal strings; length 40; only `[0-9a-f]`; changing any of key, secret or date changes the result; a digest with leading zero bytes still has 40 chars (padded) |
| `auth interceptor adds X-Auth-Key, X-Auth-Date and Authorization from the clock` | unit | real `OkHttpClient` with `PodcastIndexAuthInterceptor(creds, fixedClock)` against MockWebServer | one request | recorded headers: `X-Auth-Key == key`, `X-Auth-Date == "1700000000"`, `Authorization ==` expected SHA-1 vector; an existing `User-Agent` set by the shared client is not removed |
| `search sends GET to api 1_0 search byterm and maps feeds` | unit | configured credentials, `podcastindex_ok.json`, term `" a b "`, limit 7 | `search` | path `/api/1.0/search/byterm`, `q` is `"a b"` encoded, `max=7`; genre is the value of the lowest numeric key (`"102"` beats `"104"`; non-numeric keys ignored), `""` for null/empty map; artwork is `artwork` else `image`, blank both gives null; entries with blank `url` or `title` dropped; duplicate `url` deduped; blank term returns empty with zero requests |
| `isConfigured is true only when key and secret are both non-blank, otherwise search makes no request` | unit | credentials `("k","s")`, `("","s")`, `("k","")`, `("  ","s")`, `("","")` | `isConfigured`; then `search("x")` with each unconfigured pair | only the first is configured; unconfigured returns `emptyList()` and `server.requestCount == 0` |
| `search returns empty for empty feeds and throws on http error or malformed json` | unit | `podcastindex_empty.json`; HTTP 401/500; truncated JSON; response without `feeds` key | `search("x")` | `[]` for empty and for missing `feeds`; exceptions propagate (no swallowing) for the error cases |

### CompositeSearchServiceTest
| name | level | given | when | then |
|---|---|---|---|---|
| `blank term returns empty without calling any provider` | unit | configured credentials; mocked providers | `search("")` and `search("  ")` | `emptyList()`; neither provider called |
| `configured search merges podcast index first then new fyyd results truncated to limit` | unit | PI returns [A, B]; fyyd returns [B (different title), C, D]; limit 3, then limit 25 | `search("x", limit)` | limit 3 gives `[A, B(from PI), C]`; limit 25 gives `[A, B, C, D]`; PI copy of B wins; limit smaller than the PI list truncates PI results; both providers receive the trimmed term |
| `configured providers run concurrently` | unit | both fakes suspend on a gate (`CompletableDeferred`) and record start | start `search` in `runTest`, advance until idle, check both started before either completes, then release gates | both providers invoked before any result is returned (not sequential); result as in the merge case |
| `one provider failing returns the other's results, both failing throws the fyyd exception` | unit | PI throws `IOException("pi")`, fyyd ok; then fyyd throws `IOException("fyyd")`, PI ok; then both throw | `search` | first: fyyd results; second: PI results; third: throws the fyyd exception instance (message `"fyyd"`), not the PI one |
| `not configured uses fyyd only and propagates its exception` | unit | credentials `("","")` | `search("x")` with fyyd ok; then fyyd throwing | PI never called; results equal fyyd's (deduped, limited); exception from fyyd propagates unchanged |
| `CancellationException is never swallowed` | unit | configured; PI throws `CancellationException`, fyyd ok; then fyyd throws `CancellationException`, PI ok; also cancel the caller job mid-flight | `search` | `CancellationException` is rethrown in each variant (no fallback result, not converted to a failure); cancelling the caller cancels both child calls |

### DefaultPodcastRepositoryTest: adaptation to the new fake (Result semantics unchanged)
Setup change: remove imports of `ItunesApi`, `ItunesPodcastDto`, `ItunesSearchClient`, `ItunesSearchResponse` and the `FakeItunesApi` class; replace field `itunes: FakeItunesApi` with `search: FakeSearchService` (initialised in `setUp`), and in `newRepo` pass `search` instead of `ItunesSearchClient(itunes, io)` (constructor argument `searchService: PodcastSearchService`). Nothing else in that file references iTunes.

| name | level | given | when | then |
|---|---|---|---|---|
| `search returns service results wrapped in success, empty for none` (adapt existing `search returns client results wrapped in success, empty for none`) | room | `search.results = listOf(PodcastSearchResult("https://a/f", "A", "Au", null, "News"), PodcastSearchResult("https://b/f", "B", "Bu", null, "Tech"))`, then `emptyList()` (replaces the `ItunesPodcastDto` list) | `repo.search("kotlin")` | success with feed urls `[https://a/f, https://b/f]`; then success with an empty list; fake recorded term `"kotlin"` |
| `search wraps exceptions in failure and rethrows CancellationException` (adapt existing `search wraps exceptions in failure` and `search rethrows CancellationException`; keep them as two `@Test`s, counted here as one case) | room | `search.error = IOException("offline")`; then `search.error = CancellationException("cancelled")` (replaces `itunes.error`) | `repo.search("kotlin")` | first: `Result.failure` with `IOException`; second: `CancellationException` thrown out of `search` (not wrapped in `Result`) |

### Acceptance criteria coverage
- iTunes removal: not a runtime test. Verify with `grep -ri "itunes" app/src app/build.gradle.kts` (expect no hits, including `itunes.apple.com`) and by the test suite compiling after deletion of the three iTunes tests and `resources/itunes/`.
- fyyd request, mapping, genre ids, empty data: fyyd cases 1-5.
- Podcast Index auth headers, SHA-1 vector, not configured: PI cases 1, 2, 4.
- Composite behaviours: all six composite cases.
- Repository still returns `Result`: the two repository cases.
- Hilt wiring (credentials provided from BuildConfig, `newBuilder()` client plus interceptor, `CompositeSearchService` binding): see out of scope; compile-time check by `./gradlew :app:assembleDebug` (kapt/ksp Hilt graph validation).

### Out of scope
- `NetworkModule` and `RepositoryModule` wiring (BuildConfig values, `User-Agent: PodOuvir/${VERSION_NAME}`, `newBuilder()` reuse): glue code with no logic; Hilt graph validation at build time covers missing bindings, and a Hilt test would need an instrumented or Robolectric Hilt setup out of proportion to the task. The interceptor itself is tested directly.
- `NetworkJson` move to `data/network/NetworkJson.kt`: same name and settings, exercised by every MockWebServer test through the converter.
- Real network calls to fyyd.de and podcastindex.org: flaky and need credentials; the fixtures reproduce the documented response shapes.
- UI behaviour of Discover (task 008) and ViewModel handling of search errors: tested in their own tasks against `FakePodcastRepository`.
- Retry, rate limiting and caching of search results: not specified.

## Implementation notes
- Added `data/network/NetworkJson.kt`, `data/search/{PodcastSearchService,FyydSearchService,PodcastIndexSearchService}.kt`; rewired `NetworkModule` (fyyd + Podcast Index Retrofit, credentials from BuildConfig, `User-Agent: PodOuvir/${VERSION_NAME}`), `RepositoryModule` (binds `CompositeSearchService`), `DefaultPodcastRepository` (`searchService: PodcastSearchService`).
- Deleted iTunes main/test code and `src/test/resources/itunes/`. `DefaultPodcastRepositoryTest` still references the iTunes fake and must be updated by the tester; fixtures/new tests not written.
- Remaining "itunes" grep hits are the iTunes RSS XML namespace in `data/rss/*` and its tests (legitimate, unrelated to the search API).
- Composite: if fyyd fails and PI fails, fyyd exception thrown; dedupe/truncate applied after merge. Not configured: fyyd only. Unconfigured PI service itself returns empty without a request.
- Build not run: no SDK.

## Test results
No JDK/SDK here (`java: command not found`): NOT RUN; CI runs them. All cases reviewed by eye against the implementation; no spec violations found (no EXPECTED FAIL).

| Case | Result |
|---|---|
| Fyyd: search sends GET to 0_2 search podcast with encoded term and count | NOT RUN |
| Fyyd: fyydGenreName maps every category id to its top-level name | NOT RUN |
| Fyyd: toSearchResultOrNull maps dto and uses first category for genre | NOT RUN |
| Fyyd: search drops results without feed url or title, dedupes, short-circuits blank terms | NOT RUN |
| Fyyd: search returns empty for empty data and throws on http error or malformed json | NOT RUN |
| PI: podcastIndexAuthorization returns lowercase hex sha1 | NOT RUN |
| PI: auth interceptor adds X-Auth-Key, X-Auth-Date and Authorization from the clock | NOT RUN |
| PI: search sends GET to api 1_0 search byterm and maps feeds | NOT RUN |
| PI: isConfigured only when both non-blank, otherwise no request | NOT RUN |
| PI: search returns empty for empty feeds and throws on http error or malformed json | NOT RUN |
| Composite: blank term returns empty without calling any provider | NOT RUN |
| Composite: merges podcast index first then new fyyd results truncated to limit | NOT RUN |
| Composite: configured providers run concurrently | NOT RUN |
| Composite: one provider failing / both failing throws fyyd exception | NOT RUN |
| Composite: not configured uses fyyd only and propagates its exception | NOT RUN |
| Composite: CancellationException is never swallowed | NOT RUN |
| Repository: search returns service results wrapped in success, empty for none | NOT RUN |
| Repository: search wraps exceptions in failure / rethrows CancellationException (2 tests) | NOT RUN |

Notes: grep for `itunes.apple.com` and `Itunes*` search classes in app/src is clean (remaining hits are the RSS itunes namespace and `parseItunesDuration`). Added `testutil/Fixtures.kt` (`loadFixture`, previously inside the deleted ItunesApiTest) and `testutil/FakeSearchService.kt`.
