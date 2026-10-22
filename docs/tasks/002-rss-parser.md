# 002 RSS feed parser
Plan item: M1 — iTunes Search client + RSS parser (with fixture-based tests) — parser part
Status: done
Depends on: 001

## Goal
Parse a podcast RSS 2.0 feed (with iTunes namespace) into a `ParsedFeed`, robust to the messy feeds
found in the wild. Streaming parser (`XmlPullParser`), no extra dependency.

## Files
- `app/src/main/java/com/punyplatipus/podouvir/data/rss/RssParser.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/data/rss/ParsedFeed.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/data/rss/RssDates.kt` (new: duration + date helpers)
- Tests: `app/src/test/java/com/punyplatipus/podouvir/data/rss/` with fixtures in `app/src/test/resources/rss/`.
  `XmlPullParser` is an Android API, so parser tests run with Robolectric (`@RunWith(RobolectricTestRunner::class)`).
  The helper functions in `RssDates.kt` are pure and need no Robolectric.

## Public API
```kotlin
package com.punyplatipus.podouvir.data.rss

data class ParsedFeed(
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val genre: String,              // "" when none
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val durationMs: Long?,
    val publishedAt: Long?,         // epoch millis
)

class FeedParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RssParser @javax.inject.Inject constructor() {
    /** Does not close [input]. Throws FeedParseException for malformed XML or a document without <channel>. */
    fun parse(input: java.io.InputStream): ParsedFeed
}

/** "3723", "62:03", "1:02:03" (also with fractional seconds "62:03.5") → millis; null/blank/garbage → null. */
fun parseItunesDuration(value: String?): Long?

/** RFC 822/1123 dates as found in feeds ("Tue, 21 Oct 2026 08:00:00 GMT", "+0000" offsets,
 *  single-digit day, missing weekday, missing seconds) → epoch millis; unparseable → null. */
fun parseRfc822Date(value: String?): Long?
```

## Acceptance criteria
- Namespace-aware parsing; the iTunes namespace is `http://www.itunes.com/dtds/podcast-1.0.dtd`.
- Channel: `title`; `author` = `itunes:author`, else `""`; `description` = `description`, else `itunes:summary`, else `""`;
  `artworkUrl` = `itunes:image@href`, else `image/url`, else null; `genre` = `text` attribute of the FIRST
  top-level `itunes:category` (nested sub-categories ignored), else `""`.
- Channel-level `title`/`description` must not be overwritten by values inside `<item>` or `<image>`.
- Item: `audioUrl` = `enclosure@url` (required — items without an enclosure are skipped); `guid` = `guid` text,
  else the enclosure URL; `title` (else `""`); `description` = `description`, else `itunes:summary`, else `""`;
  `durationMs` via `parseItunesDuration(itunes:duration)`; `publishedAt` via `parseRfc822Date(pubDate)`.
- Text is trimmed; CDATA content is read as text; HTML inside descriptions is kept as-is.
- Episodes are returned in document order.
- Malformed XML → `FeedParseException` (never a raw XmlPullParserException). Non-RSS XML (no `<channel>`) → `FeedParseException`.

## Test plan

### Test files
- `app/src/test/java/com/punyplatipus/podouvir/data/rss/RssDatesTest.kt` (plain JUnit4, no Robolectric)
- `app/src/test/java/com/punyplatipus/podouvir/data/rss/RssParserTest.kt` (`@RunWith(RobolectricTestRunner::class)`; `XmlPullParser` is an Android API)

### Fixtures (`app/src/test/resources/rss/`)
Load via `javaClass.classLoader!!.getResourceAsStream("rss/<name>")`; build tiny inline feeds with `ByteArrayInputStream` for one-off cases.
- `simple_feed.xml`: well-formed feed with itunes ns; channel title/author/description/`itunes:image`/`itunes:category`; 3 items (guid, enclosure, duration, pubDate) in a known order.
- `messy_feed.xml`: CDATA + HTML description, `itunes:summary` only, missing guid, missing duration, `<image><url>` fallback artwork, nested `itunes:category`, one item without enclosure, extra `<title>` inside `<image>` and `<item>`, surrounding whitespace in values.
- `malformed.xml`: truncated/unclosed tags.
- `not_rss.xml`: valid XML (e.g. Atom `<feed>`) with no `<channel>`.
- Inline: empty channel (no items), empty input stream, a stream wrapper that records whether `close()` was called.
No fakes/mocks needed.

