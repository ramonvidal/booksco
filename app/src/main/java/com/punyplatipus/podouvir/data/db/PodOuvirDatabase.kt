package com.punyplatipus.podouvir.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, PlaybackStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PodOuvirDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun playbackStateDao(): PlaybackStateDao
}
