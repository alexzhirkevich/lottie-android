package com.airbnb.lottie.network

import android.util.Pair
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import com.airbnb.lottie.utils.Logger.debug
import com.airbnb.lottie.utils.Logger.warning
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException


/**
 * Helper class to save and restore animations fetched from an URL to the app disk cache.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class NetworkCache(private val cacheProvider: LottieNetworkCacheProvider) {
    fun clear() {
        val parentDir = parentDir()
        if (parentDir.exists()) {
            val files = parentDir.listFiles()
            if (files != null && files.size > 0) {
                for (file in files) {
                    file.delete()
                }
            }
            parentDir.delete()
        }
    }

    /**
     * If the animation doesn't exist in the cache, null will be returned.
     *
     *
     * Once the animation is successfully parsed, [.renameTempFile] must be
     * called to move the file from a temporary location to its permanent cache location so it can
     * be used in the future.
     */
    @WorkerThread
    fun fetch(url: String): Pair<FileExtension, InputStream>? {
        val cachedFile: File?
        try {
            cachedFile = getCachedFile(url)
        } catch (e: FileNotFoundException) {
            return null
        }
        if (cachedFile == null) {
            return null
        }

        val inputStream: FileInputStream
        try {
            inputStream = FileInputStream(cachedFile)
        } catch (e: FileNotFoundException) {
            return null
        }
        val extension = if (cachedFile.absolutePath.endsWith(".zip")) {
            FileExtension.ZIP
        } else if (cachedFile.absolutePath.endsWith(".gz")) {
            FileExtension.GZIP
        } else {
            FileExtension.JSON
        }

        debug("Cache hit for " + url + " at " + cachedFile.absolutePath)
        return Pair(extension, inputStream as InputStream)
    }

    /**
     * Writes an InputStream from a network response to a temporary file. If the file successfully parses
     * to an composition, [.renameTempFile] should be called to move the file
     * to its final location for future cache hits.
     */
    @Throws(IOException::class)
    fun writeTempCacheFile(url: String, stream: InputStream, extension: FileExtension): File {
        val fileName = filenameForUrl(url, extension, true)
        val file = File(parentDir(), fileName)
        try {
            val output: OutputStream = FileOutputStream(file)
            try {
                val buffer = ByteArray(1024)
                var read: Int

                while ((stream.read(buffer).also { read = it }) != -1) {
                    output.write(buffer, 0, read)
                }

                output.flush()
            } finally {
                output.close()
            }
        } finally {
            stream.close()
        }
        return file
    }

    /**
     * If the file created by [.writeTempCacheFile] was successfully parsed,
     * this should be called to remove the temporary part of its name which will allow it to be a cache hit in the future.
     */
    fun renameTempFile(url: String, extension: FileExtension) {
        val fileName = filenameForUrl(url, extension, true)
        val file = File(parentDir(), fileName)
        val newFileName = file.absolutePath.replace(".temp", "")
        val newFile = File(newFileName)
        val renamed = file.renameTo(newFile)
        debug("Copying temp file to real file ($newFile)")
        if (!renamed) {
            warning("Unable to rename cache file " + file.absolutePath + " to " + newFile.absolutePath + ".")
        }
    }

    /**
     * Returns the cache file for the given url if it exists. Checks for both json and zip.
     * Returns null if neither exist.
     */
    @Throws(FileNotFoundException::class)
    private fun getCachedFile(url: String): File? {
        val jsonFile = File(parentDir(), filenameForUrl(url, FileExtension.JSON, false))
        if (jsonFile.exists()) {
            return jsonFile
        }
        val zipFile = File(parentDir(), filenameForUrl(url, FileExtension.ZIP, false))
        if (zipFile.exists()) {
            return zipFile
        }
        val gzipFile = File(parentDir(), filenameForUrl(url, FileExtension.GZIP, false))
        if (gzipFile.exists()) {
            return gzipFile
        }
        return null
    }

    private fun parentDir(): File {
        val file = cacheProvider.cacheDir
        if (file.isFile) {
            file.delete()
        }
        if (!file.exists()) {
            file.mkdirs()
        }
        return file
    }

    companion object {
        private fun filenameForUrl(url: String, extension: FileExtension, isTemp: Boolean): String {
            val prefix = "lottie_cache_"
            val suffix = (if (isTemp) extension.tempExtension() else extension.extension)
            var sanitizedUrl = url.replace("\\W+".toRegex(), "")
            // The max filename on Android is 255 chars.
            val maxUrlLength = 255 - prefix.length - suffix.length
            if (sanitizedUrl.length > maxUrlLength) {
                // If the url is too long, use md5 as the cache key instead.
                // md5 is preferable to substring because it is impossible to know
                // which parts of the url are significant. If it is the end chars
                // then substring could cause multiple animations to use the same
                // cache key.
                // md5 is probably better for everything but:
                //     1. It is slower and unnecessary in most cases.
                //     2. Upon upgrading, if the cache key algorithm changes,
                //        all old cached animations will get orphaned.
                sanitizedUrl = getMD5(sanitizedUrl, maxUrlLength)
            }

            return prefix + sanitizedUrl + suffix
        }

        private fun getMD5(input: String, maxLength: Int): String {
            val md: MessageDigest
            try {
                md = MessageDigest.getInstance("MD5")
            } catch (e: NoSuchAlgorithmException) {
                // For some reason, md5 doesn't exist, return a substring.
                // This should never happen.
                return input.substring(0, maxLength)
            }
            val messageDigest = md.digest(input.toByteArray())
            val sb = StringBuilder()
            for (i in messageDigest.indices) {
                val b = messageDigest[i]
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }
    }
}