### `parseItunesDuration` (RssDatesTest, level `unit`)
| name | given | when | then |
|---|---|---|---|
| `plain seconds are converted to millis` | `"3723"` | parse | `3_723_000` |
| `mm_ss format is converted` | `"62:03"` | parse | `3_723_000` |
| `hh_mm_ss format is converted` | `"1:02:03"` | parse | `3_723_000` |
| `fractional seconds are kept` | `"62:03.5"` | parse | `3_723_500` |
| `surrounding whitespace is ignored` | `" 90 "` | parse | `90_000` |
| `zero duration is zero not null` | `"0"` / `"00:00:00"` | parse | `0` |
| `null blank and garbage return null` | `null`, `""`, `"  "`, `"abc"`, `"1:xx"`, `"1:2:3:4"`, `"-5"` | parse | `null` for each, no exception |

### `parseRfc822Date` (RssDatesTest, level `unit`)
| name | given | when | then |
|---|---|---|---|
| `full RFC 1123 GMT date parses` | `"Tue, 21 Oct 2026 08:00:00 GMT"` | parse | epoch millis of 2026-10-21T08:00:00Z |
| `numeric offset is applied` | `"Tue, 21 Oct 2026 10:00:00 +0200"` | parse | same instant as the GMT case above |
| `negative offset is applied` | `"Tue, 21 Oct 2026 03:00:00 -0500"` | parse | same instant as above |
| `single digit day parses` | `"Wed, 1 Oct 2026 08:00:00 GMT"` | parse | 2026-10-01T08:00:00Z |
| `missing weekday parses` | `"21 Oct 2026 08:00:00 GMT"` | parse | 2026-10-21T08:00:00Z |
| `missing seconds parses` | `"Tue, 21 Oct 2026 08:00 GMT"` | parse | 2026-10-21T08:00:00Z |
| `result is independent of default locale and timezone` | default `Locale`/`TimeZone` set to e.g. `pt_BR` / `America/Sao_Paulo` (restore in finally) | parse the GMT date | same epoch millis (guards month-name parsing) |
| `null blank and garbage return null` | `null`, `""`, `"yesterday"`, `"2026-10-21"` | parse | `null`, no exception |

### `RssParser.parse` (RssParserTest, level `unit` via Robolectric)
| name | given | when | then |
|---|---|---|---|
| `parses channel metadata from a simple feed` | `simple_feed.xml` | parse | title, author (`itunes:author`), description, artworkUrl (`itunes:image@href`), genre (first category `text`) match fixture |
| `parses all episodes in document order` | `simple_feed.xml` | parse | 3 episodes; guids/titles in document order; each has audioUrl, durationMs, publishedAt per fixture |
| `episode fields map from item elements` | first item with guid, enclosure, `itunes:duration` `"1:02:03"`, pubDate GMT | parse | guid = guid text, audioUrl = `enclosure@url`, durationMs = 3_723_000, publishedAt = expected millis |
| `missing guid falls back to enclosure url` | item without `<guid>` | parse | `guid == audioUrl` |
| `item without enclosure is skipped` | 3 items, middle one has no enclosure | parse | 2 episodes, order preserved |
| `description falls back to itunes summary` | channel and item with only `itunes:summary` | parse | channel and episode description = summary text |
| `description prefers description over itunes summary` | both present | parse | `description` element wins |
| `author title description and genre default to empty string` | channel with only `<title>`, no author/description/category | parse | author `""`, description `""`, genre `""` (episode title missing -> `""` too) |
| `artwork falls back to image url then null` | (a) no `itunes:image`, has `<image><url>`; (b) neither | parse | (a) url from `image/url`; (b) `null`. `itunes:image@href` wins when both present |
| `genre is first top-level category and ignores nested ones` | `<itunes:category text="Tech"><itunes:category text="Gadgets"/></itunes:category><itunes:category text="News"/>` | parse | genre `"Tech"` |
| `channel title and description are not overwritten by item or image values` | `<image><title>X</title></image>` and items with own title/description, after or before channel fields | parse | channel title/description remain channel-level values |
| `text is trimmed and CDATA is read as text` | values with surrounding whitespace/newlines; description in `<![CDATA[<p>Hi <b>there</b></p>]]>` | parse | trimmed; description equals `<p>Hi <b>there</b></p>` (HTML kept as-is) |
| `missing or invalid duration and pubDate give null` | item with no duration, another with `"abc"` duration and bad pubDate | parse | `durationMs == null`, `publishedAt == null`, episode still returned |
| `feed with no items returns empty episode list` | channel with metadata only | parse | `episodes` empty, metadata populated |
| `malformed xml throws FeedParseException` | `malformed.xml` | parse | `FeedParseException` thrown, not `XmlPullParserException`; cause is set |
| `empty input throws FeedParseException` | zero-byte stream | parse | `FeedParseException` |
| `xml without channel throws FeedParseException` | `not_rss.xml` | parse | `FeedParseException` |
| `input stream is not closed by parse` | stream wrapper recording `close()` | parse | `close()` never called |
| `iTunes elements are matched by namespace not prefix` | feed declaring the iTunes namespace under another prefix (e.g. `xmlns:it=...`, `<it:author>`) | parse | author read correctly; an element with prefix `itunes` bound to a different namespace is ignored |

