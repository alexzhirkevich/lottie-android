package com.airbnb.lottie.model

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.collection.LruCache
import com.airbnb.lottie.LottieComposition

@RestrictTo(RestrictTo.Scope.LIBRARY)
class LottieCompositionCache @VisibleForTesting internal constructor() {
    private val cache = LruCache<String, LottieComposition>(20)

    operator fun get(cacheKey: String?): LottieComposition? {
        if (cacheKey == null) {
            return null
        }
        return cache[cacheKey]
    }

    fun put(cacheKey: String?, composition: LottieComposition) {
        if (cacheKey == null) {
            return
        }
        cache.put(cacheKey, composition)
    }

    fun clear() {
        cache.evictAll()
    }

    /**
     * Set the maximum number of compositions to keep cached in memory.
     * This must be &gt; 0.
     */
    fun resize(size: Int) {
        cache.resize(size)
    }

    companion object {
        val instance: LottieCompositionCache = LottieCompositionCache()
    }
}
