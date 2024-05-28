package com.airbnb.lottie.network

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory.fromJsonInputStreamSync
import com.airbnb.lottie.LottieCompositionFactory.fromZipStreamSync
import com.airbnb.lottie.LottieResult
import com.airbnb.lottie.utils.Logger.debug
import com.airbnb.lottie.utils.Logger.warning
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

@RestrictTo(RestrictTo.Scope.LIBRARY)
class NetworkFetcher(
    private val networkCache: NetworkCache?,
    private val fetcher: LottieNetworkFetcher
) {
    @WorkerThread
    fun fetchSync(context: Context, url: String, cacheKey: String?): LottieResult<LottieComposition>?{
        val result = fetchFromCache(context, url, cacheKey)
        if (result != null) {
            return LottieResult(result)
        }

        debug("Animation for $url not found in cache. Fetching from network.")

        return fetchFromNetwork(context, url, cacheKey)
    }

    @WorkerThread
    private fun fetchFromCache(context: Context, url: String, cacheKey: String?): LottieComposition? {
        if (cacheKey == null || networkCache == null) {
            return null
        }
        val cacheResult = networkCache.fetch(url) ?: return null

        val extension = cacheResult.first
        val inputStream = cacheResult.second
        val result: LottieResult<LottieComposition>? = when (extension) {
            FileExtension.ZIP -> fromZipStreamSync(context, ZipInputStream(inputStream), cacheKey)
            FileExtension.GZIP -> try {
                fromJsonInputStreamSync(GZIPInputStream(inputStream), cacheKey)
            } catch (e: IOException) {
                LottieResult(e)
            }

            else -> fromJsonInputStreamSync(inputStream, cacheKey)
        }
        if (result?.value != null) {
            return result.value
        }
        return null
    }

    @WorkerThread
    private fun fetchFromNetwork(context: Context, url: String, cacheKey: String?): LottieResult<LottieComposition>? {
        debug("Fetching $url")

        var fetchResult: LottieFetchResult? = null
        try {
            fetchResult = fetcher.fetchSync(url)
            if (fetchResult.isSuccessful) {
                val inputStream = fetchResult.bodyByteStream()
                val contentType = fetchResult.contentType()
                val result = fromInputStream(context, url, inputStream, contentType, cacheKey)
                debug("Completed fetch from network. Success: " + (result?.value != null))
                return result
            } else {
                return LottieResult(IllegalArgumentException(fetchResult.error()))
            }
        } catch (e: Exception) {
            return LottieResult(e)
        } finally {
            if (fetchResult != null) {
                try {
                    fetchResult.close()
                } catch (e: IOException) {
                    warning("LottieFetchResult close failed ", e)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun fromInputStream(
        context: Context, url: String, inputStream: InputStream, contentType: String?,
        cacheKey: String?
    ): LottieResult<LottieComposition>? {
        var contentType = contentType
        val extension: FileExtension
        val result: LottieResult<LottieComposition>?
        if (contentType == null) {
            // Assume JSON for best effort parsing. If it fails, it will just deliver the parse exception
            // in the result which is more useful than failing here.
            contentType = "application/json"
        }
        if (contentType.contains("application/zip") ||
            contentType.contains("application/x-zip") ||
            contentType.contains("application/x-zip-compressed") ||
            url.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].endsWith(".lottie")
        ) {
            debug("Handling zip response.")
            extension = FileExtension.ZIP
            result = fromZipStream(context, url, inputStream, cacheKey)
        } else if (contentType.contains("application/gzip") ||
            contentType.contains("application/x-gzip") ||
            url.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].endsWith(".tgs")
        ) {
            debug("Handling gzip response.")
            extension = FileExtension.GZIP
            result = fromGzipStream(url, inputStream, cacheKey)
        } else {
            debug("Received json response.")
            extension = FileExtension.JSON
            result = fromJsonStream(url, inputStream, cacheKey)
        }

        if (cacheKey != null && result?.value != null && networkCache != null) {
            networkCache.renameTempFile(url, extension)
        }

        return result
    }

    @Throws(IOException::class)
    private fun fromZipStream(
        context: Context,
        url: String,
        inputStream: InputStream,
        cacheKey: String?
    ): LottieResult<LottieComposition>? {
        if (cacheKey == null || networkCache == null) {
            return fromZipStreamSync(context, ZipInputStream(inputStream), null)
        }
        val file = networkCache.writeTempCacheFile(url, inputStream, FileExtension.ZIP)
        return fromZipStreamSync(context, ZipInputStream(FileInputStream(file)), url)
    }

    @Throws(IOException::class)
    private fun fromGzipStream(url: String, inputStream: InputStream, cacheKey: String?): LottieResult<LottieComposition>? {
        if (cacheKey == null || networkCache == null) {
            return fromJsonInputStreamSync(GZIPInputStream(inputStream), null)
        }
        val file = networkCache.writeTempCacheFile(url, inputStream, FileExtension.GZIP)
        return fromJsonInputStreamSync(GZIPInputStream(FileInputStream(file)), url)
    }

    @Throws(IOException::class)
    private fun fromJsonStream(url: String, inputStream: InputStream, cacheKey: String?): LottieResult<LottieComposition>? {
        if (cacheKey == null || networkCache == null) {
            return fromJsonInputStreamSync(inputStream, null)
        }
        val file = networkCache.writeTempCacheFile(url, inputStream, FileExtension.JSON)
        return fromJsonInputStreamSync(FileInputStream(file.absolutePath), url)
    }
}
