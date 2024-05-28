package com.airbnb.lottie

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import androidx.annotation.RawRes
import androidx.annotation.RestrictTo
import androidx.annotation.WorkerThread
import androidx.collection.LongSparseArray
import androidx.collection.SparseArrayCompat
import com.airbnb.lottie.model.Font
import com.airbnb.lottie.model.FontCharacter
import com.airbnb.lottie.model.Marker
import com.airbnb.lottie.model.layer.Layer
import com.airbnb.lottie.parser.moshi.JsonReader
import com.airbnb.lottie.utils.Logger
import com.airbnb.lottie.utils.MiscUtils
import com.airbnb.lottie.utils.Utils
import org.json.JSONObject
import java.io.InputStream
import java.util.Arrays

/**
 * After Effects/Bodymovin composition model. This is the serialized model from which the
 * animation will be created. It is designed to be stateless, cacheable, and shareable.
 *
 *
 * To create one, use [LottieCompositionFactory].
 *
 *
 * It can be used with a [com.airbnb.lottie.LottieAnimationView] or
 * [com.airbnb.lottie.LottieDrawable].
 */
class LottieComposition {
    @JvmField
    val performanceTracker: PerformanceTracker = PerformanceTracker()
    private val warnings = HashSet<String>()
    private var precomps: Map<String, List<Layer>>? = null
    private var images: MutableMap<String, LottieImageAsset>? = null
    private var imagesDpScale = 0f

    /**
     * Map of font names to fonts
     */
    var fonts: Map<String, Font>? = null
        private set
    var markers: List<Marker>? = null
        private set
    var characters: SparseArrayCompat<FontCharacter>? = null
        private set
    private var layerMap: LongSparseArray<Layer>? = null
    var layers: List<Layer>? = null
        private set

    // This is stored as a set to avoid duplicates.
    var bounds: Rect? = null
        private set
    var startFrame: Float = 0f
        private set
    var endFrame: Float = 0f
        private set
    var frameRate: Float = 0f
        private set

    /**
     * Used to determine if an animation can be drawn with hardware acceleration.
     */
    private var hasDashPattern = false
    /**
     * Used to determine if an animation can be drawn with hardware acceleration.
     */
    /**
     * Counts the number of mattes and masks. Before Android switched to SKIA
     * for drawing in Oreo (API 28), using hardware acceleration with mattes and masks
     * was only faster until you had ~4 masks after which it would actually become slower.
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY)
    var maskAndMatteCount: Int = 0
        private set

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun init(
        bounds: Rect?,
        startFrame: Float,
        endFrame: Float,
        frameRate: Float,
        layers: List<Layer>?,
        layerMap: LongSparseArray<Layer>?,
        precomps: Map<String, List<Layer>>?,
        images: MutableMap<String, LottieImageAsset>?,
        imagesDpScale: Float,
        characters: SparseArrayCompat<FontCharacter>?,
        fonts: Map<String, Font>?,
        markers: List<Marker>?
    ) {
        this.bounds = bounds
        this.startFrame = startFrame
        this.endFrame = endFrame
        this.frameRate = frameRate
        this.layers = layers
        this.layerMap = layerMap
        this.precomps = precomps
        this.images = images
        this.imagesDpScale = imagesDpScale
        this.characters = characters
        this.fonts = fonts
        this.markers = markers
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun addWarning(warning: String) {
        Logger.warning(warning)
        warnings.add(warning)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun setHasDashPattern(hasDashPattern: Boolean) {
        this.hasDashPattern = hasDashPattern
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun incrementMatteOrMaskCount(amount: Int) {
        maskAndMatteCount += amount
    }

    /**
     * Used to determine if an animation can be drawn with hardware acceleration.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun hasDashPattern(): Boolean {
        return hasDashPattern
    }

    fun getWarnings(): ArrayList<String> {
        return ArrayList(Arrays.asList(*warnings.toTypedArray<String>()))
    }

    fun setPerformanceTrackingEnabled(enabled: Boolean) {
        performanceTracker.setEnabled(enabled)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun layerModelForId(id: Long): Layer? {
        return layerMap!![id]
    }

    val duration: Float
        get() = (durationFrames / frameRate * 1000).toLong().toFloat()

    fun getFrameForProgress(progress: Float): Float {
        return MiscUtils.lerp(startFrame, endFrame, progress)
    }

    fun getProgressForFrame(frame: Float): Float {
        val framesSinceStart = frame - startFrame
        val frameRange = endFrame - startFrame
        return framesSinceStart / frameRange
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun getPrecomps(id: String): List<Layer> {
        return precomps!![id].orEmpty()
    }

    fun getMarker(markerName: String): Marker? {
        val size = markers!!.size
        for (i in 0 until size) {
            val marker = markers!![i]
            if (marker.matchesName(markerName)) {
                return marker
            }
        }
        return null
    }

    fun hasImages(): Boolean {
        return !images.isNullOrEmpty()
    }

    /**
     * Returns a map of image asset id to [LottieImageAsset]. These assets contain image metadata exported
     * from After Effects or other design tool. The resulting Bitmaps can be set directly on the image asset so
     * they can be loaded once and reused across compositions.
     *
     * If the context dp scale has changed since the last time images were retrieved, images will be rescaled.
     */
    fun getImages(): Map<String, LottieImageAsset>? {
        val dpScale = Utils.dpScale()
        if (dpScale != imagesDpScale) {
            val entries: Set<Map.Entry<String, LottieImageAsset>> = images!!.entries

            for ((key, value) in entries) {
                images!![key] = value.copyWithScale(imagesDpScale / dpScale)
            }
        }
        imagesDpScale = dpScale
        return images
    }

