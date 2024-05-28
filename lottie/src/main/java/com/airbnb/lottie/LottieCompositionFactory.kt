package com.airbnb.lottie

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.util.Base64
import androidx.annotation.RawRes
import androidx.annotation.WorkerThread
import com.airbnb.lottie.L.networkCache
import com.airbnb.lottie.L.networkFetcher
import com.airbnb.lottie.model.LottieCompositionCache
import com.airbnb.lottie.parser.LottieCompositionMoshiParser
import com.airbnb.lottie.parser.moshi.JsonReader
import com.airbnb.lottie.utils.Logger
import com.airbnb.lottie.utils.Utils
import okio.BufferedSource
import okio.Okio
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/**
 * Helpers to create or cache a LottieComposition.
 *
 *
 * All factory methods take a cache key. The animation will be stored in an LRU cache for future use.
 * In-progress tasks will also be held so they can be returned for subsequent requests for the same
 * animation prior to the cache being populated.
 */
@Suppress("unused")
object LottieCompositionFactory {
    /**
     * Keep a map of cache keys to in-progress tasks and return them for new requests.
     * Without this, simultaneous requests to parse a composition will trigger multiple parallel
     * parse tasks prior to the cache getting populated.
     */
    private val taskCache: MutableMap<String, LottieTask<LottieComposition>> = HashMap()
    private val taskIdleListeners: MutableSet<LottieTaskIdleListener> = HashSet()

    /**
     * reference magic bytes for zip compressed files.
     * useful to determine if an InputStream is a zip file or not
     */
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
    private val GZIP_MAGIC = byteArrayOf(0x1f, 0x8b.toByte(), 0x08)


    /**
     * Set the maximum number of compositions to keep cached in memory.
     * This must be &gt; 0.
     */
    @JvmStatic
    fun setMaxCacheSize(size: Int) {
        LottieCompositionCache.instance.resize(size)
    }

    fun clearCache(context: Context?) {
        taskCache.clear()
        LottieCompositionCache.instance.clear()
        val networkCache = networkCache(context!!)
        networkCache?.clear()
    }

    /**
     * Use this to register a callback for when the composition factory is idle or not.
     * This can be used to provide data to an espresso idling resource.
     * Refer to FragmentVisibilityTests and its LottieIdlingResource in the Lottie repo for
     * an example.
     */
    fun registerLottieTaskIdleListener(listener: LottieTaskIdleListener) {
        taskIdleListeners.add(listener)
        listener.onIdleChanged(taskCache.size == 0)
    }

    fun unregisterLottieTaskIdleListener(listener: LottieTaskIdleListener) {
        taskIdleListeners.remove(listener)
    }

    /**
     * Fetch an animation from an http url. Once it is downloaded once, Lottie will cache the file to disk for
     * future use. Because of this, you may call `fromUrl` ahead of time to warm the cache if you think you
     * might need an animation in the future.
     */
    /**
     * Fetch an animation from an http url. Once it is downloaded once, Lottie will cache the file to disk for
     * future use. Because of this, you may call `fromUrl` ahead of time to warm the cache if you think you
     * might need an animation in the future.
     *
     *
     * To skip the cache, add null as a third parameter.
     */
    @JvmStatic
    @JvmOverloads
    fun fromUrl(context: Context?, url: String, cacheKey: String? = "url_$url"): LottieTask<LottieComposition> {
        return cache(cacheKey, {
            val result = networkFetcher(context!!).fetchSync(context, url, cacheKey)
            if (cacheKey != null && result?.value != null) {
                LottieCompositionCache.instance.put(cacheKey, result.value)
            }
            result
        }, null)
    }

    /**
     * Fetch an animation from an http url. Once it is downloaded once, Lottie will cache the file to disk for
     * future use. Because of this, you may call `fromUrl` ahead of time to warm the cache if you think you
     * might need an animation in the future.
     */
    @JvmStatic
    @WorkerThread
    fun fromUrlSync(context: Context?, url: String?): LottieResult<LottieComposition>? {
        return fromUrlSync(context, url, url)
    }


