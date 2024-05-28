package com.airbnb.lottie.network

import androidx.annotation.RestrictTo

/**
 * Helpers for known Lottie file types.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
enum class FileExtension(@JvmField val extension: String) {
    JSON(".json"),
    ZIP(".zip"),
    GZIP(".gz");

    fun tempExtension(): String {
        return ".temp$extension"
    }

    override fun toString(): String {
        return extension
    }
}
