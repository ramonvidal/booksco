# 004 iTunes Search client
Plan item: M1 — iTunes Search client + RSS parser (with fixture-based tests) — search part
Status: done
Depends on: 001

## Goal
Search podcasts by keyword with the iTunes Search API and map results to `PodcastSearchResult`.
Endpoint: `GET https://itunes.apple.com/search?term=<t>&media=podcast&entity=podcast&limit=<n>`.
Responses come as `text/javascript`, so the JSON converter must not rely on the content type.

## Files
- `app/src/main/java/com/punyplatipus/podouvir/data/network/ItunesApi.kt` (new: Retrofit interface + DTOs)
- `app/src/main/java/com/punyplatipus/podouvir/data/network/ItunesSearchClient.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/di/NetworkModule.kt` (new)
- Tests: `app/src/test/java/com/punyplatipus/podouvir/data/network/` using `MockWebServer`
  (fixtures in `app/src/test/resources/itunes/`). Pure JVM — no Robolectric needed.

## Public API
```kotlin
package com.punyplatipus.podouvir.data.network

interface ItunesApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("media") media: String = "podcast",
        @Query("entity") entity: String = "podcast",
        @Query("limit") limit: Int = 25,
    ): ItunesSearchResponse
}

@Serializable data class ItunesSearchResponse(val resultCount: Int = 0, val results: List<ItunesPodcastDto> = emptyList())

@Serializable data class ItunesPodcastDto(
    val collectionName: String? = null,
    val artistName: String? = null,
    val feedUrl: String? = null,
    val artworkUrl600: String? = null,
    val artworkUrl100: String? = null,
    val primaryGenreName: String? = null,
)

/** Null when feedUrl or collectionName is missing/blank. artwork = 600 ?: 100; author/genre default "". */
fun ItunesPodcastDto.toSearchResultOrNull(): PodcastSearchResult?

/** Json used for all network parsing: ignoreUnknownKeys = true, explicitNulls = false. */
val NetworkJson: Json

class ItunesSearchClient @Inject constructor(
    private val api: ItunesApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    /** Trims [term]; blank → empty list without a network call. Drops unusable results and duplicate feedUrls (keep first). */
    suspend fun search(term: String, limit: Int = 25): List<PodcastSearchResult>
}

/** Helper used by NetworkModule and tests. */
fun createItunesApi(baseUrl: String, client: OkHttpClient, json: Json = NetworkJson): ItunesApi
```
`NetworkModule` (Hilt, SingletonComponent): `@Singleton OkHttpClient` (15s connect/read timeouts,
`User-Agent: PodOuvir/<versionName>` via an interceptor; use `BuildConfig.VERSION_NAME` only if
`buildConfig` is enabled, otherwise a constant `"PodOuvir/0.1"`), and `ItunesApi` via
`createItunesApi("https://itunes.apple.com/", client)`.

## Acceptance criteria
- Request path is `/search` with `term` (URL-encoded, spaces included), `media=podcast`, `entity=podcast`, `limit`.
- Unknown JSON fields are ignored; response served as `text/javascript; charset=utf-8` parses fine.
- Results without `feedUrl` or `collectionName` are dropped; artwork falls back 600 → 100 → null.
- Duplicate `feedUrl`s are reduced to the first one.
- Blank/whitespace term returns `emptyList()` and makes no request.
- HTTP errors (e.g. 500) and IO failures propagate as exceptions (the repository decides how to handle them).

## Test plan

All tests are `unit` (pure JVM, `MockWebServer` + `runTest`); no Robolectric, Room or UI.

### Test files
- `app/src/test/java/com/punyplatipus/podouvir/data/network/ItunesDtoMappingTest.kt` (`toSearchResultOrNull`, `NetworkJson`)
- `app/src/test/java/com/punyplatipus/podouvir/data/network/ItunesApiTest.kt` (`createItunesApi` + `ItunesApi.search` against MockWebServer)
- `app/src/test/java/com/punyplatipus/podouvir/data/network/ItunesSearchClientTest.kt` (`ItunesSearchClient.search`, real `ItunesApi` via MockWebServer, `StandardTestDispatcher` as `ioDispatcher`)

### Fixtures / fakes
- `app/src/test/resources/itunes/search_ok.json`: 3 valid results (one with only artworkUrl100, one with both artworks, extra unknown fields such as `kind`, `trackId`, `genreIds`).
- `app/src/test/resources/itunes/search_mixed.json`: valid results plus entries missing `feedUrl`, missing `collectionName`, blank `feedUrl`, blank `collectionName`, and two entries sharing the same `feedUrl`.
- `app/src/test/resources/itunes/search_empty.json`: `{"resultCount":0,"results":[]}`.
- `app/src/test/resources/itunes/search_malformed.json`: truncated/invalid JSON.
- Helper `loadFixture(name)` in tests (classloader resource read); `MockWebServer` started/shut down in `@Before/@After`; `createItunesApi(server.url("/").toString(), OkHttpClient())`.
- Responses served with header `Content-Type: text/javascript; charset=utf-8`.

### Cases: `ItunesDtoMappingTest` (`toSearchResultOrNull`, `NetworkJson`) - all unit
| name | given | when | then |
|---|---|---|---|
| `maps full dto to search result` | all fields set | mapped | title/author/feedUrl/genre/artwork (600) copied correctly |
| `prefers artworkUrl600 over artworkUrl100` | both artworks | mapped | artwork = 600 url |
| `falls back to artworkUrl100 when 600 missing` | only 100 | mapped | artwork = 100 url |
| `artwork is null when both missing` | no artwork | mapped | artwork null, result non-null |
| `author and genre default to empty string` | artistName/primaryGenreName null | mapped | both "" |
| `returns null when feedUrl is null or blank` | feedUrl null, "", "   " | mapped | null each |
| `returns null when collectionName is null or blank` | name null, "", "   " | mapped | null each |
| `NetworkJson ignores unknown keys and absent nullable fields` | JSON with extra fields and missing optionals | decoded into `ItunesSearchResponse` | no exception, defaults applied (`resultCount=0`, `results=emptyList()` for `{}`) |