    /**
     * Fetch an animation from an http url. Once it is downloaded once, Lottie will cache the file to disk for
     * future use. Because of this, you may call `fromUrl` ahead of time to warm the cache if you think you
     * might need an animation in the future.
     */
    @WorkerThread
    fun fromUrlSync(context: Context?, url: String?, cacheKey: String?): LottieResult<LottieComposition>? {
        val cachedComposition = if (cacheKey == null) null else LottieCompositionCache.instance[cacheKey]
        if (cachedComposition != null) {
            return LottieResult(cachedComposition)
        }
        val result = networkFetcher(context!!).fetchSync(context, url!!, cacheKey)
        if (cacheKey != null && result?.value != null) {
            LottieCompositionCache.instance.put(cacheKey, result.value)
        }
        return result
    }

    /**
     * Parse an animation from src/main/assets. It is recommended to use [.fromRawRes] instead.
     * The asset file name will be used as a cache key so future usages won't have to parse the json again.
     * However, if your animation has images, you may package the json and images as a single flattened zip file in assets.
     *
     *
     * To skip the cache, add null as a third parameter.
     *
     * @see .fromZipStream
     */
    @JvmStatic
    fun fromAsset(context: Context, fileName: String): LottieTask<LottieComposition> {
        val cacheKey = "asset_$fileName"
        return fromAsset(context, fileName, cacheKey)
    }

    /**
     * Parse an animation from src/main/assets. It is recommended to use [.fromRawRes] instead.
     * The asset file name will be used as a cache key so future usages won't have to parse the json again.
     * However, if your animation has images, you may package the json and images as a single flattened zip file in assets.
     *
     *
     * Pass null as the cache key to skip the cache.
     *
     * @see .fromZipStream
     */
    @JvmStatic
    fun fromAsset(context: Context, fileName: String, cacheKey: String?): LottieTask<LottieComposition> {
        // Prevent accidentally leaking an Activity.
        val appContext = context.applicationContext
        return cache(cacheKey, { fromAssetSync(appContext, fileName, cacheKey) }, null)
    }

    /**
     * Parse an animation from src/main/assets. It is recommended to use [.fromRawRes] instead.
     * The asset file name will be used as a cache key so future usages won't have to parse the json again.
     * However, if your animation has images, you may package the json and images as a single flattened zip file in assets.
     *
     *
     * To skip the cache, add null as a third parameter.
     *
     * @see .fromZipStreamSync
     */
    @JvmStatic
    @WorkerThread
    fun fromAssetSync(context: Context, fileName: String): LottieResult<LottieComposition>? {
        val cacheKey = "asset_$fileName"
        return fromAssetSync(context, fileName, cacheKey)
    }

    /**
     * Parse an animation from src/main/assets. It is recommended to use [.fromRawRes] instead.
     * The asset file name will be used as a cache key so future usages won't have to parse the json again.
     * However, if your animation has images, you may package the json and images as a single flattened zip file in assets.
     *
     *
     * Pass null as the cache key to skip the cache.
     *
     * @see .fromZipStreamSync
     */
    @JvmStatic
    @WorkerThread
    fun fromAssetSync(context: Context, fileName: String, cacheKey: String?): LottieResult<LottieComposition>? {
        val cachedComposition = if (cacheKey == null) null else LottieCompositionCache.instance[cacheKey]
        if (cachedComposition != null) {
            return LottieResult(cachedComposition)
        }
        try {
            val source = Okio.buffer(Okio.source(context.assets.open(fileName)))
            if (isZipCompressed(source)) {
                return fromZipStreamSync(context, ZipInputStream(source.inputStream()), cacheKey)
            } else if (isGzipCompressed(source)) {
                return fromJsonInputStreamSync(GZIPInputStream(source.inputStream()), cacheKey)
            }
            return fromJsonInputStreamSync(source.inputStream(), cacheKey)
        } catch (e: IOException) {
            return LottieResult(e)
        }
    }


