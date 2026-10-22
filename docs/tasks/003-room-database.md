# 003 Room database, DAOs and mappers
Plan item: M1 — Room DB: entities, DAOs, migrations strategy (exported schemas)
Status: done
Depends on: 001

## Goal
Local source of truth for podcasts, episodes and playback state. Version 1 with exported schema
(`app/schemas/`, configured already via the `room.schemaLocation` KSP arg). Future changes add migrations.

## Files
- `app/src/main/java/com/punyplatipus/podouvir/data/db/Entities.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/data/db/PodcastDao.kt`, `EpisodeDao.kt`, `PlaybackStateDao.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/data/db/PodOuvirDatabase.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/data/db/Mappers.kt` (new)
- `app/src/main/java/com/punyplatipus/podouvir/di/DatabaseModule.kt` (new)
- Tests: `app/src/test/java/com/punyplatipus/podouvir/data/db/` — Robolectric + `Room.inMemoryDatabaseBuilder`
  with `allowMainThreadQueries()`.

## Public API
```kotlin
package com.punyplatipus.podouvir.data.db

@Entity(tableName = "podcasts", indices = [Index(value = ["feedUrl"], unique = true)])
data class PodcastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedUrl: String, val title: String, val author: String, val description: String,
    val artworkUrl: String?, val genre: String, val genreOverride: String?,
    val addedAt: Long, val lastUpdated: Long, val customOrder: Int,
)

@Entity(
    tableName = "episodes",
    foreignKeys = [ForeignKey(entity = PodcastEntity::class, parentColumns = ["id"], childColumns = ["podcastId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["podcastId", "guid"], unique = true)],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val podcastId: Long, val guid: String, val title: String, val description: String,
    val audioUrl: String, val durationMs: Long?, val publishedAt: Long?,
)

@Entity(
    tableName = "playback_state",
    foreignKeys = [ForeignKey(entity = EpisodeEntity::class, parentColumns = ["id"], childColumns = ["episodeId"], onDelete = ForeignKey.CASCADE)],
)
data class PlaybackStateEntity(
    @PrimaryKey val episodeId: Long, val positionMs: Long, val completed: Boolean, val updatedAt: Long,
)

@Dao interface PodcastDao {
    @Query("SELECT * FROM podcasts") fun observeAll(): Flow<List<PodcastEntity>>
    @Query("SELECT * FROM podcasts") suspend fun getAll(): List<PodcastEntity>
    @Query("SELECT * FROM podcasts WHERE id = :id") suspend fun getById(id: Long): PodcastEntity?
    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl") suspend fun getByFeedUrl(feedUrl: String): PodcastEntity?
    /** Returns the new row id, or -1 if a podcast with the same feedUrl exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(podcast: PodcastEntity): Long
    @Update suspend fun update(podcast: PodcastEntity)
    @Query("DELETE FROM podcasts WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("UPDATE podcasts SET genreOverride = :genre WHERE id = :id") suspend fun setGenreOverride(id: Long, genre: String?)
    @Query("SELECT MAX(customOrder) FROM podcasts") suspend fun maxCustomOrder(): Int?
}

@Dao interface EpisodeDao {
    /** Newest first; episodes without a date last. */
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt IS NULL, publishedAt DESC, id ASC")
    fun observeForPodcast(podcastId: Long): Flow<List<EpisodeEntity>>
    @Query("SELECT * FROM episodes WHERE id = :id") suspend fun getById(id: Long): EpisodeEntity?
    /** Inserts episodes not yet stored (by podcastId + guid). Returns row ids, -1 for skipped duplicates. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(episodes: List<EpisodeEntity>): List<Long>
}

@Dao interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE episodeId = :episodeId") fun observe(episodeId: Long): Flow<PlaybackStateEntity?>
    @Query("SELECT * FROM playback_state WHERE episodeId = :episodeId") suspend fun get(episodeId: Long): PlaybackStateEntity?
    @Upsert suspend fun upsert(state: PlaybackStateEntity)
}

@Database(entities = [PodcastEntity::class, EpisodeEntity::class, PlaybackStateEntity::class], version = 1, exportSchema = true)
abstract class PodOuvirDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun playbackStateDao(): PlaybackStateDao
}

// Mappers.kt — lossless both ways
fun PodcastEntity.toDomain(): Podcast;            fun Podcast.toEntity(): PodcastEntity
fun EpisodeEntity.toDomain(): Episode;            fun Episode.toEntity(): EpisodeEntity
fun PlaybackStateEntity.toDomain(): PlaybackState; fun PlaybackState.toEntity(): PlaybackStateEntity
```
`DatabaseModule` (Hilt, SingletonComponent): `@Singleton` database named `"podouvir.db"` (no destructive
migration fallback) and providers for the three DAOs.

