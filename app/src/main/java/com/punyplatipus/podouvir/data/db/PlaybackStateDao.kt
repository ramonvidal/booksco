package com.punyplatipus.podouvir.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE episodeId = :episodeId")
    fun observe(episodeId: Long): Flow<PlaybackStateEntity?>

    @Query("SELECT * FROM playback_state WHERE episodeId = :episodeId")
    suspend fun get(episodeId: Long): PlaybackStateEntity?

    @Upsert
    suspend fun upsert(state: PlaybackStateEntity)
}
