package com.punyplatipus.podouvir.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    /** Newest first; episodes without a date last. */
    @Query(
        "SELECT * FROM episodes WHERE podcastId = :podcastId " +
            "ORDER BY publishedAt IS NULL, publishedAt DESC, id ASC",
    )
    fun observeForPodcast(podcastId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: Long): EpisodeEntity?

    /** Inserts episodes not yet stored (by podcastId + guid). Returns row ids, -1 for skipped duplicates. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<EpisodeEntity>): List<Long>
}
