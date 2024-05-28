package com.airbnb.lottie

import android.content.Context
import androidx.annotation.RestrictTo
import com.airbnb.lottie.network.DefaultLottieNetworkFetcher
import com.airbnb.lottie.network.LottieNetworkCacheProvider
import com.airbnb.lottie.network.LottieNetworkFetcher
import com.airbnb.lottie.network.NetworkCache
import com.airbnb.lottie.network.NetworkFetcher
import com.airbnb.lottie.utils.LottieTrace
import java.io.File
import kotlin.concurrent.Volatile

@RestrictTo(RestrictTo.Scope.LIBRARY)
object L {
    @JvmField
    var DBG: Boolean = false
    const val TAG: String = "LOTTIE"

    private var traceEnabled = false
    private var networkCacheEnabled = true

    @JvmField
    var disablePathInterpolatorCache: Boolean = true

    @JvmField
    var defaultAsyncUpdates: AsyncUpdates = AsyncUpdates.AUTOMATIC

    private var fetcher: LottieNetworkFetcher? = null
    private var cacheProvider: LottieNetworkCacheProvider? = null

    @Volatile
    private var networkFetcher: NetworkFetcher? = null

    @Volatile
    private var networkCache: NetworkCache? = null
    private var lottieTrace: ThreadLocal<LottieTrace>? = null

    @JvmStatic
    fun setTraceEnabled(enabled: Boolean) {
        if (traceEnabled == enabled) {
            return
        }
        traceEnabled = enabled
        if (traceEnabled && lottieTrace == null) {
            lottieTrace = ThreadLocal()
        }
    }

    @JvmStatic
    fun isTraceEnabled(): Boolean {
        return traceEnabled
    }

    @JvmStatic
    fun setNetworkCacheEnabled(enabled: Boolean) {
        networkCacheEnabled = enabled
    }

    @JvmStatic
    fun beginSection(section: String?) {
        if (!traceEnabled) {
            return
        }
        trace.beginSection(section)
    }

    @JvmStatic
    fun endSection(section: String): Float {
        if (!traceEnabled) {
            return 0f
        }
        return trace.endSection(section)
    }

    private val trace: LottieTrace
        get() {
            var trace = lottieTrace!!.get()
            if (trace == null) {
                trace = LottieTrace()
                lottieTrace!!.set(trace)
            }
            return trace
        }

    @JvmStatic
    fun setFetcher(customFetcher: LottieNetworkFetcher?) {
        if ((fetcher == null && customFetcher == null) || (fetcher != null && fetcher == customFetcher)) {
            return
        }

        fetcher = customFetcher
        networkFetcher = null
    }

    @JvmStatic
    fun setCacheProvider(customProvider: LottieNetworkCacheProvider?) {
        if ((cacheProvider == null && customProvider == null) || (cacheProvider != null && cacheProvider == customProvider)) {
            return
        }

        cacheProvider = customProvider
        networkCache = null
    }

    @JvmStatic
    fun networkFetcher(context: Context): NetworkFetcher {
        var local = networkFetcher
        if (local == null) {
            synchronized(NetworkFetcher::class.java) {
                local = networkFetcher
                if (local == null) {
                    local = NetworkFetcher(networkCache(context), (if (fetcher != null) fetcher else DefaultLottieNetworkFetcher())!!)
                    networkFetcher = local
                }
            }
        }
        return local!!
    }

    @JvmStatic
    fun networkCache(context: Context): NetworkCache? {
        if (!networkCacheEnabled) {
            return null
        }
        val appContext = context.applicationContext
        var local = networkCache
        if (local == null) {
            synchronized(NetworkCache::class.java) {
                local = networkCache
                if (local == null) {
                    local = NetworkCache(
                        (if (cacheProvider != null) cacheProvider else LottieNetworkCacheProvider(
                            File(
                                appContext.cacheDir,
                                "lottie_network_cache"
                            )
                        ))!!
                    )
                    networkCache = local
                }
            }
        }
        return local
    }
}
