package com.punyplatipus.podouvir.data.network

import com.punyplatipus.podouvir.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

interface FeedFetcher {
    /** Downloads [url] and passes the body stream to [block]; closes it afterwards.
     *  Throws java.io.IOException on network failure or non-2xx status. */
    suspend fun <T> fetch(url: String, block: (InputStream) -> T): T
}

class OkHttpFeedFetcher @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FeedFetcher {
    override suspend fun <T> fetch(url: String, block: (InputStream) -> T): T = withContext(ioDispatcher) {
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid URL: $url", e)
        }
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IOException("Empty response body for $url")
            body.byteStream().use(block)
        }
    }
}
