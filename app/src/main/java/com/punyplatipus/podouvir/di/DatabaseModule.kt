package com.punyplatipus.podouvir.di

import android.content.Context
import androidx.room.Room
import com.punyplatipus.podouvir.data.db.EpisodeDao
import com.punyplatipus.podouvir.data.db.PlaybackStateDao
import com.punyplatipus.podouvir.data.db.PodOuvirDatabase
import com.punyplatipus.podouvir.data.db.PodcastDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PodOuvirDatabase =
        Room.databaseBuilder(context, PodOuvirDatabase::class.java, "podouvir.db").build()

    @Provides
    fun providePodcastDao(db: PodOuvirDatabase): PodcastDao = db.podcastDao()

    @Provides
    fun provideEpisodeDao(db: PodOuvirDatabase): EpisodeDao = db.episodeDao()

    @Provides
    fun providePlaybackStateDao(db: PodOuvirDatabase): PlaybackStateDao = db.playbackStateDao()
}
