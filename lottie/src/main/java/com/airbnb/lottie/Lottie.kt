package com.airbnb.lottie

import com.airbnb.lottie.L.setCacheProvider
import com.airbnb.lottie.L.setFetcher
import com.airbnb.lottie.L.setNetworkCacheEnabled
import com.airbnb.lottie.L.setTraceEnabled

/**
 * Class for initializing the library with custom config
 */
object Lottie {
    /**
     * Initialize Lottie with global configuration.
     *
     * @see LottieConfig.Builder
     */
    @JvmStatic
    fun initialize(lottieConfig: LottieConfig) {
        setFetcher(lottieConfig.networkFetcher)
        setCacheProvider(lottieConfig.cacheProvider)
        setTraceEnabled(lottieConfig.enableSystraceMarkers)
        setNetworkCacheEnabled(lottieConfig.enableNetworkCache)
        L.disablePathInterpolatorCache = lottieConfig.disablePathInterpolatorCache
        L.defaultAsyncUpdates = lottieConfig.defaultAsyncUpdates
    }
}