### Level summary
- `unit` (plain JVM): 15 cases (7 duration + 8 date; table rows each count as one case)
- `unit` via Robolectric (still no device/emulator): 19 cases (RssParser)
- `room`: 0
- `ui`: 0

Robolectric is justified only because `XmlPullParser` is an Android API (per task Files section); no instrumented tests are needed.

### Out of scope
- Network fetching, HTTP errors, charset sniffing from HTTP headers (belongs to the repository/client task 005).
- Mapping `ParsedFeed` to Room entities / domain models (tasks 003 and 005).
- Atom feeds, `podcast:` namespace extensions, chapters, transcripts, multiple enclosures per item.
- Performance/very large feeds: streaming is by construction; no benchmark. Optionally add one smoke case with a generated 5,000-item feed if cheap.
- HTML sanitising of descriptions (kept as-is by requirement; handled at render time).

## Implementation notes
Files: `data/rss/ParsedFeed.kt` (ParsedFeed, ParsedEpisode, FeedParseException), `data/rss/RssParser.kt`, `data/rss/RssDates.kt`.
- Parser uses `android.util.Xml.newPullParser()` with namespace processing; iTunes elements matched by namespace URI, plain RSS elements by empty namespace.
- Only direct children of `<channel>`/`<item>` are read, so nested values (image title, nested categories) cannot overwrite channel fields. First occurrence of each field wins.
- Blank description falls back to `itunes:summary`; blank guid falls back to the enclosure URL.
- Any XmlPullParserException, IOException or RuntimeException is wrapped in FeedParseException (cause set). Empty input -> no channel -> FeedParseException.
- Dates use `SimpleDateFormat` (Locale.US, GMT) with 4 patterns (weekday/seconds optional), created per call (thread-safe) and requiring the whole string to be consumed; avoids java.time to not depend on desugaring.
- Duration: 1-3 colon parts, integer parts digits only, last part may have a fraction; negatives/garbage -> null.
- Build: not run: no SDK.

## Test results
Not run: no JDK/SDK on this machine (`java` absent); CI will run them. Reviewed by eye against the implementation: no spec violations found.

| Case | Result |
|---|---|
| RssDatesTest: plain seconds are converted to millis | NOT RUN |
| RssDatesTest: mm_ss format is converted | NOT RUN |
| RssDatesTest: hh_mm_ss format is converted | NOT RUN |
| RssDatesTest: fractional seconds are kept | NOT RUN |
| RssDatesTest: surrounding whitespace is ignored | NOT RUN |
| RssDatesTest: zero duration is zero not null | NOT RUN |
| RssDatesTest: duration null blank and garbage return null | NOT RUN |
| RssDatesTest: full RFC 1123 GMT date parses | NOT RUN |
| RssDatesTest: numeric offset is applied | NOT RUN |
| RssDatesTest: negative offset is applied | NOT RUN |
| RssDatesTest: single digit day parses | NOT RUN |
| RssDatesTest: missing weekday parses | NOT RUN |
| RssDatesTest: missing seconds parses | NOT RUN |
| RssDatesTest: result is independent of default locale and timezone | NOT RUN |
| RssDatesTest: date null blank and garbage return null | NOT RUN |
| RssParserTest: parses channel metadata from a simple feed | NOT RUN |
| RssParserTest: parses all episodes in document order | NOT RUN |
| RssParserTest: episode fields map from item elements | NOT RUN |
| RssParserTest: missing guid falls back to enclosure url | NOT RUN |
| RssParserTest: item without enclosure is skipped | NOT RUN |
| RssParserTest: description falls back to itunes summary | NOT RUN |
| RssParserTest: description prefers description over itunes summary | NOT RUN |
| RssParserTest: author title description and genre default to empty string | NOT RUN |
| RssParserTest: artwork falls back to image url then null | NOT RUN |
| RssParserTest: genre is first top-level category and ignores nested ones | NOT RUN |
| RssParserTest: channel title and description are not overwritten by item or image values | NOT RUN |
| RssParserTest: text is trimmed and CDATA is read as text | NOT RUN |
| RssParserTest: missing or invalid duration and pubDate give null | NOT RUN |
| RssParserTest: feed with no items returns empty episode list | NOT RUN |
| RssParserTest: malformed xml throws FeedParseException | NOT RUN |
| RssParserTest: empty input throws FeedParseException | NOT RUN |
| RssParserTest: xml without channel throws FeedParseException | NOT RUN |
| RssParserTest: input stream is not closed by parse | NOT RUN |
| RssParserTest: iTunes elements are matched by namespace not prefix | NOT RUN |
| RssParserTest: messy fixture is parsed as specified (added by tester) | NOT RUN |
