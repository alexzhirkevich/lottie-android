package com.airbnb.lottie

import android.animation.Animator
import android.animation.Animator.AnimatorPauseListener
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.FloatRange
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import com.airbnb.lottie.L.beginSection
import com.airbnb.lottie.L.endSection
import com.airbnb.lottie.L.isTraceEnabled
import com.airbnb.lottie.animation.LPaint
import com.airbnb.lottie.manager.FontAssetManager
import com.airbnb.lottie.manager.ImageAssetManager
import com.airbnb.lottie.model.Font
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.model.Marker
import com.airbnb.lottie.model.layer.CompositionLayer
import com.airbnb.lottie.parser.LayerParser
import com.airbnb.lottie.utils.Logger
import com.airbnb.lottie.utils.LottieThreadFactory
import com.airbnb.lottie.utils.LottieValueAnimator
import com.airbnb.lottie.utils.MiscUtils
import com.airbnb.lottie.value.LottieFrameInfo
import com.airbnb.lottie.value.LottieValueCallback
import com.airbnb.lottie.value.SimpleLottieValueCallback
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * This can be used to show an lottie animation in any place that would normally take a drawable.
 *
 * @see [Full Documentation](http://airbnb.io/lottie)
 */
class LottieDrawable : Drawable(), Drawable.Callback, Animatable {
    private fun interface LazyCompositionTask {
        fun run(composition: LottieComposition?)
    }

    /**
     * Internal record keeping of the desired play state when [.isVisible] transitions to or is false.
     *
     *
     * If the animation was playing when it becomes invisible or play/pause is called on it while it is invisible, it will
     * store the state and then take the appropriate action when the drawable becomes visible again.
     */
    private enum class OnVisibleAction {
        NONE,
        PLAY,
        RESUME,
    }

    var composition: LottieComposition? = null
        private set
    private val animator: LottieValueAnimator = LottieValueAnimator()

    // Call animationsEnabled() instead of using these fields directly.
    private var systemAnimationsEnabled = true
    private var ignoreSystemAnimationsDisabled = false

    private var safeMode = false
    private var onVisibleAction = OnVisibleAction.NONE

    private val lazyCompositionTasks = ArrayList<LazyCompositionTask>()

    /**
     * ImageAssetManager created automatically by Lottie for views.
     */
    private var imageAssetManager: ImageAssetManager? = null
    var imageAssetsFolder: String? = null
        private set
    private var imageAssetDelegate: ImageAssetDelegate? = null
    private var fontAssetManager: FontAssetManager? = null
    private var fontMap: Map<String, Typeface>? = null

    /**
     * Will be set if manually overridden by [.setDefaultFontFileExtension].
     * This must be stored as a field in case it is set before the font asset delegate
     * has been created.
     */
    var defaultFontFileExtension: String = ".ttf"
        set(value) {
            field = value
            getFontAssetManager()?.setDefaultFontFileExtension(value)
        }
    var fontAssetDelegate: FontAssetDelegate? = null
        set(value) {
            field = value
            fontAssetManager?.setDelegate(value)
        }

    @JvmField
    var textDelegate: TextDelegate? = null
    var isMergePathsEnabledForKitKatAndAbove: Boolean = false
        private set
    /**
     * When true, dynamically set bitmaps will be drawn with the exact bounds of the original animation, regardless of the bitmap size.
     * When false, dynamically set bitmaps will be drawn at the top left of the original image but with its own bounds.
     *
     *
     * Defaults to false.
     */
    /**
     * When true, dynamically set bitmaps will be drawn with the exact bounds of the original animation, regardless of the bitmap size.
     * When false, dynamically set bitmaps will be drawn at the top left of the original image but with its own bounds.
     *
     *
     * Defaults to false.
     */
    @JvmField
    var maintainOriginalImageBounds: Boolean = false
    private var clipToCompositionBounds = true
    private var compositionLayer: CompositionLayer? = null
    private var alpha = 255
    private var performanceTrackingEnabled = false
    private var outlineMasksAndMattes = false

    /**
     * Sets whether to apply opacity to the each layer instead of shape.
     *
     *
     * Opacity is normally applied directly to a shape. In cases where translucent shapes overlap, applying opacity to a layer will be more accurate
     * at the expense of performance.
     *
     *
     * The default value is false.
     *
     *
     * Note: This process is very expensive. The performance impact will be reduced when hardware acceleration is enabled.
     *
     * @see android.view.View.setLayerType
     * @see LottieAnimationView.setRenderMode
     */
    @JvmField
    var isApplyingOpacityToLayersEnabled: Boolean = false

    /**
     * @see .setClipTextToBoundingBox
     */
    var clipTextToBoundingBox: Boolean = false
        /**
         * When true, if there is a bounding box set on a text layer (paragraph text), any text
         * that overflows past its height will not be drawn.
         */
        set(clipTextToBoundingBox) {
            if (clipTextToBoundingBox != field) {
                field = clipTextToBoundingBox
                invalidateSelf()
            }
        }

    private var renderMode = RenderMode.AUTOMATIC

    /**
     * The actual render mode derived from [.renderMode].
     */
    private var useSoftwareRendering = false
    private val renderingMatrix = Matrix()
    private var softwareRenderingBitmap: Bitmap? = null
    private var softwareRenderingCanvas: Canvas? = null
    private var canvasClipBounds: Rect? = null
    private var canvasClipBoundsRectF: RectF? = null
    private var softwareRenderingPaint: Paint? = null
    private var softwareRenderingSrcBoundsRect: Rect? = null
    private var softwareRenderingDstBoundsRect: Rect? = null
    private var softwareRenderingDstBoundsRectF: RectF? = null
    private var softwareRenderingTransformedBounds: RectF? = null
    private var softwareRenderingOriginalCanvasMatrix: Matrix? = null
    private var softwareRenderingOriginalCanvasMatrixInverse: Matrix? = null

    /**
     * True if the drawable has not been drawn since the last invalidateSelf.
     * We can do this to prevent things like bounds from getting recalculated
     * many times.
     */
    private var isDirty = false

    /**
     * **Note: this API is experimental and may changed.**
     *
     *
     * Sets the current value for [AsyncUpdates]. Refer to the docs for [AsyncUpdates] for more info.
     */
    var asyncUpdates: AsyncUpdates? = null
        /**
         * Returns the current value of [AsyncUpdates]. Refer to the docs for [AsyncUpdates] for more info.
         */
        get() {
            val asyncUpdates = field
            if (asyncUpdates != null) {
                return asyncUpdates
            }
            return L.defaultAsyncUpdates
        }
    private val progressUpdateListener = AnimatorUpdateListener { animation: ValueAnimator? ->
        if (asyncUpdatesEnabled) {
            // Render a new frame.
            // If draw is called while lastDrawnProgress is still recent enough, it will
            // draw straight away and then enqueue a background setProgress immediately after draw
            // finishes.
            invalidateSelf()
        } else if (compositionLayer != null) {
            compositionLayer!!.setProgress(animator.animatedValueAbsolute)
        }
    }

    /**
     * Ensures that setProgress and draw will never happen at the same time on different threads.
     * If that were to happen, parts of the animation may be on one frame while other parts would
     * be on another.
     */
    private val setProgressDrawLock = Semaphore(1)
    private var mainThreadHandler: Handler? = null
    private var invalidateSelfRunnable: Runnable? = null

    private val updateProgressRunnable = Runnable {
        val compositionLayer = this.compositionLayer ?: return@Runnable
        try {
            setProgressDrawLock.acquire()
            compositionLayer.setProgress(animator.animatedValueAbsolute)
            // Refer to invalidateSelfOnMainThread for more info.
            if (invalidateSelfOnMainThread && isDirty) {
                if (mainThreadHandler == null) {
                    mainThreadHandler = Handler(Looper.getMainLooper())
                    invalidateSelfRunnable = Runnable {
                        val callback = callback
                        callback?.invalidateDrawable(this)
                    }
                }
                mainThreadHandler!!.post(invalidateSelfRunnable!!)
            }
        } catch (e: InterruptedException) {
            // Do nothing.
        } finally {
            setProgressDrawLock.release()
        }
    }
    private var lastDrawnProgress = -Float.MAX_VALUE

    @IntDef(RESTART, REVERSE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class RepeatMode

    init {
        animator.addUpdateListener(progressUpdateListener)
    }

    /**
     * Returns whether or not any layers in this composition has masks.
     */
    fun hasMasks(): Boolean {
        return compositionLayer != null && compositionLayer!!.hasMasks()
    }

    /**
     * Returns whether or not any layers in this composition has a matte layer.
     */
    fun hasMatte(): Boolean {
        return compositionLayer != null && compositionLayer!!.hasMatte()
    }

    fun enableMergePathsForKitKatAndAbove(): Boolean {
        return isMergePathsEnabledForKitKatAndAbove
    }

    /**
     * Enable this to get merge path support for devices running KitKat (19) and above.
     *
     *
     * Merge paths currently don't work if the the operand shape is entirely contained within the
     * first shape. If you need to cut out one shape from another shape, use an even-odd fill type
     * instead of using merge paths.
     */
    fun enableMergePathsForKitKatAndAbove(enable: Boolean) {
        if (isMergePathsEnabledForKitKatAndAbove == enable) {
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Logger.warning("Merge paths are not supported pre-Kit Kat.")
            return
        }
        isMergePathsEnabledForKitKatAndAbove = enable
        if (composition != null) {
            buildCompositionLayer()
        }
    }

    /**
     * Sets whether or not Lottie should clip to the original animation composition bounds.
     *
     *
     * Defaults to true.
     */
    fun setClipToCompositionBounds(clipToCompositionBounds: Boolean) {
        if (clipToCompositionBounds != this.clipToCompositionBounds) {
            this.clipToCompositionBounds = clipToCompositionBounds
            val compositionLayer = this.compositionLayer
            compositionLayer?.setClipToCompositionBounds(clipToCompositionBounds)
            invalidateSelf()
        }
    }

    /**
     * Gets whether or not Lottie should clip to the original animation composition bounds.
     *
     *
     * Defaults to true.
     */
    fun getClipToCompositionBounds(): Boolean {
        return clipToCompositionBounds
    }

    /**
     * If you use image assets, you must explicitly specify the folder in assets/ in which they are
     * located because bodymovin uses the name filenames across all compositions (img_#).
     * Do NOT rename the images themselves.
     *
     *
     * If your images are located in src/main/assets/airbnb_loader/ then call
     * `setImageAssetsFolder("airbnb_loader/");`.
     *
     *
     *
     *
     * Be wary if you are using many images, however. Lottie is designed to work with vector shapes
     * from After Effects. If your images look like they could be represented with vector shapes,
     * see if it is possible to convert them to shape layers and re-export your animation. Check
     * the documentation at [airbnb.io/lottie](http://airbnb.io/lottie) for more information about importing shapes from
     * Sketch or Illustrator to avoid this.
     */
    fun setImagesAssetsFolder(imageAssetsFolder: String?) {
        this.imageAssetsFolder = imageAssetsFolder
    }

    /**
     * Create a composition with [LottieCompositionFactory]
     *
     * @return True if the composition is different from the previously set composition, false otherwise.
     */
    fun setComposition(composition: LottieComposition): Boolean {
        if (this.composition == composition) {
            return false
        }

        isDirty = true
        clearComposition()
        this.composition = composition
        buildCompositionLayer()
        animator.setComposition(composition)
        progress = animator.animatedFraction

        // We copy the tasks to a new ArrayList so that if this method is called from multiple threads,
        // then there won't be two iterators iterating and removing at the same time.
        val it = ArrayList(lazyCompositionTasks).iterator()
        while (it.hasNext()) {
            val t = it.next()
            // The task should never be null but it appears to happen in rare cases. Maybe it's an oem-specific or ART bug.
            // https://github.com/airbnb/lottie-android/issues/1702
            if (t != null) {
                t.run(composition)
            }
            it.remove()
        }
        lazyCompositionTasks.clear()

        composition.setPerformanceTrackingEnabled(performanceTrackingEnabled)
        computeRenderMode()

        // Ensure that ImageView updates the drawable width/height so it can
        // properly calculate its drawable matrix.
        val callback = callback
        if (callback is ImageView) {
            callback.setImageDrawable(null)
            callback.setImageDrawable(this)
        }

        return true
    }

    /**
     * Call this to set whether or not to render with hardware or software acceleration.
     * Lottie defaults to Automatic which will use hardware acceleration unless:
     * 1) There are dash paths and the device is pre-Pie.
     * 2) There are more than 4 masks and mattes and the device is pre-Pie.
     * Hardware acceleration is generally faster for those devices unless
     * there are many large mattes and masks in which case there is a lot
     * of GPU uploadTexture thrashing which makes it much slower.
     *
     *
     * In most cases, hardware rendering will be faster, even if you have mattes and masks.
     * However, if you have multiple mattes and masks (especially large ones), you
     * should test both render modes. You should also test on pre-Pie and Pie+ devices
     * because the underlying rendering engine changed significantly.
     *
     * @see [Android Hardware Acceleration](https://developer.android.com/guide/topics/graphics/hardware-accel.unsupported)
     */
    fun setRenderMode(renderMode: RenderMode) {
        this.renderMode = renderMode
        computeRenderMode()
    }

    val asyncUpdatesEnabled: Boolean
        /**
         * Similar to [.getAsyncUpdates] except it returns the actual
         * boolean value for whether async updates are enabled or not.
         * This is useful when the mode is automatic and you want to know
         * whether automatic is defaulting to enabled or not.
         */
        get() = asyncUpdates == AsyncUpdates.ENABLED

    /**
     * Returns the actual render mode being used. It will always be [RenderMode.HARDWARE] or [RenderMode.SOFTWARE].
     * When the render mode is set to AUTOMATIC, the value will be derived from [RenderMode.useSoftwareRendering].
     */
    fun getRenderMode(): RenderMode {
        return if (useSoftwareRendering) RenderMode.SOFTWARE else RenderMode.HARDWARE
    }

    private fun computeRenderMode() {
        val composition = this.composition ?: return
        useSoftwareRendering = renderMode.useSoftwareRendering(
            Build.VERSION.SDK_INT, composition.hasDashPattern(), composition.maskAndMatteCount
        )
    }

    fun setPerformanceTrackingEnabled(enabled: Boolean) {
        performanceTrackingEnabled = enabled
        if (composition != null) {
            composition!!.setPerformanceTrackingEnabled(enabled)
        }
    }

    /**
     * Enable this to debug slow animations by outlining masks and mattes. The performance overhead of the masks and mattes will
     * be proportional to the surface area of all of the masks/mattes combined.
     *
     *
     * DO NOT leave this enabled in production.
     */
    fun setOutlineMasksAndMattes(outline: Boolean) {
        if (outlineMasksAndMattes == outline) {
            return
        }
        outlineMasksAndMattes = outline
        if (compositionLayer != null) {
            compositionLayer!!.setOutlineMasksAndMattes(outline)
        }
    }

    val performanceTracker: PerformanceTracker?
        get() {
            if (composition != null) {
                return composition!!.performanceTracker
            }
            return null
        }

    /**
     * This API no longer has any effect.
     */
    @Deprecated("")
    fun disableExtraScaleModeInFitXY() {
    }

    private fun buildCompositionLayer() {
        val composition = this.composition ?: return
        compositionLayer = CompositionLayer(
            this, LayerParser.parse(composition), composition.layers.orEmpty(), composition
        )
        if (outlineMasksAndMattes) {
            compositionLayer!!.setOutlineMasksAndMattes(true)
        }
        compositionLayer!!.setClipToCompositionBounds(clipToCompositionBounds)
    }

    fun clearComposition() {
        if (animator.isRunning) {
            animator.cancel()
            if (!isVisible) {
                onVisibleAction = OnVisibleAction.NONE
            }
        }
        composition = null
        compositionLayer = null
        imageAssetManager = null
        lastDrawnProgress = -Float.MAX_VALUE
        animator.clearComposition()
        invalidateSelf()
    }

    /**
     * If you are experiencing a device specific crash that happens during drawing, you can set this to true
     * for those devices. If set to true, draw will be wrapped with a try/catch which will cause Lottie to
     * render an empty frame rather than crash your app.
     *
     *
     * Ideally, you will never need this and the vast majority of apps and animations won't. However, you may use
     * this for very specific cases if absolutely necessary.
     */
    fun setSafeMode(safeMode: Boolean) {
        this.safeMode = safeMode
    }

    override fun invalidateSelf() {
        if (isDirty) {
            return
        }
        isDirty = true

        // Refer to invalidateSelfOnMainThread for more info.
        if (invalidateSelfOnMainThread && Looper.getMainLooper() != Looper.myLooper()) {
            return
        }
        val callback = callback
        callback?.invalidateDrawable(this)
    }

    override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int) {
        this.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int {
        return alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        Logger.warning("Use addColorFilter instead.")
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    /**
     * Helper for the async execution path to potentially call setProgress
     * before drawing if the current progress has drifted sufficiently far
     * from the last set progress.
     *
     * @see AsyncUpdates
     *
     * @see .setAsyncUpdates
     */
    private fun shouldSetProgressBeforeDrawing(): Boolean {
        val composition = this.composition ?: return false
        val lastDrawnProgress = this.lastDrawnProgress
        val currentProgress = animator.animatedValueAbsolute
        this.lastDrawnProgress = currentProgress

        val duration = composition.duration

        val deltaProgress = abs((currentProgress - lastDrawnProgress).toDouble()).toFloat()
        val deltaMs = deltaProgress * duration
        return deltaMs >= MAX_DELTA_MS_ASYNC_SET_PROGRESS
    }

    override fun draw(canvas: Canvas) {
        val compositionLayer = this.compositionLayer ?: return
        val asyncUpdatesEnabled = asyncUpdatesEnabled
        try {
            if (asyncUpdatesEnabled) {
                setProgressDrawLock.acquire()
            }
            if (isTraceEnabled()) {
                beginSection("Drawable#draw")
            }

            if (asyncUpdatesEnabled && shouldSetProgressBeforeDrawing()) {
                progress = animator.animatedValueAbsolute
            }

            if (safeMode) {
                try {
                    if (useSoftwareRendering) {
                        renderAndDrawAsBitmap(canvas, compositionLayer)
                    } else {
                        drawDirectlyToCanvas(canvas)
                    }
                } catch (e: Throwable) {
                    Logger.error("Lottie crashed in draw!", e)
                }
            } else {
                if (useSoftwareRendering) {
                    renderAndDrawAsBitmap(canvas, compositionLayer)
                } else {
                    drawDirectlyToCanvas(canvas)
                }
            }

            isDirty = false
        } catch (e: InterruptedException) {
            // Do nothing.
        } finally {
            if (isTraceEnabled()) {
                endSection("Drawable#draw")
            }
            if (asyncUpdatesEnabled) {
                setProgressDrawLock.release()
                if (compositionLayer.getProgress() != animator.animatedValueAbsolute) {
                    setProgressExecutor.execute(updateProgressRunnable)
                }
            }
        }
    }

    /**
     * To be used by lottie-compose only.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun draw(canvas: Canvas, matrix: Matrix) {
        val compositionLayer = this.compositionLayer
        val composition = this.composition
        if (compositionLayer == null || composition == null) {
            return
        }
        val asyncUpdatesEnabled = asyncUpdatesEnabled
        try {
            if (asyncUpdatesEnabled) {
                setProgressDrawLock.acquire()
                if (shouldSetProgressBeforeDrawing()) {
                    progress = animator.animatedValueAbsolute
                }
            }

            if (useSoftwareRendering) {
                canvas.save()
                canvas.concat(matrix)
                renderAndDrawAsBitmap(canvas, compositionLayer)
                canvas.restore()
            } else {
                compositionLayer.draw(canvas, matrix, alpha)
            }
            isDirty = false
        } catch (e: InterruptedException) {
            // Do nothing.
        } finally {
            if (asyncUpdatesEnabled) {
                setProgressDrawLock.release()
                if (compositionLayer.getProgress() != animator.animatedValueAbsolute) {
                    setProgressExecutor.execute(updateProgressRunnable)
                }
            }
        }
    }

    // <editor-fold desc="animator">
    @MainThread
    override fun start() {
        val callback = callback
        if (callback is View && callback.isInEditMode) {
            // Don't auto play when in edit mode.
            return
        }
        playAnimation()
    }

    @MainThread
    override fun stop() {
        endAnimation()
    }

    override fun isRunning(): Boolean {
        return isAnimating
    }

    /**
     * Plays the animation from the beginning. If speed is &lt; 0, it will start at the end
     * and play towards the beginning
     */
    @MainThread
    fun playAnimation() {
        if (compositionLayer == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> playAnimation() })
            return
        }

        computeRenderMode()
        if (animationsEnabled() || repeatCount == 0) {
            if (isVisible) {
                animator.playAnimation()
                onVisibleAction = OnVisibleAction.NONE
            } else {
                onVisibleAction = OnVisibleAction.PLAY
            }
        }
        if (!animationsEnabled()) {
            val markerForAnimationsDisabled = markerForAnimationsDisabled
            if (markerForAnimationsDisabled != null) {
                frame = markerForAnimationsDisabled.startFrame.toInt()
            } else {
                frame = (if (speed < 0) minFrame else maxFrame).toInt()
            }
            animator.endAnimation()
            if (!isVisible) {
                onVisibleAction = OnVisibleAction.NONE
            }
        }
    }


    private val markerForAnimationsDisabled: Marker?
        /**
         * This method is used to get the marker for animations when system animations are disabled.
         * It iterates over the list of allowed reduced motion markers and returns the first non-null marker it finds.
         * If no non-null marker is found, it returns null.
         *
         * @return The first non-null marker from the list of allowed reduced motion markers, or null if no such marker is found.
         */
        get() {
            var marker: Marker? = null
            for (markerName in ALLOWED_REDUCED_MOTION_MARKERS) {
                marker = composition!!.getMarker(markerName)
                if (marker != null) {
                    break
                }
            }
            return marker
        }

    @MainThread
    fun endAnimation() {
        lazyCompositionTasks.clear()
        animator.endAnimation()
        if (!isVisible) {
            onVisibleAction = OnVisibleAction.NONE
        }
    }

    /**
     * Continues playing the animation from its current position. If speed &lt; 0, it will play backwards
     * from the current position.
     */
    @MainThread
    fun resumeAnimation() {
        if (compositionLayer == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> resumeAnimation() })
            return
        }

        computeRenderMode()
        if (animationsEnabled() || repeatCount == 0) {
            if (isVisible) {
                animator.resumeAnimation()
                onVisibleAction = OnVisibleAction.NONE
            } else {
                onVisibleAction = OnVisibleAction.RESUME
            }
        }
        if (!animationsEnabled()) {
            frame = (if (speed < 0) minFrame else maxFrame).toInt()
            animator.endAnimation()
            if (!isVisible) {
                onVisibleAction = OnVisibleAction.NONE
            }
        }
    }

    /**
     * Sets the minimum frame that the animation will start from when playing or looping.
     */
    fun setMinFrame(minFrame: Int) {
        if (composition == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> setMinFrame(minFrame) })
            return
        }
        animator.setMinFrame(minFrame.toFloat())
    }

    val minFrame: Float
        /**
         * Returns the minimum frame set by [.setMinFrame] or [.setMinProgress]
         */
        get() = animator.getMinFrame()

    /**
     * Sets the minimum progress that the animation will start from when playing or looping.
     */
    fun setMinProgress(minProgress: Float) {
        if (composition == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> setMinProgress(minProgress) })
            return
        }
        setMinFrame(MiscUtils.lerp(composition!!.startFrame, composition!!.endFrame, minProgress).toInt())
    }

    /**
     * Sets the maximum frame that the animation will end at when playing or looping.
     *
     *
     * The value will be clamped to the composition bounds. For example, setting Integer.MAX_VALUE would result in the same
     * thing as composition.endFrame.
     */
    fun setMaxFrame(maxFrame: Int) {
        if (composition == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> setMaxFrame(maxFrame) })
            return
        }
        animator.setMinFrame(maxFrame + 0.99f)
    }

    val maxFrame: Float
        /**
         * Returns the maximum frame set by [.setMaxFrame] or [.setMaxProgress]
         */
        get() = animator.getMaxFrame()

    /**
     * Sets the maximum progress that the animation will end at when playing or looping.
     */
    fun setMaxProgress(@FloatRange(from = 0.0, to = 1.0) maxProgress: Float) {
        if (composition == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> setMaxProgress(maxProgress) })
            return
        }
        animator.setMaxFrame(MiscUtils.lerp(composition!!.startFrame, composition!!.endFrame, maxProgress))
    }

    /**
     * Sets the minimum frame to the start time of the specified marker.
     *
     * @throws IllegalArgumentException if the marker is not found.
     */
    fun setMinFrame(markerName: String) {
        if (composition == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> setMinFrame(markerName) })
            return
        }
        val marker = composition!!.getMarker(markerName) ?: throw IllegalArgumentException("Cannot find marker with name $markerName.")
        setMinFrame(marker.startFrame.toInt())
    }

    /**
     * Sets the maximum frame to the start time + duration of the specified marker.
     *
     * @throws IllegalArgumentException if the marker is not found.
     */
    fun setMaxFrame(markerName: String) {
        if (composition == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> setMaxFrame(markerName) })
            return
        }
        val marker = composition!!.getMarker(markerName) ?: throw IllegalArgumentException("Cannot find marker with name $markerName.")
        setMaxFrame((marker.startFrame + marker.durationFrames).toInt())
    }

    /**
     * Sets the minimum and maximum frame to the start time and start time + duration
     * of the specified marker.
     *
     * @throws IllegalArgumentException if the marker is not found.
     */
    fun setMinAndMaxFrame(markerName: String) {
        if (composition == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> setMinAndMaxFrame(markerName) })
            return
        }
        val marker = composition!!.getMarker(markerName) ?: throw IllegalArgumentException("Cannot find marker with name $markerName.")
        val startFrame = marker.startFrame.toInt()
        setMinAndMaxFrame(startFrame, startFrame + marker.durationFrames.toInt())
    }

    /**
     * Sets the minimum and maximum frame to the start marker start and the maximum frame to the end marker start.
     * playEndMarkerStartFrame determines whether or not to play the frame that the end marker is on. If the end marker
     * represents the end of the section that you want, it should be true. If the marker represents the beginning of the
     * next section, it should be false.
     *
     * @throws IllegalArgumentException if either marker is not found.
     */
    fun setMinAndMaxFrame(startMarkerName: String, endMarkerName: String, playEndMarkerStartFrame: Boolean) {
        if (composition == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? ->
                setMinAndMaxFrame(
                    startMarkerName,
                    endMarkerName,
                    playEndMarkerStartFrame
                )
            })
            return
        }
        val startMarker = composition!!.getMarker(startMarkerName)
            ?: throw IllegalArgumentException("Cannot find marker with name $startMarkerName.")
        val startFrame = startMarker.startFrame.toInt()

        val endMarker = composition!!.getMarker(endMarkerName) ?: throw IllegalArgumentException("Cannot find marker with name $endMarkerName.")
        val endFrame = (endMarker.startFrame + (if (playEndMarkerStartFrame) 1f else 0f)).toInt()

        setMinAndMaxFrame(startFrame, endFrame)
    }

    /**
     * @see .setMinFrame
     * @see .setMaxFrame
     */
    fun setMinAndMaxFrame(minFrame: Int, maxFrame: Int) {
        if (composition == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> setMinAndMaxFrame(minFrame, maxFrame) })
            return
        }
        // Adding 0.99 ensures that the maxFrame itself gets played.
        animator.setMinAndMaxFrames(minFrame.toFloat(), maxFrame + 0.99f)
    }

    /**
     * @see .setMinProgress
     * @see .setMaxProgress
     */
    fun setMinAndMaxProgress(
        @FloatRange(from = 0.0, to = 1.0) minProgress: Float,
        @FloatRange(from = 0.0, to = 1.0) maxProgress: Float
    ) {
        if (composition == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> setMinAndMaxProgress(minProgress, maxProgress) })
            return
        }

        setMinAndMaxFrame(
            MiscUtils.lerp(composition!!.startFrame, composition!!.endFrame, minProgress).toInt(),
            MiscUtils.lerp(composition!!.startFrame, composition!!.endFrame, maxProgress).toInt()
        )
    }

    /**
     * Reverses the current animation speed. This does NOT play the animation.
     *
     * @see .setSpeed
     * @see .playAnimation
     * @see .resumeAnimation
     */
    fun reverseAnimationSpeed() {
        animator.reverseAnimationSpeed()
    }

    var speed: Float
        /**
         * Returns the current playback speed. This will be &lt; 0 if the animation is playing backwards.
         */
        get() = animator.speed
        /**
         * Sets the playback speed. If speed &lt; 0, the animation will play backwards.
         */
        set(speed) {
            animator.speed = speed
        }

    fun addAnimatorUpdateListener(updateListener: AnimatorUpdateListener) {
        animator.addUpdateListener(updateListener)
    }

    fun removeAnimatorUpdateListener(updateListener: AnimatorUpdateListener) {
        animator.removeUpdateListener(updateListener)
    }

    fun removeAllUpdateListeners() {
        animator.removeAllUpdateListeners()
        animator.addUpdateListener(progressUpdateListener)
    }

    fun addAnimatorListener(listener: Animator.AnimatorListener) {
        animator.addListener(listener)
    }

    fun removeAnimatorListener(listener: Animator.AnimatorListener) {
        animator.removeListener(listener)
    }

    fun removeAllAnimatorListeners() {
        animator.removeAllListeners()
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    fun addAnimatorPauseListener(listener: AnimatorPauseListener) {
        animator.addPauseListener(listener)
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    fun removeAnimatorPauseListener(listener: AnimatorPauseListener) {
        animator.removePauseListener(listener)
    }

    var frame: Int = 0
        /**
         * Get the currently rendered frame.
         */
        get() = animator.getFrame().toInt()
        /**
         * Sets the progress to the specified frame.
         * If the composition isn't set yet, the progress will be set to the frame when
         * it is.
         */
        set(frame) {
            if (composition == null) {
                lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> field = frame })
                return
            }

            animator.setFrame(frame.toFloat())
        }

    /**
     * @see .setRepeatCount
     */
    @Deprecated("")
    fun loop(loop: Boolean) {
        animator.repeatCount = if (loop) ValueAnimator.INFINITE else 0
    }

    @get:RepeatMode
    @get:SuppressLint("WrongConstant")
    var repeatMode: Int
        /**
         * Defines what this animation should do when it reaches the end.
         *
         * @return either one of [.REVERSE] or [.RESTART]
         */
        get() = animator.repeatMode
        /**
         * Defines what this animation should do when it reaches the end. This
         * setting is applied only when the repeat count is either greater than
         * 0 or [.INFINITE]. Defaults to [.RESTART].
         *
         * @param mode [.RESTART] or [.REVERSE]
         */
        set(mode) {
            animator.repeatMode = mode
        }

    var repeatCount: Int
        /**
         * Defines how many times the animation should repeat. The default value
         * is 0.
         *
         * @return the number of times the animation should repeat, or [.INFINITE]
         */
        get() = animator.repeatCount
        /**
         * Sets how many times the animation should be repeated. If the repeat
         * count is 0, the animation is never repeated. If the repeat count is
         * greater than 0 or [.INFINITE], the repeat mode will be taken
         * into account. The repeat count is 0 by default.
         *
         * @param count the number of times the animation should be repeated
         */
        set(count) {
            animator.repeatCount = count
        }


    @get:Suppress("unused")
    val isLooping: Boolean
        get() = animator.repeatCount == ValueAnimator.INFINITE

    val isAnimating: Boolean
        get() {
            // On some versions of Android, this is called from the LottieAnimationView constructor, before animator was created.
            // https://github.com/airbnb/lottie-android/issues/1430
            if (animator == null) {
                return false
            }
            return animator.isRunning
        }

    val isAnimatingOrWillAnimateOnVisible: Boolean
        get() = if (isVisible) {
            animator.isRunning
        } else {
            onVisibleAction == OnVisibleAction.PLAY || onVisibleAction == OnVisibleAction.RESUME
        }

    private fun animationsEnabled(): Boolean {
        return systemAnimationsEnabled || ignoreSystemAnimationsDisabled
    }

    /**
     * Tell Lottie that system animations are disabled. When using [LottieAnimationView] or Compose `LottieAnimation`, this is done
     * automatically. However, if you are using LottieDrawable on its own, you should set this to false when
     * [com.airbnb.lottie.utils.Utils.getAnimationScale] is 0. If the animation is provided a "reduced motion"
     * marker name, they will be shown instead of the first or last frame. Supported marker names are case insensitive, and include:
     * - reduced motion
     * - reducedMotion
     * - reduced_motion
     * - reduced-motion
     */
    fun setSystemAnimationsAreEnabled(areEnabled: Boolean) {
        systemAnimationsEnabled = areEnabled
    }

    // </editor-fold>
    /**
     * Allows ignoring system animations settings, therefore allowing animations to run even if they are disabled.
     *
     *
     * Defaults to false.
     *
     * @param ignore if true animations will run even when they are disabled in the system settings.
     */
    fun setIgnoreDisabledSystemAnimations(ignore: Boolean) {
        ignoreSystemAnimationsDisabled = ignore
    }

    /**
     * Lottie files can specify a target frame rate. By default, Lottie ignores it and re-renders
     * on every frame. If that behavior is undesirable, you can set this to true to use the composition
     * frame rate instead.
     *
     *
     * Note: composition frame rates are usually lower than display frame rates
     * so this will likely make your animation feel janky. However, it may be desirable
     * for specific situations such as pixel art that are intended to have low frame rates.
     */
    fun setUseCompositionFrameRate(useCompositionFrameRate: Boolean) {
        animator.setUseCompositionFrameRate(useCompositionFrameRate)
    }

    /**
     * Use this if you can't bundle images with your app. This may be useful if you download the
     * animations from the network or have the images saved to an SD Card. In that case, Lottie
     * will defer the loading of the bitmap to this delegate.
     *
     *
     * Be wary if you are using many images, however. Lottie is designed to work with vector shapes
     * from After Effects. If your images look like they could be represented with vector shapes,
     * see if it is possible to convert them to shape layers and re-export your animation. Check
     * the documentation at [http://airbnb.io/lottie](http://airbnb.io/lottie) for more information about importing shapes from
     * Sketch or Illustrator to avoid this.
     */
    fun setImageAssetDelegate(assetDelegate: ImageAssetDelegate?) {
        this.imageAssetDelegate = assetDelegate
        if (imageAssetManager != null) {
            imageAssetManager!!.setDelegate(assetDelegate)
        }
    }


    /**
     * Set a map from font name keys to Typefaces.
     * The keys can be in the form:
     * * fontFamily
     * * fontFamily-fontStyle
     * * fontName
     * All 3 are defined as fName, fFamily, and fStyle in the Lottie file.
     *
     *
     * If you change a value in fontMap, create a new map or call
     * [.invalidateSelf]. Setting the same map again will noop.
     */
    fun setFontMap(fontMap: Map<String, Typeface>?) {
        if (fontMap === this.fontMap) {
            return
        }
        this.fontMap = fontMap
        invalidateSelf()
    }

    fun useTextGlyphs(): Boolean {
        return fontMap == null && textDelegate == null && composition!!.characters!!.size() > 0
    }

    fun cancelAnimation() {
        lazyCompositionTasks.clear()
        animator.cancel()
        if (!isVisible) {
            onVisibleAction = OnVisibleAction.NONE
        }
    }

    fun pauseAnimation() {
        lazyCompositionTasks.clear()
        animator.pauseAnimation()
        if (!isVisible) {
            onVisibleAction = OnVisibleAction.NONE
        }
    }

    @get:FloatRange(from = 0.0, to = 1.0)
    var progress: Float = 0f
        get() = animator.animatedValueAbsolute
        set(progress) {
            if (composition == null) {
                lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> field = progress })
                return
            }
            if (isTraceEnabled()) {
                beginSection("Drawable#setProgress")
            }
            animator.setFrame(composition!!.getFrameForProgress(progress))
            if (isTraceEnabled()) {
                endSection("Drawable#setProgress")
            }
        }

    override fun getIntrinsicWidth(): Int {
        return if (composition == null) -1 else composition!!.bounds!!.width()
    }

    override fun getIntrinsicHeight(): Int {
        return if (composition == null) -1 else composition!!.bounds!!.height()
    }

    /**
     * Takes a [KeyPath], potentially with wildcards or globstars and resolve it to a list of
     * zero or more actual [Keypaths][KeyPath] that exist in the current animation.
     *
     *
     * If you want to set value callbacks for any of these values, it is recommend to use the
     * returned [KeyPath] objects because they will be internally resolved to their content
     * and won't trigger a tree walk of the animation contents when applied.
     */
    fun resolveKeyPath(keyPath: KeyPath): List<KeyPath> {
        if (compositionLayer == null) {
            Logger.warning("Cannot resolve KeyPath. Composition is not set yet.")
            return emptyList()
        }
        val keyPaths: MutableList<KeyPath> = ArrayList()
        compositionLayer!!.resolveKeyPath(keyPath, 0, keyPaths, KeyPath())
        return keyPaths
    }

    /**
     * Add an property callback for the specified [KeyPath]. This [KeyPath] can resolve
     * to multiple contents. In that case, the callback's value will apply to all of them.
     *
     *
     * Internally, this will check if the [KeyPath] has already been resolved with
     * [.resolveKeyPath] and will resolve it if it hasn't.
     *
     *
     * Set the callback to null to clear it.
     */
    fun <T> addValueCallback(
        keyPath: KeyPath, property: T, callback: LottieValueCallback<T>?
    ) {
        if (compositionLayer == null) {
            lazyCompositionTasks.add(LazyCompositionTask { c: LottieComposition? -> addValueCallback(keyPath, property, callback) })
            return
        }
        val invalidate: Boolean
        if (keyPath === KeyPath.COMPOSITION) {
            compositionLayer!!.addValueCallback(property, callback)
            invalidate = true
        } else if (keyPath.resolvedElement != null) {
            keyPath.resolvedElement!!.addValueCallback(property, callback)
            invalidate = true
        } else {
            val elements = resolveKeyPath(keyPath)

            for (i in elements.indices) {
                elements[i].resolvedElement!!.addValueCallback(property, callback)
            }
            invalidate = !elements.isEmpty()
        }
        if (invalidate) {
            invalidateSelf()
            if (property == LottieProperty.TIME_REMAP) {
                // Time remapping values are read in setProgress. In order for the new value
                // to apply, we have to re-set the progress with the current progress so that the
                // time remapping can be reapplied.
                progress = progress
            }
        }
    }

    /**
     * Overload of [.addValueCallback] that takes an interface. This allows you to use a single abstract
     * method code block in Kotlin such as:
     * drawable.addValueCallback(yourKeyPath, LottieProperty.COLOR) { yourColor }
     */
    fun <T> addValueCallback(
        keyPath: KeyPath, property: T,
        callback: SimpleLottieValueCallback<T>
    ) {
        addValueCallback(keyPath, property, object : LottieValueCallback<T>() {
            override fun getValue(frameInfo: LottieFrameInfo<T>): T? {
                return callback.getValue(frameInfo)
            }
        })
    }


    /**
     * Allows you to modify or clear a bitmap that was loaded for an image either automatically
     * through [.setImagesAssetsFolder] or with an [ImageAssetDelegate].
     *
     * @return the previous Bitmap or null.
     */
    fun updateBitmap(id: String?, bitmap: Bitmap?): Bitmap? {
        val bm = getImageAssetManager()
        if (bm == null) {
            Logger.warning(
                "Cannot update bitmap. Most likely the drawable is not added to a View " +
                        "which prevents Lottie from getting a Context."
            )
            return null
        }
        val ret = bm.updateBitmap(id, bitmap)
        invalidateSelf()
        return ret
    }


    @Deprecated("use {@link #getBitmapForId(String)}.")
    fun getImageAsset(id: String?): Bitmap? {
        val bm = getImageAssetManager()
        if (bm != null) {
            return bm.bitmapForId(id)
        }
        val imageAsset = if (composition == null) null else composition!!.getImages()!![id!!]
        if (imageAsset != null) {
            return imageAsset.bitmap
        }
        return null
    }

    /**
     * Returns the bitmap that will be rendered for the given id in the Lottie animation file.
     * The id is the asset reference id stored in the "id" property of each object in the "assets" array.
     *
     *
     * The returned bitmap could be from:
     * * Embedded in the animation file as a base64 string.
     * * In the same directory as the animation file.
     * * In the same zip file as the animation file.
     * * Returned from an [ImageAssetDelegate].
     * or null if the image doesn't exist from any of those places.
     */
    fun getBitmapForId(id: String?): Bitmap? {
        val assetManager = getImageAssetManager()
        if (assetManager != null) {
            return assetManager.bitmapForId(id)
        }
        return null
    }

    /**
     * Returns the [LottieImageAsset] that will be rendered for the given id in the Lottie animation file.
     * The id is the asset reference id stored in the "id" property of each object in the "assets" array.
     *
     *
     * The returned bitmap could be from:
     * * Embedded in the animation file as a base64 string.
     * * In the same directory as the animation file.
     * * In the same zip file as the animation file.
     * * Returned from an [ImageAssetDelegate].
     * or null if the image doesn't exist from any of those places.
     */
    fun getLottieImageAssetForId(id: String?): LottieImageAsset? {
        val composition = this.composition ?: return null
        return composition.getImages()!![id!!]
    }

    private fun getImageAssetManager(): ImageAssetManager {
        if (imageAssetManager != null && !imageAssetManager!!.hasSameContext(context)) {
            imageAssetManager = null
        }

        if (imageAssetManager == null) {
            imageAssetManager = ImageAssetManager(
                callback,
                imageAssetsFolder, imageAssetDelegate, composition!!.getImages()
            )
        }

        return imageAssetManager!!
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    fun getTypeface(font: Font): Typeface? {
        val fontMap = this.fontMap
        if (fontMap != null) {
            var key = font.family
            if (fontMap.containsKey(key)) {
                return fontMap[key]
            }
            key = font.name
            if (fontMap.containsKey(key)) {
                return fontMap[key]
            }
            key = font.family + "-" + font.style
            if (fontMap.containsKey(key)) {
                return fontMap[key]
            }
        }

        val assetManager = getFontAssetManager()
        if (assetManager != null) {
            return assetManager.getTypeface(font)
        }
        return null
    }

    private fun getFontAssetManager(): FontAssetManager? {
        if (callback == null) {
            // We can't get a bitmap since we can't get a Context from the callback.
            return null
        }

        if (fontAssetManager == null) {
            fontAssetManager = FontAssetManager(callback!!, fontAssetDelegate)
            val defaultExtension = this.defaultFontFileExtension
            if (defaultExtension != null) {
                fontAssetManager!!.setDefaultFontFileExtension(defaultFontFileExtension)
            }
        }

        return fontAssetManager
    }

    private val context: Context?
        get() {
            val callback = callback ?: return null

            if (callback is View) {
                return callback.context
            }
            return null
        }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        // Sometimes, setVisible(false) gets called twice in a row. If we don't check wasNotVisibleAlready, we could
        // wind up clearing the onVisibleAction value for the second call.
        val wasNotVisibleAlready = !isVisible
        val ret = super.setVisible(visible, restart)

        if (visible) {
            if (onVisibleAction == OnVisibleAction.PLAY) {
                playAnimation()
            } else if (onVisibleAction == OnVisibleAction.RESUME) {
                resumeAnimation()
            }
        } else {
            if (animator.isRunning) {
                pauseAnimation()
                onVisibleAction = OnVisibleAction.RESUME
            } else if (!wasNotVisibleAlready) {
                onVisibleAction = OnVisibleAction.NONE
            }
        }
        return ret
    }

    /**
     * These Drawable.Callback methods proxy the calls so that this is the drawable that is
     * actually invalidated, not a child one which will not pass the view's validateDrawable check.
     */
    override fun invalidateDrawable(who: Drawable) {
        val callback = callback ?: return
        callback.invalidateDrawable(this)
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        val callback = callback ?: return
        callback.scheduleDrawable(this, what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        val callback = callback ?: return
        callback.unscheduleDrawable(this, what)
    }

    /**
     * Hardware accelerated render path.
     */
    private fun drawDirectlyToCanvas(canvas: Canvas) {
        val compositionLayer = this.compositionLayer
        val composition = this.composition
        if (compositionLayer == null || composition == null) {
            return
        }

        renderingMatrix.reset()
        val bounds = bounds
        if (!bounds.isEmpty) {
            // In fitXY mode, the scale doesn't take effect.
            val scaleX = bounds.width() / composition.bounds!!.width().toFloat()
            val scaleY = bounds.height() / composition.bounds!!.height().toFloat()

            renderingMatrix.preScale(scaleX, scaleY)
            renderingMatrix.preTranslate(bounds.left.toFloat(), bounds.top.toFloat())
        }
        compositionLayer.draw(canvas, renderingMatrix, alpha)
    }

    /**
     * Software accelerated render path.
     *
     *
     * This draws the animation to an internally managed bitmap and then draws the bitmap to the original canvas.
     *
     * @see LottieAnimationView.setRenderMode
     */

    private val emptyMatrix = Matrix()
    private fun renderAndDrawAsBitmap(originalCanvas: Canvas, compositionLayer: CompositionLayer?) {
        if (composition == null || compositionLayer == null) {
            return
        }
        ensureSoftwareRenderingObjectsInitialized()

        originalCanvas.getMatrix(softwareRenderingOriginalCanvasMatrix!!)

        // Get the canvas clip bounds and map it to the coordinate space of canvas with it's current transform.
        originalCanvas.getClipBounds(canvasClipBounds!!)
        convertRect(canvasClipBounds, canvasClipBoundsRectF)
        softwareRenderingOriginalCanvasMatrix!!.mapRect(canvasClipBoundsRectF)
        convertRect(canvasClipBoundsRectF, canvasClipBounds)

        if (clipToCompositionBounds) {
            // Start with the intrinsic bounds. This will later be unioned with the clip bounds to find the
            // smallest possible render area.
            softwareRenderingTransformedBounds!![0f, 0f, intrinsicWidth.toFloat()] = intrinsicHeight.toFloat()
        } else {
            // Calculate the full bounds of the animation.
            compositionLayer.getBounds(softwareRenderingTransformedBounds!!, emptyMatrix, false)
        }
        // Transform the animation bounds to the bounds that they will render to on the canvas.
        softwareRenderingOriginalCanvasMatrix!!.mapRect(softwareRenderingTransformedBounds)

        // The bounds are usually intrinsicWidth x intrinsicHeight. If they are different, an external source is scaling this drawable.
        // This is how ImageView.ScaleType.FIT_XY works.
        val bounds = bounds
        val scaleX = bounds.width() / intrinsicWidth.toFloat()
        val scaleY = bounds.height() / intrinsicHeight.toFloat()
        scaleRect(softwareRenderingTransformedBounds, scaleX, scaleY)

        if (!ignoreCanvasClipBounds()) {
            softwareRenderingTransformedBounds!!.intersect(
                canvasClipBounds!!.left.toFloat(),
                canvasClipBounds!!.top.toFloat(),
                canvasClipBounds!!.right.toFloat(),
                canvasClipBounds!!.bottom.toFloat()
            )
        }

        val renderWidth = ceil(softwareRenderingTransformedBounds!!.width().toDouble()).toInt()
        val renderHeight = ceil(softwareRenderingTransformedBounds!!.height().toDouble()).toInt()

        if (renderWidth <= 0 || renderHeight <= 0) {
            return
        }

        ensureSoftwareRenderingBitmap(renderWidth, renderHeight)

        if (isDirty) {
            renderingMatrix.set(softwareRenderingOriginalCanvasMatrix)
            renderingMatrix.preScale(scaleX, scaleY)
            // We want to render the smallest bitmap possible. If the animation doesn't start at the top left, we translate the canvas and shrink the
            // bitmap to avoid allocating and copying the empty space on the left and top. renderWidth and renderHeight take this into account.
            renderingMatrix.postTranslate(-softwareRenderingTransformedBounds!!.left, -softwareRenderingTransformedBounds!!.top)

            softwareRenderingBitmap!!.eraseColor(0)
            compositionLayer.draw(softwareRenderingCanvas!!, renderingMatrix, alpha)

            // Calculate the dst bounds.
            // We need to map the rendered coordinates back to the canvas's coordinates. To do so, we need to invert the transform
            // of the original canvas.
            // Take the bounds of the rendered animation and map them to the canvas's coordinates.
            // This is similar to the src rect above but the src bound may have a left and top offset.
            softwareRenderingOriginalCanvasMatrix!!.invert(softwareRenderingOriginalCanvasMatrixInverse)
            softwareRenderingOriginalCanvasMatrixInverse!!.mapRect(softwareRenderingDstBoundsRectF, softwareRenderingTransformedBounds)
            convertRect(softwareRenderingDstBoundsRectF, softwareRenderingDstBoundsRect)
        }

        softwareRenderingSrcBoundsRect!![0, 0, renderWidth] = renderHeight
        originalCanvas.drawBitmap(softwareRenderingBitmap!!, softwareRenderingSrcBoundsRect, softwareRenderingDstBoundsRect!!, softwareRenderingPaint)
    }

    private fun ensureSoftwareRenderingObjectsInitialized() {
        if (softwareRenderingCanvas != null) {
            return
        }
        softwareRenderingCanvas = Canvas()
        softwareRenderingTransformedBounds = RectF()
        softwareRenderingOriginalCanvasMatrix = Matrix()
        softwareRenderingOriginalCanvasMatrixInverse = Matrix()
        canvasClipBounds = Rect()
        canvasClipBoundsRectF = RectF()
        softwareRenderingPaint = LPaint()
        softwareRenderingSrcBoundsRect = Rect()
        softwareRenderingDstBoundsRect = Rect()
        softwareRenderingDstBoundsRectF = RectF()
    }

    private fun ensureSoftwareRenderingBitmap(renderWidth: Int, renderHeight: Int) {
        if (softwareRenderingBitmap == null || softwareRenderingBitmap!!.width < renderWidth || softwareRenderingBitmap!!.height < renderHeight) {
            // The bitmap is larger. We need to create a new one.
            softwareRenderingBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
            softwareRenderingCanvas!!.setBitmap(softwareRenderingBitmap)
            isDirty = true
        } else if (softwareRenderingBitmap!!.width > renderWidth || softwareRenderingBitmap!!.height > renderHeight) {
            // The bitmap is smaller. Take subset of the original.
            softwareRenderingBitmap = Bitmap.createBitmap(softwareRenderingBitmap!!, 0, 0, renderWidth, renderHeight)
            softwareRenderingCanvas!!.setBitmap(softwareRenderingBitmap)
            isDirty = true
        }
    }

    /**
     * Convert a RectF to a Rect
     */
    private fun convertRect(src: RectF?, dst: Rect?) {
        dst!![floor(src!!.left.toDouble()).toInt(), floor(src.top.toDouble()).toInt(), ceil(src.right.toDouble()).toInt()] = ceil(
            src.bottom.toDouble()
        ).toInt()
    }

    /**
     * Convert a Rect to a RectF
     */
    private fun convertRect(src: Rect?, dst: RectF?) {
        dst!![src!!.left.toFloat(), src.top.toFloat(), src.right.toFloat()] = src.bottom.toFloat()
    }

    private fun scaleRect(rect: RectF?, scaleX: Float, scaleY: Float) {
        rect!![rect.left * scaleX, rect.top * scaleY, rect.right * scaleX] = rect.bottom * scaleY
    }

    /**
     * When a View's parent has clipChildren set to false, it doesn't affect the clipBound
     * of its child canvases so we should explicitly check for it and draw the full animation
     * bounds instead.
     */
    private fun ignoreCanvasClipBounds(): Boolean {
        val callback = callback as? View
            ?: // If the callback isn't a view then respect the canvas's clip bounds.
            return false
        val parent = callback.parent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && parent is ViewGroup) {
            return !parent.clipChildren
        }
        // Unlikely to ever happen. If the callback is a View, its parent should be a ViewGroup.
        return false
    }

    companion object {
        /**
         * Prior to Oreo, you could only call invalidateDrawable() from the main thread.
         * This means that when async updates are enabled, we must post the invalidate call to the main thread.
         * Newer devices can call invalidate directly from whatever thread asyncUpdates runs on.
         */
        private val invalidateSelfOnMainThread = Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1

        /**
         * The marker to use if "reduced motion" is enabled.
         * Supported marker names are case insensitive, and include:
         * - reduced motion
         * - reducedMotion
         * - reduced_motion
         * - reduced-motion
         */
        private val ALLOWED_REDUCED_MOTION_MARKERS: List<String> = mutableListOf(
            "reduced motion",
            "reduced_motion",
            "reduced-motion",
            "reducedmotion"
        )

        /**
         * The executor that [AsyncUpdates] will be run on.
         *
         *
         * Defaults to a core size of 0 so that when no animations are playing, there will be no
         * idle cores consuming resources.
         *
         *
         * Allows up to two active threads so that if there are many animations, they can all work in parallel.
         * Two was arbitrarily chosen but should be sufficient for most uses cases. In the case of a single
         * animation, this should never exceed one.
         *
         *
         * Each thread will timeout after 35ms which gives it enough time to persist for one frame, one dropped frame
         * and a few extra ms just in case.
         */
        private val setProgressExecutor: Executor = ThreadPoolExecutor(
            0, 2, 35, TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(), LottieThreadFactory()
        )
        private const val MAX_DELTA_MS_ASYNC_SET_PROGRESS = 3 / 60f * 1000

        /**
         * When the animation reaches the end and `repeatCount` is INFINITE
         * or a positive value, the animation restarts from the beginning.
         */
        const val RESTART: Int = ValueAnimator.RESTART

        /**
         * When the animation reaches the end and `repeatCount` is INFINITE
         * or a positive value, the animation reverses direction on every iteration.
         */
        const val REVERSE: Int = ValueAnimator.REVERSE

        /**
         * This value used used with the [.setRepeatCount] property to repeat
         * the animation indefinitely.
         */
        const val INFINITE: Int = ValueAnimator.INFINITE
    }
}
