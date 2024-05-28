package com.airbnb.lottie.network

import androidx.annotation.WorkerThread
import java.io.IOException

/**
 * Implement this interface to handle network fetching manually when animations are requested via url. By default, Lottie will use an
 * [java.net.HttpURLConnection] under the hood but this enables you to hook into your own network stack. By default, Lottie will also handle
 * caching the
 * animations but if you want to provide your own cache directory, you may implement [LottieNetworkCacheProvider].
 *
 * @see com.airbnb.lottie.Lottie.initialize
 */
interface LottieNetworkFetcher {
    @WorkerThread
    @Throws(IOException::class)
    fun fetchSync(url: String): LottieFetchResult
}