### Cases: `ItunesApiTest` (`createItunesApi`, `ItunesApi.search`) - all unit
| name | given | when | then |
|---|---|---|---|
| `sends GET to search path with default params` | server returns `search_ok.json` | `search("kotlin")` | recorded request: method GET, path `/search`, query `term=kotlin`, `media=podcast`, `entity=podcast`, `limit=25` |
| `url-encodes term with spaces and special characters` | term `"foo bar & baz"` | `search(term)` | raw request line has encoded term (`foo%20bar` or `foo+bar`, `%26`); decoded `requestUrl.queryParameter("term")` equals original |
| `passes custom limit` | limit = 5 | `search("x", limit = 5)` | query `limit=5` |
| `parses text/javascript content type` | `search_ok.json` with `Content-Type: text/javascript; charset=utf-8` | call | returns response with expected `resultCount` and `results` size, no exception |
| `ignores unknown fields in results` | fixture with extra fields | call | parsed without error |
| `parses empty results` | `search_empty.json` | call | `results` empty, `resultCount` 0 |
| `http 500 throws HttpException` | server responds 500 | call | `retrofit2.HttpException` with code 500 |
| `malformed json throws` | `search_malformed.json` | call | `SerializationException` (or wrapping exception) propagates |
| `connection failure throws IOException` | server shut down / `SocketPolicy.DISCONNECT_AT_START` | call | `IOException` propagates |

### Cases: `ItunesSearchClientTest` (`ItunesSearchClient.search`) - all unit
| name | given | when | then |
|---|---|---|---|
| `returns mapped results for valid response` | `search_ok.json` | `search("kotlin")` | list of `PodcastSearchResult` in response order with expected fields |
| `trims term before request` | term `"  kotlin  "` | search | recorded request `term=kotlin` |
| `blank term returns empty list without network call` | term `""`, `"   "`, `"\t\n"` | search | `emptyList()` and `server.requestCount == 0` |
| `drops results missing feedUrl or collectionName` | `search_mixed.json` | search | only usable entries remain |
| `deduplicates by feedUrl keeping first` | two entries same feedUrl, different names | search | one result, the first entry's data |
| `returns empty list when API returns no results` | `search_empty.json` | search | `emptyList()` |
| `forwards limit to the API` | limit = 10 | search | recorded request `limit=10` |
| `http error propagates` | 500 response | search | `HttpException` thrown, not swallowed |
| `io failure propagates` | disconnect | search | `IOException` thrown |
| `runs on injected io dispatcher` | `StandardTestDispatcher` as ioDispatcher | search launched but scheduler not advanced | request not yet made until dispatcher is advanced; completes after `advanceUntilIdle()` (confirms `withContext(ioDispatcher)`) |

### Out of scope
- `NetworkModule` Hilt wiring, OkHttp timeouts and the `User-Agent` interceptor: configuration glue verified by the build/Hilt graph compile; optionally one MockWebServer check that the User-Agent interceptor adds `PodOuvir/...` can be added if the interceptor is exposed as a testable function (not part of the public API here).
- Real calls to itunes.apple.com (flaky, non-hermetic).
- Repository-level error handling and caching (task 005).
- UI behavior of the search screen.

Totals: 27 cases, all `unit` (8 mapping, 9 API, 10 client); 0 `room`, 0 `ui`.

## Implementation notes
- Added `data/network/ItunesApi.kt` (API, DTOs, mapper, `NetworkJson`, `createItunesApi`), `data/network/ItunesSearchClient.kt`, `di/NetworkModule.kt`.
- Converter: `json.asConverterFactory("application/json".toMediaType())` (retrofit-kotlinx-serialization), which ignores response Content-Type.
- Client calls `api.search(term = trimmed, limit = limit)` inside `withContext(ioDispatcher)`; errors propagate unchanged.
- User-Agent is the constant `PodOuvir/0.1` (buildConfig disabled).
- Not built: no SDK. Assumes `libs.retrofit.kotlinx.serialization` is already in app/build.gradle.kts.
- Fix: import corrected to `retrofit2.converter.kotlinx.serialization.asConverterFactory` (official square converter package).

## Test results
Not run: no JDK/SDK on this machine (`java: command not found`); CI runs them. Tests reviewed by eye against the implementation; no spec violations found.

Files: `ItunesDtoMappingTest.kt`, `ItunesApiTest.kt` (also defines `loadFixture`), `ItunesSearchClientTest.kt`; fixtures `search_ok|mixed|empty|malformed.json` under `app/src/test/resources/itunes/`.

| case | result |
|---|---|
| Mapping: maps full dto; prefers 600; falls back to 100; artwork null; author/genre default ""; null on blank feedUrl; null on blank collectionName; NetworkJson ignores unknown keys (8) | NOT RUN |
| Api: default params; url-encoding; custom limit; text/javascript; unknown fields; empty results; http 500; malformed json; connection failure (9) | NOT RUN |
| Client: mapped results; trims term; blank term no call; drops unusable; dedup keep first; empty; forwards limit; http error; io failure; runs on injected io dispatcher (10) | NOT RUN |

Notes: `parses text/javascript content type` is named `parses text-javascript content type` (slash is illegal in JVM method names). The malformed-JSON test accepts SerializationException or IOException.