## Acceptance criteria
- Inserting a podcast with an existing `feedUrl` returns -1 and leaves the existing row unchanged.
- Inserting an episode with an existing (`podcastId`, `guid`) is skipped (-1); same guid under another podcast is allowed.
- Deleting a podcast cascades to its episodes and their playback state.
- `observeForPodcast` emits newest-first, null dates last, and re-emits after `insertAll`.
- `observeAll` re-emits after insert, update, delete and `setGenreOverride` (including clearing it to null).
- `PlaybackStateDao.upsert` inserts then updates the same `episodeId`.
- Mappers round-trip every field (entity → domain → entity is equal).
- Schema JSON for version 1 is generated in `app/schemas/` by the build (commit it).

## Test plan
All DB tests use Robolectric (`@RunWith(RobolectricTestRunner::class)`) with `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`,
`runTest`, and Turbine for Flows. Level `room` = Robolectric + in-memory Room on the JVM (no device); mapper tests are `unit`.
Each test builds a fresh DB in `@Before` and closes it in `@After`. Foreign keys are enforced by Room by default.

### Test files
- `app/src/test/java/com/punyplatipus/podouvir/data/db/PodcastDaoTest.kt`
- `app/src/test/java/com/punyplatipus/podouvir/data/db/EpisodeDaoTest.kt`
- `app/src/test/java/com/punyplatipus/podouvir/data/db/PlaybackStateDaoTest.kt`
- `app/src/test/java/com/punyplatipus/podouvir/data/db/CascadeDeleteTest.kt`
- `app/src/test/java/com/punyplatipus/podouvir/data/db/MappersTest.kt`

### Fixtures/fakes
- `app/src/test/java/com/punyplatipus/podouvir/data/db/DbTestFixtures.kt`: builders `podcastEntity(feedUrl = ..., title = ..., customOrder = ...)`,
  `episodeEntity(podcastId, guid, publishedAt = ...)`, `playbackState(episodeId, ...)` with sensible defaults; helper `createDb()`.
- No resource files, no fakes (real in-memory DB).
- Schema file `app/schemas/com.punyplatipus.podouvir.data.db.PodOuvirDatabase/1.json` is checked by existence only (see last table).

### PodcastDao (`PodcastDaoTest`) — level: room
| name | given | when | then |
|---|---|---|---|
| `insert returns generated id for new podcast` | empty DB | insert podcast | id > 0; `getById(id)` equals entity with that id |
| `insert with existing feedUrl returns -1 and keeps existing row` | podcast A stored | insert different podcast with same feedUrl (other title) | returns -1; `getByFeedUrl` still returns original A; `getAll().size == 1` |
| `getById and getByFeedUrl return null when missing` | empty DB | query unknown id / url | both null |
| `getAll returns every podcast` | 3 podcasts | getAll | size 3; empty DB gives empty list |
| `update persists changed fields` | stored podcast | update title/lastUpdated/customOrder | getById reflects all changes |
| `deleteById removes only that podcast` | 2 podcasts | deleteById(first) | getById(first) null; second remains; deleting an unknown id is a no-op |
| `setGenreOverride sets then clears override` | podcast with null override | set "Comedy", then set null | getById.genreOverride is "Comedy" then null; `genre` untouched |
| `maxCustomOrder is null when empty and max otherwise` | empty, then podcasts with orders 0, 5, 2 | maxCustomOrder | null, then 5 |
| `observeAll emits initial then re-emits after insert, update, delete and setGenreOverride` | Turbine on observeAll | insert; update; setGenreOverride("X"); setGenreOverride(null); deleteById | emissions: empty, [p], updated, override "X", override null, empty (awaitItem each, in order) |
| `observeAll emits empty list for empty DB` | empty DB | collect first | empty list |

