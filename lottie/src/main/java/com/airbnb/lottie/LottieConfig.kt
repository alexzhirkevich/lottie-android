package com.airbnb.lottie

import com.airbnb.lottie.network.LottieNetworkCacheProvider
import com.airbnb.lottie.network.LottieNetworkFetcher
import java.io.File

/**
 * Class for custom library configuration.
 *
 *
 * This should be constructed with [LottieConfig.Builder]
 */
class LottieConfig private constructor(
    val networkFetcher: LottieNetworkFetcher?, val cacheProvider: LottieNetworkCacheProvider?,
    val enableSystraceMarkers: Boolean, val enableNetworkCache: Boolean, val disablePathInterpolatorCache: Boolean,
    val defaultAsyncUpdates: AsyncUpdates
) {
    class Builder {
        private var networkFetcher: LottieNetworkFetcher? = null
        private var cacheProvider: LottieNetworkCacheProvider? = null
        private var enableSystraceMarkers = false
        private var enableNetworkCache = true
        private var disablePathInterpolatorCache = true
        private var defaultAsyncUpdates = AsyncUpdates.AUTOMATIC

        /**
         * Lottie has a default network fetching stack built on [java.net.HttpURLConnection]. However, if you would like to hook into your own
         * network stack for performance, caching, or analytics, you may replace the internal stack with your own.
         */
        fun setNetworkFetcher(fetcher: LottieNetworkFetcher): Builder {
            this.networkFetcher = fetcher
            return this
        }

        /**
         * Provide your own network cache directory. By default, animations will be saved in your application's cacheDir/lottie_network_cache.
         *
         * @see .setNetworkCacheProvider
         */
        fun setNetworkCacheDir(file: File): Builder {
            check(cacheProvider == null) { "There is already a cache provider!" }
            require(file.isDirectory) { "cache file must be a directory" }
            cacheProvider = LottieNetworkCacheProvider(file)
            return this
        }

        /**
         * Provide your own network cache provider. By default, animations will be saved in your application's cacheDir/lottie_network_cache.
         */
        fun setNetworkCacheProvider(fileCacheProvider: LottieNetworkCacheProvider): Builder {
            check(cacheProvider == null) { "There is already a cache provider!" }
            val file = fileCacheProvider.cacheDir
            require(file.isDirectory) { "cache file must be a directory" }
            cacheProvider = LottieNetworkCacheProvider(file)
            return this
        }

        /**
         * Enable this if you want to run systrace to debug the performance of animations.
         *
         *
         * DO NOT leave this enabled in production. The overhead is low but non-zero.
         *
         * @see [Systrace Docs](https://developer.android.com/topic/performance/tracing/command-line)
         */
        fun setEnableSystraceMarkers(enable: Boolean): Builder {
            enableSystraceMarkers = enable
            return this
        }

        /**
         * Disable this if you want to completely disable internal Lottie cache for retrieving network animations.
         * Internal network cache is enabled by default.
         */
        fun setEnableNetworkCache(enable: Boolean): Builder {
            enableNetworkCache = enable
            return this
        }

        /**
         * When parsing animations, Lottie has a path interpolator cache. This cache allows Lottie to reuse PathInterpolators
         * across an animation. This is desirable in most cases. However, when shared across screenshot tests, it can cause slight
         * deviations in the rendering due to underlying approximations in the PathInterpolator.
         *
         * The cache is enabled by default and should probably only be disabled for screenshot tests.
         */
        fun setDisablePathInterpolatorCache(disable: Boolean): Builder {
            disablePathInterpolatorCache = disable
            return this
        }

        /**
         * Sets the default value for async updates.
         * @see LottieDrawable.setAsyncUpdates
         */
        fun setDefaultAsyncUpdates(asyncUpdates: AsyncUpdates): Builder {
            defaultAsyncUpdates = asyncUpdates
            return this
        }

        fun build(): LottieConfig {
            return LottieConfig(
                networkFetcher, cacheProvider, enableSystraceMarkers, enableNetworkCache, disablePathInterpolatorCache,
                defaultAsyncUpdates
            )
        }
    }
}