    /**
     * Parse an animation from raw/res. This is recommended over putting your animation in assets because
     * it uses a hard reference to R.
     * The resource id will be used as a cache key so future usages won't parse the json again.
     * Note: to correctly load dark mode (-night) resources, make sure you pass Activity as a context (instead of e.g. the application context).
     * The Activity won't be leaked.
     *
     *
     * Pass null as the cache key to skip caching.
     */
    /**
     * Parse an animation from raw/res. This is recommended over putting your animation in assets because
     * it uses a hard reference to R.
     * The resource id will be used as a cache key so future usages won't parse the json again.
     * Note: to correctly load dark mode (-night) resources, make sure you pass Activity as a context (instead of e.g. the application context).
     * The Activity won't be leaked.
     *
     *
     * To skip the cache, add null as a third parameter.
     */
    @JvmStatic
    @JvmOverloads
    fun fromRawRes(
        context: Context,
        @RawRes rawRes: Int,
        cacheKey: String? = rawResCacheKey(context, rawRes)): LottieTask<LottieComposition> {
        // Prevent accidentally leaking an Activity.
        val contextRef = WeakReference(context)
        val appContext = context.applicationContext
        return cache(cacheKey, {
            val originalContext = contextRef.get()
            val context1 = originalContext ?: appContext
            fromRawResSync(context1, rawRes, cacheKey)
        }, null)
    }

    /**
     * Parse an animation from raw/res. This is recommended over putting your animation in assets because
     * it uses a hard reference to R.
     * The resource id will be used as a cache key so future usages won't parse the json again.
     * Note: to correctly load dark mode (-night) resources, make sure you pass Activity as a context (instead of e.g. the application context).
     * The Activity won't be leaked.
     *
     *
     * To skip the cache, add null as a third parameter.
     */
    @JvmStatic
    @WorkerThread
    fun fromRawResSync(context: Context, @RawRes rawRes: Int): LottieResult<LottieComposition>? {
        return fromRawResSync(context, rawRes, rawResCacheKey(context, rawRes))
    }

    /**
     * Parse an animation from raw/res. This is recommended over putting your animation in assets because
     * it uses a hard reference to R.
     * The resource id will be used as a cache key so future usages won't parse the json again.
     * Note: to correctly load dark mode (-night) resources, make sure you pass Activity as a context (instead of e.g. the application context).
     * The Activity won't be leaked.
     *
     *
     * Pass null as the cache key to skip caching.
     */
    @JvmStatic
    @WorkerThread
    fun fromRawResSync(context: Context, @RawRes rawRes: Int, cacheKey: String?): LottieResult<LottieComposition>? {
        val cachedComposition = if (cacheKey == null) null else LottieCompositionCache.instance[cacheKey]
        if (cachedComposition != null) {
            return LottieResult(cachedComposition)
        }
        try {
            val source = Okio.buffer(Okio.source(context.resources.openRawResource(rawRes)))
            if (isZipCompressed(source)) {
                return fromZipStreamSync(context, ZipInputStream(source.inputStream()), cacheKey)
            } else if (isGzipCompressed(source)) {
                return try {
                    fromJsonInputStreamSync(GZIPInputStream(source.inputStream()), cacheKey)
                } catch (e: IOException) {
                    // This shouldn't happen because we check the header for magic bytes.
                    LottieResult(e)
                }
            }
            return fromJsonInputStreamSync(source.inputStream(), cacheKey)
        } catch (e: Resources.NotFoundException) {
            return LottieResult(e)
        }
    }

    private fun rawResCacheKey(context: Context, @RawRes resId: Int): String {
        return "rawRes" + (if (isNightMode(context)) "_night_" else "_day_") + resId
    }

