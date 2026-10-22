package com.punyplatipus.podouvir.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts")
    suspend fun getAll(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun getById(id: Long): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl")
    suspend fun getByFeedUrl(feedUrl: String): PodcastEntity?

    /** Returns the new row id, or -1 if a podcast with the same feedUrl exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(podcast: PodcastEntity): Long

    @Update
    suspend fun update(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE podcasts SET genreOverride = :genre WHERE id = :id")
    suspend fun setGenreOverride(id: Long, genre: String?)

    @Query("SELECT MAX(customOrder) FROM podcasts")
    suspend fun maxCustomOrder(): Int?

    @Query("UPDATE podcasts SET customOrder = :order WHERE id = :id")
    suspend fun setCustomOrder(id: Long, order: Int)

    /** customOrder = index for each id, in one transaction. */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setCustomOrder(id, index) }
    }
}
