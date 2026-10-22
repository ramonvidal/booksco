package com.punyplatipus.podouvir.di

import com.punyplatipus.podouvir.data.network.FeedFetcher
import com.punyplatipus.podouvir.data.network.OkHttpFeedFetcher
import com.punyplatipus.podouvir.data.repository.DefaultPodcastRepository
import com.punyplatipus.podouvir.data.repository.PodcastRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPodcastRepository(impl: DefaultPodcastRepository): PodcastRepository

    @Binds
    @Singleton
    abstract fun bindFeedFetcher(impl: OkHttpFeedFetcher): FeedFetcher
}
