package com.punyplatipus.podouvir.di

import com.punyplatipus.podouvir.data.network.ItunesApi
import com.punyplatipus.podouvir.data.network.createItunesApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val USER_AGENT = "PodOuvir/0.1"
private const val ITUNES_BASE_URL = "https://itunes.apple.com/"
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
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    @Provides
    @Singleton
    fun provideItunesApi(client: OkHttpClient): ItunesApi = createItunesApi(ITUNES_BASE_URL, client)
}