    /**
     * It is important to include day/night in the cache key so that if it changes, the cache won't return an animation from the wrong bucket.
     */
    private fun isNightMode(context: Context): Boolean {
        val nightModeMasked = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeMasked == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Auto-closes the stream.
     *
     * @see .fromJsonInputStreamSync
     */
    @JvmStatic
    fun fromJsonInputStream(stream: InputStream, cacheKey: String?): LottieTask<LottieComposition> {
        return cache(cacheKey, { fromJsonInputStreamSync(stream, cacheKey) }, { Utils.closeQuietly(stream) })
    }

    /**
     * @see .fromJsonInputStreamSync
     */
    fun fromJsonInputStream(stream: InputStream, cacheKey: String?, close: Boolean): LottieTask<LottieComposition>? {
        return cache(cacheKey, { fromJsonInputStreamSync(stream, cacheKey, close) }, {
            if (close) {
                Utils.closeQuietly(stream)
            }
        })
    }

    /**
     * Return a LottieComposition for the given InputStream to json.
     */
    @JvmStatic
    @WorkerThread
    fun fromJsonInputStreamSync(stream: InputStream, cacheKey: String?): LottieResult<LottieComposition>? {
        return fromJsonInputStreamSync(stream, cacheKey, true)
    }

    /**
     * Return a LottieComposition for the given InputStream to json.
     */
    @WorkerThread
    fun fromJsonInputStreamSync(stream: InputStream?, cacheKey: String?, close: Boolean): LottieResult<LottieComposition>? {
        return fromJsonReaderSync(JsonReader.of(Okio.buffer(Okio.source(stream))), cacheKey, close)
    }

    /**
     * @see .fromJsonSync
     */
    @Deprecated("")
    fun fromJson(json: JSONObject, cacheKey: String?): LottieTask<LottieComposition>? {
        return cache(cacheKey, { fromJsonSync(json, cacheKey) }, null)
    }

    /**
     * Prefer passing in the json string directly. This method just calls `toString()` on your JSONObject.
     * If you are loading this animation from the network, just use the response body string instead of
     * parsing it first for improved performance.
     */
    @Deprecated("")
    @WorkerThread
    fun fromJsonSync(json: JSONObject, cacheKey: String?): LottieResult<LottieComposition>? {
        return fromJsonStringSync(json.toString(), cacheKey)
    }

    /**
     * @see .fromJsonStringSync
     */
    fun fromJsonString(json: String, cacheKey: String?): LottieTask<LottieComposition> {
        return cache(cacheKey, { fromJsonStringSync(json, cacheKey) }, null)
    }

    /**
     * Return a LottieComposition for the specified raw json string.
     * If loading from a file, it is preferable to use the InputStream or rawRes version.
     */
    @JvmStatic
    @WorkerThread
    fun fromJsonStringSync(json: String, cacheKey: String?): LottieResult<LottieComposition>? {
        val stream = ByteArrayInputStream(json.toByteArray())
        return fromJsonReaderSync(JsonReader.of(Okio.buffer(Okio.source(stream))), cacheKey)
    }

    @JvmStatic
    fun fromJsonReader(reader: JsonReader, cacheKey: String?): LottieTask<LottieComposition> {
        return cache(cacheKey, { fromJsonReaderSync(reader, cacheKey) }, { Utils.closeQuietly(reader) })
    }

    @JvmStatic
    @WorkerThread
    fun fromJsonReaderSync(reader: JsonReader, cacheKey: String?): LottieResult<LottieComposition>? {
        return fromJsonReaderSync(reader, cacheKey, true)
    }

    @WorkerThread
    fun fromJsonReaderSync(
        reader: JsonReader,
        cacheKey: String?,
        close: Boolean
    ): LottieResult<LottieComposition> {
        return fromJsonReaderSyncInternal(reader, cacheKey, close)
    }

    private fun fromJsonReaderSyncInternal(
        reader: JsonReader,
        cacheKey: String?,
        close: Boolean
    ): LottieResult<LottieComposition> {
        try {
            val cachedComposition = if (cacheKey == null) null else LottieCompositionCache.instance[cacheKey]
            if (cachedComposition != null) {
                return LottieResult(cachedComposition)
            }
            val composition = LottieCompositionMoshiParser.parse(reader)
            if (cacheKey != null) {
                LottieCompositionCache.instance.put(cacheKey, composition)
            }
            return LottieResult(composition)
        } catch (e: Exception) {
            return LottieResult(e)
        } finally {
            if (close) {
                Utils.closeQuietly(reader)
            }
        }
    }

    /**
     * In this overload, embedded fonts will NOT be parsed. If your zip file has custom fonts, use the overload
     * that takes Context as the first parameter.
     */
    @JvmStatic
    fun fromZipStream(inputStream: ZipInputStream, cacheKey: String?): LottieTask<LottieComposition>? {
        return fromZipStream(null, inputStream, cacheKey)
    }

    /**
     * In this overload, embedded fonts will NOT be parsed. If your zip file has custom fonts, use the overload
     * that takes Context as the first parameter.
     */
    fun fromZipStream(inputStream: ZipInputStream, cacheKey: String?, close: Boolean): LottieTask<LottieComposition>? {
        return fromZipStream(null, inputStream, cacheKey, close)
    }

    /**
     * @see .fromZipStreamSync
     */
    fun fromZipStream(context: Context?, inputStream: ZipInputStream, cacheKey: String?): LottieTask<LottieComposition>? {
        return cache(cacheKey, { fromZipStreamSync(context, inputStream, cacheKey) }, { Utils.closeQuietly(inputStream) })
    }

    /**
     * @see .fromZipStreamSync
     */
    fun fromZipStream(
        context: Context?, inputStream: ZipInputStream,
        cacheKey: String?, close: Boolean
    ): LottieTask<LottieComposition> {
        return cache(
            cacheKey,
            { fromZipStreamSync(context, inputStream, cacheKey) },
            if (close) Runnable { Utils.closeQuietly(inputStream) } else null)
    }

    /**
     * Parses a zip input stream into a Lottie composition.
     * Your zip file should just be a folder with your json file and images zipped together.
     * It will automatically store and configure any images inside the animation if they exist.
     *
     *
     * In this overload, embedded fonts will NOT be parsed. If your zip file has custom fonts, use the overload
     * that takes Context as the first parameter.
     */
    /**
     * Parses a zip input stream into a Lottie composition.
     * Your zip file should just be a folder with your json file and images zipped together.
     * It will automatically store and configure any images inside the animation if they exist.
     *
     *
     * In this overload, embedded fonts will NOT be parsed. If your zip file has custom fonts, use the overload
     * that takes Context as the first parameter.
     *
     *
     * The ZipInputStream will be automatically closed at the end. If you would like to keep it open, use the overload
     * with a close parameter and pass in false.
     */
    @JvmOverloads
    fun fromZipStreamSync(inputStream: ZipInputStream, cacheKey: String?, close: Boolean = true): LottieResult<LottieComposition>? {
        return fromZipStreamSync(null, inputStream, cacheKey, close)
    }

    /**
     * Parses a zip input stream into a Lottie composition.
     * Your zip file should just be a folder with your json file and images zipped together.
     * It will automatically store and configure any images inside the animation if they exist.
     *
     *
     * The ZipInputStream will be automatically closed at the end. If you would like to keep it open, use the overload
     * with a close parameter and pass in false.
     *
     * @param context is optional and only needed if your zip file contains ttf or otf fonts. If yours doesn't, you may pass null.
     * Embedded fonts may be .ttf or .otf files, can be in subdirectories, but must have the same name as the
     * font family (fFamily) in your animation file.
     */
    @JvmStatic
    @WorkerThread
    fun fromZipStreamSync(context: Context?, inputStream: ZipInputStream, cacheKey: String?): LottieResult<LottieComposition>? {
        return fromZipStreamSync(context, inputStream, cacheKey, true)
    }

    /**
     * Parses a zip input stream into a Lottie composition.
     * Your zip file should just be a folder with your json file and images zipped together.
     * It will automatically store and configure any images inside the animation if they exist.
     *
     * @param context is optional and only needed if your zip file contains ttf or otf fonts. If yours doesn't, you may pass null.
     * Embedded fonts may be .ttf or .otf files, can be in subdirectories, but must have the same name as the
     * font family (fFamily) in your animation file.
     */
    @WorkerThread
    fun fromZipStreamSync(
        context: Context?,
        inputStream: ZipInputStream,
        cacheKey: String?, close: Boolean
    ): LottieResult<LottieComposition>? {
        try {
            return fromZipStreamSyncInternal(context, inputStream, cacheKey)
        } finally {
            if (close) {
                Utils.closeQuietly(inputStream)
            }
        }
    }

    @WorkerThread
    private fun fromZipStreamSyncInternal(
        context: Context?,
        inputStream: ZipInputStream,
        cacheKey: String?
    ): LottieResult<LottieComposition>? {
        var composition: LottieComposition? = null
        val images: MutableMap<String, Bitmap> = HashMap()
        val fonts: MutableMap<String, Typeface> = HashMap()

        try {
            val cachedComposition = if (cacheKey == null) null else LottieCompositionCache.instance[cacheKey]
            if (cachedComposition != null) {
                return LottieResult(cachedComposition)
            }
            var entry = inputStream.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (entryName.contains("__MACOSX")) {
                    inputStream.closeEntry()
                } else if (entry.name.equals("manifest.json", ignoreCase = true)) { //ignore .lottie manifest
                    inputStream.closeEntry()
                } else if (entry.name.contains(".json")) {
                    val reader = JsonReader.of(Okio.buffer(Okio.source(inputStream)))
                    composition = fromJsonReaderSyncInternal(reader, null, false).value
                } else if (entryName.contains(".png") || entryName.contains(".webp") || entryName.contains(".jpg") || entryName.contains(".jpeg")) {
                    val splitName = entryName.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val name = splitName[splitName.size - 1]
                    images[name] = BitmapFactory.decodeStream(inputStream)
                } else if (entryName.contains(".ttf") || entryName.contains(".otf")) {
                    val splitName = entryName.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val fileName = splitName[splitName.size - 1]
                    val fontFamily = fileName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                    val tempFile = File(context!!.cacheDir, fileName)
                    val fos = FileOutputStream(tempFile)
                    try {
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(4 * 1024)
                            var read: Int
                            while ((inputStream.read(buffer).also { read = it }) != -1) {
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    } catch (e: Throwable) {
                        Logger.warning("Unable to save font $fontFamily to the temporary file: $fileName. ", e)
                    }
                    val typeface = Typeface.createFromFile(tempFile)
                    if (!tempFile.delete()) {
                        Logger.warning("Failed to delete temp font file " + tempFile.absolutePath + ".")
                    }
                    fonts[fontFamily] = typeface
                } else {
                    inputStream.closeEntry()
                }

                entry = inputStream.nextEntry
            }
        } catch (e: IOException) {
            return LottieResult(e)
        }


        if (composition == null) {
            return LottieResult(IllegalArgumentException("Unable to parse composition"))
        }

        for ((key, value) in images) {
            val imageAsset = findImageAssetForFileName(composition, key)
            if (imageAsset != null) {
                imageAsset.bitmap = Utils.resizeBitmapIfNeeded(value, imageAsset.width, imageAsset.height)
            }
        }

        for ((key, value) in fonts) {
            var found = false
            for (font in composition.fonts!!.values) {
                if (font.family == key) {
                    found = true
                    font.typeface = value
                }
            }
            if (!found) {
                Logger.warning("Parsed font for $key however it was not found in the animation.")
            }
        }

        if (images.isEmpty()) {
            for ((_, value) in composition.getImages()!!) {
                val asset = value ?: return null
                val filename = asset.fileName
                val opts = BitmapFactory.Options()
                opts.inScaled = true
                opts.inDensity = 160

                if (filename.startsWith("data:") && filename.indexOf("base64,") > 0) {
                    // Contents look like a base64 data URI, with the format data:image/png;base64,<data>.
                    var data: ByteArray
                    try {
                        data = Base64.decode(filename.substring(filename.indexOf(',') + 1), Base64.DEFAULT)
                    } catch (e: IllegalArgumentException) {
                        Logger.warning("data URL did not have correct base64 format.", e)
                        return null
                    }
                    var bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, opts)
                    bitmap = Utils.resizeBitmapIfNeeded(bitmap, asset.width, asset.height)
                    asset.bitmap = bitmap
                }
            }
        }

        if (cacheKey != null) {
            LottieCompositionCache.instance.put(cacheKey, composition)
        }
        return LottieResult(composition)
    }

