package com.punyplatipus.podouvir.di

import com.punyplatipus.podouvir.BuildConfig
import com.punyplatipus.podouvir.data.network.NetworkJson
import com.punyplatipus.podouvir.data.search.FyydApi
import com.punyplatipus.podouvir.data.search.PodcastIndexApi
import com.punyplatipus.podouvir.data.search.PodcastIndexAuthInterceptor
import com.punyplatipus.podouvir.data.search.PodcastIndexCredentials
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val FYYD_BASE_URL = "https://api.fyyd.de/"
private const val PODCAST_INDEX_BASE_URL = "https://api.podcastindex.org/"
private const val TIMEOUT_SECONDS = 15L

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "PodOuvir/${BuildConfig.VERSION_NAME}")
                    .build(),
            )
        }
        .build()

    @Provides
    @Singleton
    fun providePodcastIndexCredentials(): PodcastIndexCredentials =
        PodcastIndexCredentials(BuildConfig.PODCAST_INDEX_KEY, BuildConfig.PODCAST_INDEX_SECRET)

    @Provides
    @Singleton
    fun provideFyydApi(client: OkHttpClient): FyydApi = retrofit(FYYD_BASE_URL, client).create(FyydApi::class.java)

    @Provides
    @Singleton
    fun providePodcastIndexApi(
        client: OkHttpClient,
        credentials: PodcastIndexCredentials,
        clock: Clock,
    ): PodcastIndexApi {
        val piClient = client.newBuilder()
            .addInterceptor(PodcastIndexAuthInterceptor(credentials, clock))
            .build()
        return retrofit(PODCAST_INDEX_BASE_URL, piClient).create(PodcastIndexApi::class.java)
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
        .build()
}