### EpisodeDao (`EpisodeDaoTest`) — level: room
| name | given | when | then |
|---|---|---|---|
| `insertAll returns row ids for new episodes` | podcast stored | insertAll of 3 | 3 ids, all > 0; `getById` returns each |
| `insertAll skips duplicate guid in same podcast with -1` | episode (p1, "g1") stored | insertAll [(p1,"g1") again, (p1,"g2")] | result [-1, id>0]; original "g1" row unchanged (title not overwritten) |
| `insertAll allows same guid under another podcast` | episodes (p1,"g1") stored, p2 exists | insertAll [(p2,"g1")] | id > 0; both rows exist |
| `insertAll with empty list returns empty list` | any | insertAll(emptyList()) | empty result, no error |
| `insertAll with duplicate guid inside the same batch` | podcast stored | insertAll [(p1,"g"), (p1,"g")] | [id>0, -1]; one row stored |
| `insertAll fails for unknown podcastId` | no podcast | insertAll episode with podcastId 999 | throws SQLiteConstraintException (foreign key) |
| `getById returns null when missing` | empty | getById(42) | null |
| `observeForPodcast orders newest first, null dates last, ties by id` | episodes with dates 100, 300, 200, null, null, and two with equal date | observeForPodcast | order 300, 200, 100... then nulls; equal dates and nulls ordered by id ascending |
| `observeForPodcast only returns episodes of that podcast` | episodes under p1 and p2 | observe p1 | only p1 episodes; unknown podcast gives empty list |
| `observeForPodcast re-emits after insertAll` | Turbine on observeForPodcast(p1), initially empty | insertAll 2 episodes; insertAll with 1 new + 1 duplicate | emissions: empty, 2 items in order, 3 items in order (no spurious extra emission required beyond Room's invalidation) |
| `episode with very long title and description round-trips` | 100k-char strings | insertAll then getById | strings equal |
| `nullable durationMs and publishedAt are stored as null` | episode with both null | insertAll, getById | both null |

### PlaybackStateDao (`PlaybackStateDaoTest`) — level: room
| name | given | when | then |
|---|---|---|---|
| `get and observe return null when no state` | episode stored, no state | get; first emission of observe | both null |
| `upsert inserts a new state` | episode stored | upsert state | `get` equals state |
| `upsert twice updates same episodeId` | state stored | upsert with new positionMs/completed/updatedAt | `get` equals new state; table has a single row for that episode |
| `observe emits null, then inserted, then updated state` | Turbine on observe(e1) | upsert, upsert again | null, state1, state2 |
| `observe does not emit for other episodes' changes` | two episodes | upsert state for e2 while observing e1 | no new item for e1 (`expectNoEvents` or only a de-duplicated emission) |
| `upsert fails for unknown episodeId` | no such episode | upsert | throws SQLiteConstraintException |
| `boundary values persist` | positionMs = 0 and Long.MAX_VALUE, completed true | upsert/get | values equal |

### Cascade (`CascadeDeleteTest`) — level: room
| name | given | when | then |
|---|---|---|---|
| `deleting a podcast cascades to episodes and playback state` | podcast with 2 episodes, each with playback state | podcastDao.deleteById | episodes `getById` null; playback `get` null for both |
| `deleting a podcast leaves other podcasts' data intact` | two podcasts with episodes and states | delete first | second podcast's episodes and states still present |

### Mappers (`MappersTest`) — level: unit (no DB; still under Robolectric runner not required)
| name | given | when | then |
|---|---|---|---|
| `podcast entity to domain to entity is equal` | entity with all fields set | toDomain().toEntity() | equals original |
| `podcast with null artworkUrl and genreOverride round-trips` | those fields null | round trip | equals; domain fields null |
| `podcast domain to entity to domain is equal` | domain Podcast incl. id, customOrder, addedAt, lastUpdated | toEntity().toDomain() | equals original |
| `episode round-trips with and without durationMs and publishedAt` | one fully set, one with nulls | both directions | equal |
| `playback state round-trips with completed true and false` | both | both directions | equal |
| `mapper preserves ids including default 0` | id = 0 | toEntity() | id 0 (so autoGenerate applies) |

### Schema and DI — level: unit / build
| name | given | when | then |
|---|---|---|---|
| `schema v1 json is exported` | after `testDebugUnitTest` build | read `app/schemas/com.punyplatipus.podouvir.data.db.PodOuvirDatabase/1.json` | file exists, `"version": 1`, contains tables `podcasts`, `episodes`, `playback_state` and the unique indices (checked by android-build-verify / tester, not a Kotlin test) |
| `database declares version 1 with exportSchema` | n/a | review in code review | no destructive-migration fallback in `DatabaseModule` (reviewer check, not a test) |

### Out of scope
- `DatabaseModule` Hilt wiring (needs a Hilt test harness; verified by the app build and later integration tests).
- Migration tests: only version 1 exists; add `MigrationTestHelper` tests with the first schema change.
- Query performance and very large tables.
- Domain-level behaviour (`effectiveGenre`, sorting) is covered in task 001.

## Implementation notes
- Added in `data/db/`: `Entities.kt`, `PodcastDao.kt`, `EpisodeDao.kt`, `PlaybackStateDao.kt`, `PodOuvirDatabase.kt`, `Mappers.kt`; and `di/DatabaseModule.kt`. Signatures match the task exactly.
- Mappers are field-by-field and use the domain models from task 001 (`Podcast`, `Episode`, `PlaybackState` with named args).
- `DatabaseModule` has no `fallbackToDestructiveMigration`.
- Not built (no JDK/SDK), so `app/schemas/.../1.json` was NOT generated; run `./gradlew assembleDebug` and commit the schema.
- Note: `@Insert(IGNORE)` with `List` returns `List<Long>` with -1 for ignored rows (Room behaviour). Unknown FK on insert with IGNORE: FK violations are not ignored by SQLite's OR IGNORE, so they throw as the test plan expects.

## Test results
No JDK/Android SDK on this machine (`java: command not found`): not run: no SDK. Tests reviewed by eye against the implementation; CI to run them.

| Class | Case | Result |
|---|---|---|
| PodcastDaoTest | insert returns generated id for new podcast | NOT RUN |
| PodcastDaoTest | insert with existing feedUrl returns -1 and keeps existing row | NOT RUN |
| PodcastDaoTest | getById and getByFeedUrl return null when missing | NOT RUN |
| PodcastDaoTest | getAll returns every podcast | NOT RUN |
| PodcastDaoTest | update persists changed fields | NOT RUN |
| PodcastDaoTest | deleteById removes only that podcast | NOT RUN |
| PodcastDaoTest | setGenreOverride sets then clears override | NOT RUN |
| PodcastDaoTest | maxCustomOrder is null when empty and max otherwise | NOT RUN |
| PodcastDaoTest | observeAll emits initial then re-emits after insert, update, delete and setGenreOverride | NOT RUN |
| PodcastDaoTest | observeAll emits empty list for empty DB | NOT RUN |
| EpisodeDaoTest | insertAll returns row ids for new episodes | NOT RUN |
| EpisodeDaoTest | insertAll skips duplicate guid in same podcast with -1 | NOT RUN |
| EpisodeDaoTest | insertAll allows same guid under another podcast | NOT RUN |
| EpisodeDaoTest | insertAll with empty list returns empty list | NOT RUN |
| EpisodeDaoTest | insertAll with duplicate guid inside the same batch | NOT RUN |
| EpisodeDaoTest | insertAll fails for unknown podcastId | NOT RUN |
| EpisodeDaoTest | getById returns null when missing | NOT RUN |
| EpisodeDaoTest | observeForPodcast orders newest first, null dates last, ties by id | NOT RUN |
| EpisodeDaoTest | observeForPodcast only returns episodes of that podcast | NOT RUN |
| EpisodeDaoTest | observeForPodcast re-emits after insertAll | NOT RUN |
| EpisodeDaoTest | episode with very long title and description round-trips | NOT RUN |
| EpisodeDaoTest | nullable durationMs and publishedAt are stored as null | NOT RUN |
| PlaybackStateDaoTest | get and observe return null when no state | NOT RUN |
| PlaybackStateDaoTest | upsert inserts a new state | NOT RUN |
| PlaybackStateDaoTest | upsert twice updates same episodeId | NOT RUN |
| PlaybackStateDaoTest | observe emits null, then inserted, then updated state | NOT RUN |
| PlaybackStateDaoTest | observe does not emit for other episodes' changes | NOT RUN |
| PlaybackStateDaoTest | upsert fails for unknown episodeId | NOT RUN |
| PlaybackStateDaoTest | boundary values persist | NOT RUN |
| CascadeDeleteTest | deleting a podcast cascades to episodes and playback state | NOT RUN |
| CascadeDeleteTest | deleting a podcast leaves other podcasts' data intact | NOT RUN |
| MappersTest | podcast entity to domain to entity is equal | NOT RUN |
| MappersTest | podcast with null artworkUrl and genreOverride round-trips | NOT RUN |
| MappersTest | podcast domain to entity to domain is equal | NOT RUN |
| MappersTest | episode round-trips with and without durationMs and publishedAt | NOT RUN |
| MappersTest | playback state round-trips with completed true and false | NOT RUN |
| MappersTest | mapper preserves ids including default 0 | NOT RUN |
| (build) | schema v1 json is exported | NOT RUN (1.json not generated; needs build) |
| (review) | DatabaseModule has no destructive-migration fallback | PASS (review: `Room.databaseBuilder(..., "podouvir.db").build()`, @Singleton, 3 DAO providers) |

Notes: no implementation violations found by review (no EXPECTED FAIL). `observe does not emit for other episodes' changes` applies `distinctUntilChanged()` because Room invalidates per table. Fixtures in `DbTestFixtures.kt` (`createDb`, `podcastEntity`, `episodeEntity`, `playbackState`). Cascade tests rely on Room enabling foreign keys by default.