    /**
     * Check if a given InputStream points to a .zip compressed file
     */
    private fun isZipCompressed(inputSource: BufferedSource): Boolean {
        return matchesMagicBytes(inputSource, ZIP_MAGIC)
    }

    /**
     * Check if a given InputStream points to a .gzip compressed file
     */
    private fun isGzipCompressed(inputSource: BufferedSource): Boolean {
        return matchesMagicBytes(inputSource, GZIP_MAGIC)
    }

    private fun matchesMagicBytes(inputSource: BufferedSource, magic: ByteArray): Boolean {
        try {
            val peek = inputSource.peek()
            for (b in magic) {
                if (peek.readByte() != b) {
                    return false
                }
            }
            peek.close()
            return true
        } catch (e: NoSuchMethodError) {
            // This happens in the Android Studio layout preview.
            return false
        } catch (e: Exception) {
            Logger.error("Failed to check zip file header", e)
            return false
        }
    }

    private fun findImageAssetForFileName(composition: LottieComposition, fileName: String): LottieImageAsset? {
        for (asset in composition.getImages()!!.values) {
            if (asset.fileName == fileName) {
                return asset
            }
        }
        return null
    }

    /**
     * First, check to see if there are any in-progress tasks associated with the cache key and return it if there is.
     * If not, create a new task for the callable.
     * Then, add the new task to the task cache and set up listeners so it gets cleared when done.
     */
    private fun cache(
        cacheKey: String?, callable: Callable<LottieResult<LottieComposition>>,
        onCached: Runnable?
    ): LottieTask<LottieComposition> {
        var task: LottieTask<LottieComposition>? = null
        val cachedComposition = if (cacheKey == null) null else LottieCompositionCache.instance[cacheKey]
        if (cachedComposition != null) {
            task = LottieTask(cachedComposition)
        }
        if (cacheKey != null && taskCache.containsKey(cacheKey)) {
            task = taskCache[cacheKey]
        }
        if (task != null) {
            onCached?.run()
            return task
        }

        task = LottieTask(callable)
        if (cacheKey != null) {
            val resultAlreadyCalled = AtomicBoolean(false)
            task.addListener(LottieListener<LottieComposition> { result: LottieComposition ->
                taskCache.remove(cacheKey)
                resultAlreadyCalled.set(true)
                if (taskCache.size == 0) {
                    notifyTaskCacheIdleListeners(true)
                }
            })
            task.addFailureListener(LottieListener<Throwable?> { result: Throwable? ->
                taskCache.remove(cacheKey)
                resultAlreadyCalled.set(true)
                if (taskCache.size == 0) {
                    notifyTaskCacheIdleListeners(true)
                }
            })
            // It is technically possible for the task to finish and for the listeners to get called
            // before this code runs. If this happens, the task will be put in taskCache but never removed.
            // This would require this thread to be sleeping at exactly this point in the code
            // for long enough for the task to finish and call the listeners. Unlikely but not impossible.
            if (!resultAlreadyCalled.get()) {
                taskCache[cacheKey] = task
                if (taskCache.size == 1) {
                    notifyTaskCacheIdleListeners(false)
                }
            }
        }
        return task
    }

    private fun notifyTaskCacheIdleListeners(idle: Boolean) {
        val listeners: List<LottieTaskIdleListener> = ArrayList(taskIdleListeners)
        for (i in listeners.indices) {
            listeners[i].onIdleChanged(idle)
        }
    }
}