    val durationFrames: Float
        get() = endFrame - startFrame


    override fun toString(): String {
        val sb = StringBuilder("LottieComposition:\n")
        for (layer in layers!!) {
            sb.append(layer.toString("\t"))
        }
        return sb.toString()
    }

    /**
     * This will be removed in the next version of Lottie. [LottieCompositionFactory] has improved
     * API names, failure handlers, and will return in-progress tasks so you will never parse the same
     * animation twice in parallel.
     *
     * @see LottieCompositionFactory
     */
    @Deprecated("")
    object Factory {
        /**
         * @see LottieCompositionFactory.fromAsset
         */
        @Suppress("deprecation")
        @Deprecated("")
        fun fromAssetFileName(context: Context, fileName: String, l: OnCompositionLoadedListener): Cancellable {
            val listener = ListenerAdapter(l)
            LottieCompositionFactory.fromAsset(context, fileName).addListener(listener)
            return listener
        }

        /**
         * @see LottieCompositionFactory.fromRawRes
         */
        @Suppress("deprecation")
        @Deprecated("")
        fun fromRawFile(context: Context, @RawRes resId: Int, l: OnCompositionLoadedListener): Cancellable {
            val listener = ListenerAdapter(l)
            LottieCompositionFactory.fromRawRes(context, resId).addListener(listener)
            return listener
        }

        /**
         * @see LottieCompositionFactory.fromJsonInputStream
         */
        @Suppress("deprecation")
        @Deprecated("")
        fun fromInputStream(stream: InputStream, l: OnCompositionLoadedListener): Cancellable {
            val listener = ListenerAdapter(l)
            LottieCompositionFactory.fromJsonInputStream(stream, null).addListener(listener)
            return listener
        }

        /**
         * @see LottieCompositionFactory.fromJsonString
         */
        @Suppress("deprecation")
        @Deprecated("")
        fun fromJsonString(jsonString: String, l: OnCompositionLoadedListener): Cancellable {
            val listener = ListenerAdapter(l)
            LottieCompositionFactory.fromJsonString(jsonString, null).addListener(listener)
            return listener
        }

        /**
         * @see LottieCompositionFactory.fromJsonReader
         */
        @Suppress("deprecation")
        @Deprecated("")
        fun fromJsonReader(reader: JsonReader, l: OnCompositionLoadedListener): Cancellable {
            val listener = ListenerAdapter(l)
            LottieCompositionFactory.fromJsonReader(reader, null).addListener(listener)
            return listener
        }

        /**
         * @see LottieCompositionFactory.fromAssetSync
         */
        @WorkerThread
        @Deprecated("")
        fun fromFileSync(context: Context, fileName: String): LottieComposition? {
            return LottieCompositionFactory.fromAssetSync(context, fileName)?.value
        }

        /**
         * @see LottieCompositionFactory.fromJsonInputStreamSync
         */
        @WorkerThread
        @Deprecated("")
        fun fromInputStreamSync(stream: InputStream): LottieComposition? {
            return LottieCompositionFactory.fromJsonInputStreamSync(stream, null)?.value
        }

        /**
         * This will now auto-close the input stream!
         *
         * @see LottieCompositionFactory.fromJsonInputStreamSync
         */
        @WorkerThread
        @Deprecated("")
        fun fromInputStreamSync(stream: InputStream, close: Boolean): LottieComposition? {
            if (close) {
                Logger.warning("Lottie now auto-closes input stream!")
            }
            return LottieCompositionFactory.fromJsonInputStreamSync(stream, null)?.value
        }

        /**
         * @see LottieCompositionFactory.fromJsonSync
         */
        @WorkerThread
        @Deprecated("")
        fun fromJsonSync(@Suppress("unused") res: Resources, json: JSONObject): LottieComposition? {
            return LottieCompositionFactory.fromJsonSync(json, null)?.value
        }

        /**
         * @see LottieCompositionFactory.fromJsonStringSync
         */
        @WorkerThread
        @Deprecated("")
        fun fromJsonSync(json: String): LottieComposition? {
            return LottieCompositionFactory.fromJsonStringSync(json, null)?.value
        }

        /**
         * @see LottieCompositionFactory.fromJsonReaderSync
         */
        @WorkerThread
        @Deprecated("")
        fun fromJsonSync(reader: JsonReader): LottieComposition? {
            return LottieCompositionFactory.fromJsonReaderSync(reader, null)?.value
        }

        @Suppress("deprecation")
        private class ListenerAdapter(private val listener: OnCompositionLoadedListener) : LottieListener<LottieComposition>, Cancellable {
            private var cancelled = false

            override fun onResult(composition: LottieComposition) {
                if (cancelled) {
                    return
                }
                listener.onCompositionLoaded(composition)
            }

            override fun cancel() {
                cancelled = true
            }
        }
    }
}
