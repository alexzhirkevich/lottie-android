package com.airbnb.lottie

import android.animation.Animator
import android.animation.Animator.AnimatorPauseListener
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorFilter
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.FloatRange
import androidx.annotation.MainThread
import androidx.annotation.RawRes
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory.fromAsset
import com.airbnb.lottie.LottieCompositionFactory.fromAssetSync
import com.airbnb.lottie.LottieCompositionFactory.fromJsonInputStream
import com.airbnb.lottie.LottieCompositionFactory.fromRawRes
import com.airbnb.lottie.LottieCompositionFactory.fromRawResSync
import com.airbnb.lottie.LottieCompositionFactory.fromUrl
import com.airbnb.lottie.LottieCompositionFactory.fromZipStream
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.utils.Logger
import com.airbnb.lottie.utils.Utils
import com.airbnb.lottie.value.LottieFrameInfo
import com.airbnb.lottie.value.LottieValueCallback
import com.airbnb.lottie.value.SimpleLottieValueCallback
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import java.util.zip.ZipInputStream

/**
 * This view will load, deserialize, and display an After Effects animation exported with
 * bodymovin ([github.com/airbnb/lottie-web](https://github.com/airbnb/lottie-web)).
 *
 *
 * You may set the animation in one of two ways:
 * 1) Attrs: [R.styleable.LottieAnimationView_lottie_fileName]
 * 2) Programmatically:
 * [.setAnimation]
 * [.setAnimation]
 * [.setAnimation]
 * [.setAnimationFromJson]
 * [.setAnimationFromUrl]
 * [.setComposition]
 *
 *
 * You can set a default cache strategy with [R.attr.lottie_cacheComposition].
 *
 *
 * You can manually set the progress of the animation with [.setProgress] or
 * [R.attr.lottie_progress]
 *
 * @see [Full Documentation](http://airbnb.io/lottie)
 */
@Suppress("unused")
open class LottieAnimationView : AppCompatImageView {
    private val loadedListener: LottieListener<LottieComposition> = WeakSuccessListener(this)

    private class WeakSuccessListener(target: LottieAnimationView) : LottieListener<LottieComposition> {
        private val targetReference = WeakReference(target)

        override fun onResult(result: LottieComposition) {
            val targetView = targetReference.get() ?: return
            targetView.composition = result
        }
    }

    private val wrappedFailureListener: LottieListener<Throwable?> = WeakFailureListener(this)

    private class WeakFailureListener(target: LottieAnimationView) : LottieListener<Throwable?> {
        private val targetReference = WeakReference(target)

        override fun onResult(result: Throwable?) {
            val targetView = targetReference.get() ?: return

            if (targetView.fallbackResource != 0) {
                targetView.setImageResource(targetView.fallbackResource)
            }
            val l = if (targetView.failureListener == null) DEFAULT_FAILURE_LISTENER else targetView.failureListener!!
            l.onResult(result)
        }
    }

    private var failureListener: LottieListener<Throwable?>? = null

    @DrawableRes
    private var fallbackResource = 0

    private val lottieDrawable = LottieDrawable()
    private var animationName: String? = null

    @RawRes
    private var animationResId = 0

    /**
     * When we set a new composition, we set LottieDrawable to null then back again so that ImageView re-checks its bounds.
     * However, this causes the drawable to get unscheduled briefly. Normally, we would pause the animation but in this case, we don't want to.
     */
    private var ignoreUnschedule = false

    private var autoPlay = false
    private var cacheComposition = true

    /**
     * Keeps track of explicit user actions taken and prevents onRestoreInstanceState from overwriting already set values.
     */
    private val userActionsTaken: MutableSet<UserActionTaken> = HashSet()
    private val lottieOnCompositionLoadedListeners: MutableSet<LottieOnCompositionLoadedListener> = HashSet()

    private var compositionTask: LottieTask<LottieComposition>? = null

    constructor(context: Context?) : super(context!!) {
        init(null, R.attr.lottieAnimationViewStyle)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        init(attrs, R.attr.lottieAnimationViewStyle)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr) {
        init(attrs, defStyleAttr)
    }

    private fun init(attrs: AttributeSet?, @AttrRes defStyleAttr: Int) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LottieAnimationView, defStyleAttr, 0)
        cacheComposition = ta.getBoolean(R.styleable.LottieAnimationView_lottie_cacheComposition, true)
        val hasRawRes = ta.hasValue(R.styleable.LottieAnimationView_lottie_rawRes)
        val hasFileName = ta.hasValue(R.styleable.LottieAnimationView_lottie_fileName)
        val hasUrl = ta.hasValue(R.styleable.LottieAnimationView_lottie_url)
        require(!(hasRawRes && hasFileName)) {
            "lottie_rawRes and lottie_fileName cannot be used at " +
                    "the same time. Please use only one at once."
        }
        if (hasRawRes) {
            val rawResId = ta.getResourceId(R.styleable.LottieAnimationView_lottie_rawRes, 0)
            if (rawResId != 0) {
                setAnimation(rawResId)
            }
        } else if (hasFileName) {
            val fileName = ta.getString(R.styleable.LottieAnimationView_lottie_fileName)
            if (fileName != null) {
                setAnimation(fileName)
            }
        } else if (hasUrl) {
            val url = ta.getString(R.styleable.LottieAnimationView_lottie_url)
            if (url != null) {
                setAnimationFromUrl(url)
            }
        }

        setFallbackResource(ta.getResourceId(R.styleable.LottieAnimationView_lottie_fallbackRes, 0))
        if (ta.getBoolean(R.styleable.LottieAnimationView_lottie_autoPlay, false)) {
            autoPlay = true
        }

        if (ta.getBoolean(R.styleable.LottieAnimationView_lottie_loop, false)) {
            lottieDrawable.repeatCount = LottieDrawable.INFINITE
        }

        if (ta.hasValue(R.styleable.LottieAnimationView_lottie_repeatMode)) {
            repeatMode = ta.getInt(
                R.styleable.LottieAnimationView_lottie_repeatMode,
                LottieDrawable.RESTART
            )
        }

        if (ta.hasValue(R.styleable.LottieAnimationView_lottie_repeatCount)) {
            repeatCount = ta.getInt(
                R.styleable.LottieAnimationView_lottie_repeatCount,
                LottieDrawable.INFINITE
            )
        }

        if (ta.hasValue(R.styleable.LottieAnimationView_lottie_speed)) {
            speed = ta.getFloat(R.styleable.LottieAnimationView_lottie_speed, 1f)
        }

        if (ta.hasValue(R.styleable.LottieAnimationView_lottie_clipToCompositionBounds)) {
            clipToCompositionBounds = ta.getBoolean(R.styleable.LottieAnimationView_lottie_clipToCompositionBounds, true)
        }

        if (ta.hasValue(R.styleable.LottieAnimationView_lottie_clipTextToBoundingBox)) {
            clipTextToBoundingBox = ta.getBoolean(R.styleable.LottieAnimationView_lottie_clipTextToBoundingBox, false)
        }

        if (ta.hasValue(R.styleable.LottieAnimationView_lottie_defaultFontFileExtension)) {
            setDefaultFontFileExtension(ta.getString(R.styleable.LottieAnimationView_lottie_defaultFontFileExtension))
        }

        imageAssetsFolder = ta.getString(R.styleable.LottieAnimationView_lottie_imageAssetsFolder)

        val hasProgress = ta.hasValue(R.styleable.LottieAnimationView_lottie_progress)
        setProgressInternal(ta.getFloat(R.styleable.LottieAnimationView_lottie_progress, 0f), hasProgress)

        enableMergePathsForKitKatAndAbove(
            ta.getBoolean(
                R.styleable.LottieAnimationView_lottie_enableMergePathsForKitKatAndAbove, false
            )
        )
        if (ta.hasValue(R.styleable.LottieAnimationView_lottie_colorFilter)) {
            val colorRes = ta.getResourceId(R.styleable.LottieAnimationView_lottie_colorFilter, -1)
            val csl = AppCompatResources.getColorStateList(context, colorRes)
            val filter = SimpleColorFilter(csl.defaultColor)
            val keyPath = KeyPath("**")
            val callback = LottieValueCallback<ColorFilter>(filter)
            addValueCallback(keyPath, LottieProperty.COLOR_FILTER, callback)
        }

        if (ta.hasValue(R.styleable.LottieAnimationView_lottie_renderMode)) {
            var renderModeOrdinal = ta.getInt(R.styleable.LottieAnimationView_lottie_renderMode, RenderMode.AUTOMATIC.ordinal)
            if (renderModeOrdinal >= RenderMode.entries.size) {
                renderModeOrdinal = RenderMode.AUTOMATIC.ordinal
            }
            renderMode = RenderMode.entries[renderModeOrdinal]
        }

        if (ta.hasValue(R.styleable.LottieAnimationView_lottie_asyncUpdates)) {
            var asyncUpdatesOrdinal = ta.getInt(R.styleable.LottieAnimationView_lottie_asyncUpdates, AsyncUpdates.AUTOMATIC.ordinal)
            if (asyncUpdatesOrdinal >= RenderMode.entries.size) {
                asyncUpdatesOrdinal = AsyncUpdates.AUTOMATIC.ordinal
            }
            asyncUpdates = AsyncUpdates.entries[asyncUpdatesOrdinal]
        }

        setIgnoreDisabledSystemAnimations(
            ta.getBoolean(
                R.styleable.LottieAnimationView_lottie_ignoreDisabledSystemAnimations,
                false
            )
        )

        if (ta.hasValue(R.styleable.LottieAnimationView_lottie_useCompositionFrameRate)) {
            setUseCompositionFrameRate(ta.getBoolean(R.styleable.LottieAnimationView_lottie_useCompositionFrameRate, false))
        }

        ta.recycle()

        lottieDrawable.setSystemAnimationsAreEnabled(Utils.getAnimationScale(context) != 0f)
    }

    override fun setImageResource(resId: Int) {
        this.animationResId = 0
        animationName = null
        cancelLoaderTask()
        super.setImageResource(resId)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        this.animationResId = 0
        animationName = null
        cancelLoaderTask()
        super.setImageDrawable(drawable)
    }

    override fun setImageBitmap(bm: Bitmap) {
        this.animationResId = 0
        animationName = null
        cancelLoaderTask()
        super.setImageBitmap(bm)
    }

    override fun unscheduleDrawable(who: Drawable) {
        if (!ignoreUnschedule && who === lottieDrawable && lottieDrawable.isAnimating) {
            pauseAnimation()
        } else if (!ignoreUnschedule && who is LottieDrawable && who.isAnimating) {
            who.pauseAnimation()
        }
        super.unscheduleDrawable(who)
    }

    override fun invalidate() {
        super.invalidate()
        val d = drawable
        if (d is LottieDrawable && d.getRenderMode() == RenderMode.SOFTWARE) {
            // This normally isn't needed. However, when using software rendering, Lottie caches rendered bitmaps
            // and updates it when the animation changes internally.
            // If you have dynamic properties with a value callback and want to update the value of the dynamic property, you need a way
            // to tell Lottie that the bitmap is dirty and it needs to be re-rendered. Normal drawables always re-draw the actual shapes
            // so this isn't an issue but for this path, we have to take the extra step of setting the dirty flag.
            lottieDrawable.invalidateSelf()
        }
    }

    override fun invalidateDrawable(dr: Drawable) {
        if (drawable === lottieDrawable) {
            // We always want to invalidate the root drawable so it redraws the whole drawable.
            // Eventually it would be great to be able to invalidate just the changed region.
            super.invalidateDrawable(lottieDrawable)
        } else {
            // Otherwise work as regular ImageView
            super.invalidateDrawable(dr)
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        ss.animationName = animationName
        ss.animationResId = animationResId
        ss.progress = lottieDrawable.progress
        ss.isAnimating = lottieDrawable.isAnimatingOrWillAnimateOnVisible
        ss.imageAssetsFolder = lottieDrawable.imageAssetsFolder
        ss.repeatMode = lottieDrawable.repeatMode
        ss.repeatCount = lottieDrawable.repeatCount
        return ss
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        val ss = state
        super.onRestoreInstanceState(ss.superState)
        animationName = ss.animationName
        if (!userActionsTaken.contains(UserActionTaken.SET_ANIMATION) && !TextUtils.isEmpty(animationName)) {
            setAnimation(animationName.orEmpty())
        }
        animationResId = ss.animationResId
        if (!userActionsTaken.contains(UserActionTaken.SET_ANIMATION) && animationResId != 0) {
            setAnimation(animationResId)
        }
        if (!userActionsTaken.contains(UserActionTaken.SET_PROGRESS)) {
            setProgressInternal(ss.progress, false)
        }
        if (!userActionsTaken.contains(UserActionTaken.PLAY_OPTION) && ss.isAnimating) {
            playAnimation()
        }
        if (!userActionsTaken.contains(UserActionTaken.SET_IMAGE_ASSETS)) {
            imageAssetsFolder = ss.imageAssetsFolder
        }
        if (!userActionsTaken.contains(UserActionTaken.SET_REPEAT_MODE)) {
            repeatMode = ss.repeatMode
        }
        if (!userActionsTaken.contains(UserActionTaken.SET_REPEAT_COUNT)) {
            repeatCount = ss.repeatCount
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode && autoPlay) {
            lottieDrawable.playAnimation()
        }
    }

    /**
     * Allows ignoring system animations settings, therefore allowing animations to run even if they are disabled.
     *
     *
     * Defaults to false.
     *
     * @param ignore if true animations will run even when they are disabled in the system settings.
     */
    fun setIgnoreDisabledSystemAnimations(ignore: Boolean) {
        lottieDrawable.setIgnoreDisabledSystemAnimations(ignore)
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
        lottieDrawable.setUseCompositionFrameRate(useCompositionFrameRate)
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
        lottieDrawable.enableMergePathsForKitKatAndAbove(enable)
    }

    val isMergePathsEnabledForKitKatAndAbove: Boolean
        /**
         * Returns whether merge paths are enabled for KitKat and above.
         */
        get() = lottieDrawable.isMergePathsEnabledForKitKatAndAbove

    var clipToCompositionBounds: Boolean
        /**
         * Gets whether or not Lottie should clip to the original animation composition bounds.
         *
         *
         * Defaults to true.
         */
        get() = lottieDrawable.getClipToCompositionBounds()
        /**
         * Sets whether or not Lottie should clip to the original animation composition bounds.
         *
         *
         * When set to true, the parent view may need to disable clipChildren so Lottie can render outside of the LottieAnimationView bounds.
         *
         *
         * Defaults to true.
         */
        set(clipToCompositionBounds) {
            lottieDrawable.setClipToCompositionBounds(clipToCompositionBounds)
        }

    /**
     * If set to true, all future compositions that are set will be cached so that they don't need to be parsed
     * next time they are loaded. This won't apply to compositions that have already been loaded.
     *
     *
     * Defaults to true.
     *
     *
     * [R.attr.lottie_cacheComposition]
     */
    fun setCacheComposition(cacheComposition: Boolean) {
        this.cacheComposition = cacheComposition
    }

    /**
     * Enable this to debug slow animations by outlining masks and mattes. The performance overhead of the masks and mattes will
     * be proportional to the surface area of all of the masks/mattes combined.
     *
     *
     * DO NOT leave this enabled in production.
     */
    fun setOutlineMasksAndMattes(outline: Boolean) {
        lottieDrawable.setOutlineMasksAndMattes(outline)
    }

    /**
     * Sets the animation from a file in the raw directory.
     * This will load and deserialize the file asynchronously.
     */
    fun setAnimation(@RawRes rawRes: Int) {
        this.animationResId = rawRes
        animationName = null
        setCompositionTask(fromRawRes(rawRes))
    }


    private fun fromRawRes(@RawRes rawRes: Int): LottieTask<LottieComposition> {
        return if (isInEditMode) {
            LottieTask(Callable {
                if (cacheComposition
                ) fromRawResSync(context, rawRes) else fromRawResSync(context, rawRes, null)
            }, true)
        } else {
            if (cacheComposition) fromRawRes(context, rawRes) else fromRawRes(context, rawRes, null)
        }
    }

    fun setAnimation(assetName: String) {
        this.animationName = assetName
        animationResId = 0
        setCompositionTask(fromAssets(assetName))
    }

    private fun fromAssets(assetName: String): LottieTask<LottieComposition>? {
        return if (isInEditMode) {
            LottieTask(Callable {
                if (cacheComposition) fromAssetSync(
                    context,
                    assetName!!
                ) else fromAssetSync(context, assetName, null)
            }, true)
        } else {
            if (cacheComposition) fromAsset(context, assetName) else fromAsset(
                context,
                assetName,
                null
            )
        }
    }

    /**
     * @see .setAnimationFromJson
     */
    @Deprecated("")
    fun setAnimationFromJson(jsonString: String) {
        setAnimationFromJson(jsonString, null)
    }

    /**
     * Sets the animation from json string. This is the ideal API to use when loading an animation
     * over the network because you can use the raw response body here and a conversion to a
     * JSONObject never has to be done.
     */
    fun setAnimationFromJson(jsonString: String, cacheKey: String?) {
        setAnimation(ByteArrayInputStream(jsonString.toByteArray()), cacheKey)
    }

    /**
     * Sets the animation from an arbitrary InputStream.
     * This will load and deserialize the file asynchronously.
     *
     *
     * If this is a Zip file, wrap your InputStream with a ZipInputStream to use the overload
     * designed for zip files.
     *
     *
     * This is particularly useful for animations loaded from the network. You can fetch the
     * bodymovin json from the network and pass it directly here.
     *
     *
     * Auto-closes the stream.
     */
    fun setAnimation(stream: InputStream, cacheKey: String?) {
        setCompositionTask(fromJsonInputStream(stream, cacheKey))
    }

    /**
     * Sets the animation from a ZipInputStream.
     * This will load and deserialize the file asynchronously.
     *
     *
     * This is particularly useful for animations loaded from the network. You can fetch the
     * bodymovin json from the network and pass it directly here.
     *
     *
     * Auto-closes the stream.
     */
    fun setAnimation(stream: ZipInputStream?, cacheKey: String?) {
        setCompositionTask(fromZipStream(stream!!, cacheKey))
    }

    /**
     * Load a lottie animation from a url. The url can be a json file or a zip file. Use a zip file if you have images. Simply zip them together and
     * lottie
     * will unzip and link the images automatically.
     *
     *
     * Under the hood, Lottie uses Java HttpURLConnection because it doesn't require any transitive networking dependencies. It will download the file
     * to the application cache under a temporary name. If the file successfully parses to a composition, it will rename the temporary file to one that
     * can be accessed immediately for subsequent requests. If the file does not parse to a composition, the temporary file will be deleted.
     *
     *
     * You can replace the default network stack or cache handling with a global [LottieConfig]
     *
     * @see LottieConfig.Builder
     *
     * @see Lottie.initialize
     */
    fun setAnimationFromUrl(url: String?) {
        val task = if (cacheComposition) fromUrl(context, url!!) else fromUrl(context, url!!, null)
        setCompositionTask(task)
    }

    /**
     * Load a lottie animation from a url. The url can be a json file or a zip file. Use a zip file if you have images. Simply zip them together and
     * lottie
     * will unzip and link the images automatically.
     *
     *
     * Under the hood, Lottie uses Java HttpURLConnection because it doesn't require any transitive networking dependencies. It will download the file
     * to the application cache under a temporary name. If the file successfully parses to a composition, it will rename the temporary file to one that
     * can be accessed immediately for subsequent requests. If the file does not parse to a composition, the temporary file will be deleted.
     *
     *
     * You can replace the default network stack or cache handling with a global [LottieConfig]
     *
     * @see LottieConfig.Builder
     *
     * @see Lottie.initialize
     */
    fun setAnimationFromUrl(url: String?, cacheKey: String?) {
        val task = fromUrl(context, url!!, cacheKey)
        setCompositionTask(task)
    }

    /**
     * Set a default failure listener that will be called if any of the setAnimation APIs fail for any reason.
     * This can be used to replace the default behavior.
     *
     *
     * The default behavior will log any network errors and rethrow all other exceptions.
     *
     *
     * If you are loading an animation from the network, errors may occur if your user has no internet.
     * You can use this listener to retry the download or you can have it default to an error drawable
     * with [.setFallbackResource].
     *
     *
     * Unless you are using [.setAnimationFromUrl], errors are unexpected.
     *
     *
     * Set the listener to null to revert to the default behavior.
     */
    fun setFailureListener(failureListener: LottieListener<Throwable?>?) {
        this.failureListener = failureListener
    }

    /**
     * Set a drawable that will be rendered if the LottieComposition fails to load for any reason.
     * Unless you are using [.setAnimationFromUrl], this is an unexpected error and
     * you should handle it with [.setFailureListener].
     *
     *
     * If this is a network animation, you may use this to show an error to the user or
     * you can use a failure listener to retry the download.
     */
    fun setFallbackResource(@DrawableRes fallbackResource: Int) {
        this.fallbackResource = fallbackResource
    }

    private fun setCompositionTask(compositionTask: LottieTask<LottieComposition>?) {
        val result = compositionTask!!.getResult()
        val lottieDrawable = this.lottieDrawable
        if (result != null && lottieDrawable == drawable && lottieDrawable.composition == result.value) {
            return
        }
        userActionsTaken.add(UserActionTaken.SET_ANIMATION)
        clearComposition()
        cancelLoaderTask()
        this.compositionTask = compositionTask
            .addListener(loadedListener)
            .addFailureListener(wrappedFailureListener)
    }

    private fun cancelLoaderTask() {
        if (compositionTask != null) {
            compositionTask!!.removeListener(loadedListener)
            compositionTask!!.removeFailureListener(wrappedFailureListener)
        }
    }

    var composition: LottieComposition?
        get() = if (drawable === lottieDrawable) lottieDrawable.composition else null
        /**
         * Sets a composition.
         * You can set a default cache strategy if this view was inflated with xml by
         * using [R.attr.lottie_cacheComposition].
         */
        set(composition) {
            if (L.DBG) {
                Log.v(TAG, "Set Composition \n$composition")
            }
            lottieDrawable.callback = this

            ignoreUnschedule = true
            val isNewComposition = lottieDrawable.setComposition(composition!!)
            if (autoPlay) {
                lottieDrawable.playAnimation()
            }
            ignoreUnschedule = false
            if (drawable === lottieDrawable && !isNewComposition) {
                // We can avoid re-setting the drawable, and invalidating the view, since the composition
                // hasn't changed.
                return
            } else if (!isNewComposition) {
                // The current drawable isn't lottieDrawable but the drawable already has the right composition.
                setLottieDrawable()
            }

            // This is needed to makes sure that the animation is properly played/paused for the current visibility state.
            // It is possible that the drawable had a lazy composition task to play the animation but this view subsequently
            // became invisible. Comment this out and run the espresso tests to see a failing test.
            onVisibilityChanged(this, visibility)

            requestLayout()

            for (lottieOnCompositionLoadedListener in lottieOnCompositionLoadedListeners) {
                lottieOnCompositionLoadedListener.onCompositionLoaded(composition)
            }
        }

    /**
     * Returns whether or not any layers in this composition has masks.
     */
    fun hasMasks(): Boolean {
        return lottieDrawable.hasMasks()
    }

    /**
     * Returns whether or not any layers in this composition has a matte layer.
     */
    fun hasMatte(): Boolean {
        return lottieDrawable.hasMatte()
    }

    /**
     * Plays the animation from the beginning. If speed is &lt; 0, it will start at the end
     * and play towards the beginning
     */
    @MainThread
    fun playAnimation() {
        userActionsTaken.add(UserActionTaken.PLAY_OPTION)
        lottieDrawable.playAnimation()
    }

    /**
     * Continues playing the animation from its current position. If speed &lt; 0, it will play backwards
     * from the current position.
     */
    @MainThread
    fun resumeAnimation() {
        userActionsTaken.add(UserActionTaken.PLAY_OPTION)
        lottieDrawable.resumeAnimation()
    }

    /**
     * Sets the minimum frame that the animation will start from when playing or looping.
     */
    fun setMinFrame(startFrame: Int) {
        lottieDrawable.setMinFrame(startFrame)
    }

    val minFrame: Float
        /**
         * Returns the minimum frame set by [.setMinFrame] or [.setMinProgress]
         */
        get() = lottieDrawable.minFrame

    /**
     * Sets the minimum progress that the animation will start from when playing or looping.
     */
    fun setMinProgress(startProgress: Float) {
        lottieDrawable.setMinProgress(startProgress)
    }

    /**
     * Sets the maximum frame that the animation will end at when playing or looping.
     *
     *
     * The value will be clamped to the composition bounds. For example, setting Integer.MAX_VALUE would result in the same
     * thing as composition.endFrame.
     */
    fun setMaxFrame(endFrame: Int) {
        lottieDrawable.setMaxFrame(endFrame)
    }

    val maxFrame: Float
        /**
         * Returns the maximum frame set by [.setMaxFrame] or [.setMaxProgress]
         */
        get() = lottieDrawable.maxFrame

    /**
     * Sets the maximum progress that the animation will end at when playing or looping.
     */
    fun setMaxProgress(@FloatRange(from = 0.0, to = 1.0) endProgress: Float) {
        lottieDrawable.setMaxProgress(endProgress)
    }

    /**
     * Sets the minimum frame to the start time of the specified marker.
     *
     * @throws IllegalArgumentException if the marker is not found.
     */
    fun setMinFrame(markerName: String?) {
        lottieDrawable.setMinFrame(markerName!!)
    }

    /**
     * Sets the maximum frame to the start time + duration of the specified marker.
     *
     * @throws IllegalArgumentException if the marker is not found.
     */
    fun setMaxFrame(markerName: String?) {
        lottieDrawable.setMaxFrame(markerName!!)
    }

    /**
     * Sets the minimum and maximum frame to the start time and start time + duration
     * of the specified marker.
     *
     * @throws IllegalArgumentException if the marker is not found.
     */
    fun setMinAndMaxFrame(markerName: String?) {
        lottieDrawable.setMinAndMaxFrame(markerName!!)
    }

    /**
     * Sets the minimum and maximum frame to the start marker start and the maximum frame to the end marker start.
     * playEndMarkerStartFrame determines whether or not to play the frame that the end marker is on. If the end marker
     * represents the end of the section that you want, it should be true. If the marker represents the beginning of the
     * next section, it should be false.
     *
     * @throws IllegalArgumentException if either marker is not found.
     */
    fun setMinAndMaxFrame(startMarkerName: String?, endMarkerName: String?, playEndMarkerStartFrame: Boolean) {
        lottieDrawable.setMinAndMaxFrame(startMarkerName!!, endMarkerName!!, playEndMarkerStartFrame)
    }

    /**
     * @see .setMinFrame
     * @see .setMaxFrame
     */
    fun setMinAndMaxFrame(minFrame: Int, maxFrame: Int) {
        lottieDrawable.setMinAndMaxFrame(minFrame, maxFrame)
    }

    /**
     * @see .setMinProgress
     * @see .setMaxProgress
     */
    fun setMinAndMaxProgress(
        @FloatRange(from = 0.0, to = 1.0) minProgress: Float,
        @FloatRange(from = 0.0, to = 1.0) maxProgress: Float
    ) {
        lottieDrawable.setMinAndMaxProgress(minProgress, maxProgress)
    }

    /**
     * Reverses the current animation speed. This does NOT play the animation.
     *
     * @see .setSpeed
     * @see .playAnimation
     * @see .resumeAnimation
     */
    fun reverseAnimationSpeed() {
        lottieDrawable.reverseAnimationSpeed()
    }

    var speed: Float
        /**
         * Returns the current playback speed. This will be &lt; 0 if the animation is playing backwards.
         */
        get() = lottieDrawable.speed
        /**
         * Sets the playback speed. If speed &lt; 0, the animation will play backwards.
         */
        set(speed) {
            lottieDrawable.speed = speed
        }

    fun addAnimatorUpdateListener(updateListener: AnimatorUpdateListener) {
        lottieDrawable.addAnimatorUpdateListener(updateListener)
    }

    fun removeUpdateListener(updateListener: AnimatorUpdateListener) {
        lottieDrawable.removeAnimatorUpdateListener(updateListener)
    }

    fun removeAllUpdateListeners() {
        lottieDrawable.removeAllUpdateListeners()
    }

    fun addAnimatorListener(listener: Animator.AnimatorListener) {
        lottieDrawable.addAnimatorListener(listener)
    }

    fun removeAnimatorListener(listener: Animator.AnimatorListener) {
        lottieDrawable.removeAnimatorListener(listener)
    }

    fun removeAllAnimatorListeners() {
        lottieDrawable.removeAllAnimatorListeners()
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    fun addAnimatorPauseListener(listener: AnimatorPauseListener) {
        lottieDrawable.addAnimatorPauseListener(listener)
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    fun removeAnimatorPauseListener(listener: AnimatorPauseListener) {
        lottieDrawable.removeAnimatorPauseListener(listener)
    }

    /**
     * @see .setRepeatCount
     */
    @Deprecated("")
    fun loop(loop: Boolean) {
        lottieDrawable.repeatCount = if (loop) ValueAnimator.INFINITE else 0
    }

    @get:LottieDrawable.RepeatMode
    var repeatMode: Int
        /**
         * Defines what this animation should do when it reaches the end.
         *
         * @return either one of [LottieDrawable.REVERSE] or [LottieDrawable.RESTART]
         */
        get() = lottieDrawable.repeatMode
        /**
         * Defines what this animation should do when it reaches the end. This
         * setting is applied only when the repeat count is either greater than
         * 0 or [LottieDrawable.INFINITE]. Defaults to [LottieDrawable.RESTART].
         *
         * @param mode [LottieDrawable.RESTART] or [LottieDrawable.REVERSE]
         */
        set(mode) {
            userActionsTaken.add(UserActionTaken.SET_REPEAT_MODE)
            lottieDrawable.repeatMode = mode
        }

    var repeatCount: Int
        /**
         * Defines how many times the animation should repeat. The default value
         * is 0.
         *
         * @return the number of times the animation should repeat, or [LottieDrawable.INFINITE]
         */
        get() = lottieDrawable.repeatCount
        /**
         * Sets how many times the animation should be repeated. If the repeat
         * count is 0, the animation is never repeated. If the repeat count is
         * greater than 0 or [LottieDrawable.INFINITE], the repeat mode will be taken
         * into account. The repeat count is 0 by default.
         *
         * @param count the number of times the animation should be repeated
         */
        set(count) {
            userActionsTaken.add(UserActionTaken.SET_REPEAT_COUNT)
            lottieDrawable.repeatCount = count
        }

    val isAnimating: Boolean
        get() = lottieDrawable.isAnimating

    var imageAssetsFolder: String?
        get() = lottieDrawable.imageAssetsFolder
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
         * Be wary if you are using many images, however. Lottie is designed to work with vector shapes
         * from After Effects. If your images look like they could be represented with vector shapes,
         * see if it is possible to convert them to shape layers and re-export your animation. Check
         * the documentation at [airbnb.io/lottie](http://airbnb.io/lottie) for more information about importing shapes from
         * Sketch or Illustrator to avoid this.
         */
        set(imageAssetsFolder) {
            lottieDrawable.setImagesAssetsFolder(imageAssetsFolder)
        }

    var maintainOriginalImageBounds: Boolean
        /**
         * When true, dynamically set bitmaps will be drawn with the exact bounds of the original animation, regardless of the bitmap size.
         * When false, dynamically set bitmaps will be drawn at the top left of the original image but with its own bounds.
         *
         *
         * Defaults to false.
         */
        get() = lottieDrawable.maintainOriginalImageBounds
        /**
         * When true, dynamically set bitmaps will be drawn with the exact bounds of the original animation, regardless of the bitmap size.
         * When false, dynamically set bitmaps will be drawn at the top left of the original image but with its own bounds.
         *
         *
         * Defaults to false.
         */
        set(maintainOriginalImageBounds) {
            lottieDrawable.maintainOriginalImageBounds = maintainOriginalImageBounds
        }

    /**
     * Allows you to modify or clear a bitmap that was loaded for an image either automatically
     * through [.setImageAssetsFolder] or with an [ImageAssetDelegate].
     *
     * @return the previous Bitmap or null.
     */
    fun updateBitmap(id: String?, bitmap: Bitmap?): Bitmap? {
        return lottieDrawable.updateBitmap(id, bitmap)
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
     * the documentation at [airbnb.io/lottie](http://airbnb.io/lottie) for more information about importing shapes from
     * Sketch or Illustrator to avoid this.
     */
    fun setImageAssetDelegate(assetDelegate: ImageAssetDelegate?) {
        lottieDrawable.setImageAssetDelegate(assetDelegate)
    }

    /**
     * By default, Lottie will look in src/assets/fonts/FONT_NAME.ttf
     * where FONT_NAME is the fFamily specified in your Lottie file.
     * If your fonts have a different extension, you can override the
     * default here.
     *
     *
     * Alternatively, you can use [.setFontAssetDelegate]
     * for more control.
     *
     * @see .setFontAssetDelegate
     */
    fun setDefaultFontFileExtension(extension: String?) {
        if (extension == null)
            return
        lottieDrawable.defaultFontFileExtension = extension
    }

    /**
     * Use this to manually set fonts.
     */
    fun setFontAssetDelegate(assetDelegate: FontAssetDelegate?) {
        lottieDrawable.fontAssetDelegate = assetDelegate
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
     * [.invalidate]. Setting the same map again will noop.
     */
    fun setFontMap(fontMap: Map<String, Typeface>?) {
        lottieDrawable.setFontMap(fontMap)
    }

    /**
     * Set this to replace animation text with custom text at runtime
     */
    fun setTextDelegate(textDelegate: TextDelegate?) {
        lottieDrawable.textDelegate = textDelegate
    }

    /**
     * Takes a [KeyPath], potentially with wildcards or globstars and resolve it to a list of
     * zero or more actual [Keypaths][KeyPath] that exist in the current animation.
     *
     *
     * If you want to set value callbacks for any of these values, it is recommended to use the
     * returned [KeyPath] objects because they will be internally resolved to their content
     * and won't trigger a tree walk of the animation contents when applied.
     */
    fun resolveKeyPath(keyPath: KeyPath): List<KeyPath> {
        return lottieDrawable.resolveKeyPath(keyPath)
    }

    /**
     * Clear the value callback for all nodes that match the given [KeyPath] and property.
     */
    fun <T> clearValueCallback(keyPath: KeyPath?, property: T) {
        lottieDrawable.addValueCallback(keyPath!!, property, null as LottieValueCallback<T>?)
    }

    /**
     * Add a property callback for the specified [KeyPath]. This [KeyPath] can resolve
     * to multiple contents. In that case, the callback's value will apply to all of them.
     *
     *
     * Internally, this will check if the [KeyPath] has already been resolved with
     * [.resolveKeyPath] and will resolve it if it hasn't.
     */
    fun <T> addValueCallback(keyPath: KeyPath?, property: T, callback: LottieValueCallback<T>?) {
        lottieDrawable.addValueCallback(keyPath!!, property, callback)
    }

    /**
     * Overload of [.addValueCallback] that takes an interface. This allows you to use a single abstract
     * method code block in Kotlin such as:
     * animationView.addValueCallback(yourKeyPath, LottieProperty.COLOR) { yourColor }
     */
    fun <T> addValueCallback(
        keyPath: KeyPath?, property: T,
        callback: SimpleLottieValueCallback<T>
    ) {
        lottieDrawable.addValueCallback(keyPath!!, property, object : LottieValueCallback<T>() {
            override fun getValue(frameInfo: LottieFrameInfo<T>): T? {
                return callback.getValue(frameInfo)
            }
        })
    }

    @MainThread
    fun cancelAnimation() {
        autoPlay = false
        userActionsTaken.add(UserActionTaken.PLAY_OPTION)
        lottieDrawable.cancelAnimation()
    }

    @MainThread
    fun pauseAnimation() {
        autoPlay = false
        lottieDrawable.pauseAnimation()
    }

    var frame: Int
        /**
         * Get the currently rendered frame.
         */
        get() = lottieDrawable.frame
        /**
         * Sets the progress to the specified frame.
         * If the composition isn't set yet, the progress will be set to the frame when
         * it is.
         */
        set(frame) {
            lottieDrawable.frame = frame
        }

    private fun setProgressInternal(
        @FloatRange(from = 0.0, to = 1.0) progress: Float,
        fromUser: Boolean
    ) {
        if (fromUser) {
            userActionsTaken.add(UserActionTaken.SET_PROGRESS)
        }
        lottieDrawable.progress = progress
    }

    @get:FloatRange(from = 0.0, to = 1.0)
    var progress: Float
        get() = lottieDrawable.progress
        set(progress) {
            setProgressInternal(progress, true)
        }

    val duration: Long
        get() {
            val composition = composition
            return composition?.duration?.toLong() ?: 0
        }

    fun setPerformanceTrackingEnabled(enabled: Boolean) {
        lottieDrawable.setPerformanceTrackingEnabled(enabled)
    }

    val performanceTracker: PerformanceTracker?
        get() = lottieDrawable.performanceTracker

    private fun clearComposition() {
        lottieDrawable.clearComposition()
    }

    /**
     * If you are experiencing a device specific crash that happens during drawing, you can set this to true
     * for those devices. If set to true, draw will be wrapped with a try/catch which will cause Lottie to
     * render an empty frame rather than crash your app.
     *
     *
     * Ideally, you will never need this and the vast majority of apps and animations won't. However, you may use
     * this for very specific cases if absolutely necessary.
     *
     *
     * There is no XML attr for this because it should be set programmatically and only for specific devices that
     * are known to be problematic.
     */
    fun setSafeMode(safeMode: Boolean) {
        lottieDrawable.setSafeMode(safeMode)
    }

    var renderMode: RenderMode?
        /**
         * Returns the actual render mode being used. It will always be [RenderMode.HARDWARE] or [RenderMode.SOFTWARE].
         * When the render mode is set to AUTOMATIC, the value will be derived from [RenderMode.useSoftwareRendering].
         */
        get() = lottieDrawable.getRenderMode()
        /**
         * Call this to set whether or not to render with hardware or software acceleration.
         * Lottie defaults to Automatic which will use hardware acceleration unless:
         * 1) There are dash paths and the device is pre-Pie.
         * 2) There are more than 4 masks and mattes.
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
        set(renderMode) {
            lottieDrawable.setRenderMode(renderMode!!)
        }

    var asyncUpdates: AsyncUpdates?
        /**
         * Returns the current value of [AsyncUpdates]. Refer to the docs for [AsyncUpdates] for more info.
         */
        get() = lottieDrawable.asyncUpdates
        /**
         * **Note: this API is experimental and may changed.**
         *
         *
         * Sets the current value for [AsyncUpdates]. Refer to the docs for [AsyncUpdates] for more info.
         */
        set(asyncUpdates) {
            lottieDrawable.asyncUpdates = asyncUpdates
        }

    val asyncUpdatesEnabled: Boolean
        /**
         * Similar to [.getAsyncUpdates] except it returns the actual
         * boolean value for whether async updates are enabled or not.
         */
        get() = lottieDrawable.asyncUpdatesEnabled

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
     * @see .setRenderMode
     */
    fun setApplyingOpacityToLayersEnabled(isApplyingOpacityToLayersEnabled: Boolean) {
        lottieDrawable.isApplyingOpacityToLayersEnabled = isApplyingOpacityToLayersEnabled
    }

    var clipTextToBoundingBox: Boolean
        /**
         * @see .setClipTextToBoundingBox
         */
        get() = lottieDrawable.clipTextToBoundingBox
        /**
         * When true, if there is a bounding box set on a text layer (paragraph text), any text
         * that overflows past its height will not be drawn.
         */
        set(clipTextToBoundingBox) {
            lottieDrawable.clipTextToBoundingBox = clipTextToBoundingBox
        }

    /**
     * This API no longer has any effect.
     */
    @Deprecated("")
    fun disableExtraScaleModeInFitXY() {
        lottieDrawable.disableExtraScaleModeInFitXY()
    }

    fun addLottieOnCompositionLoadedListener(lottieOnCompositionLoadedListener: LottieOnCompositionLoadedListener): Boolean {
        val composition = composition
        if (composition != null) {
            lottieOnCompositionLoadedListener.onCompositionLoaded(composition)
        }
        return lottieOnCompositionLoadedListeners.add(lottieOnCompositionLoadedListener)
    }

    fun removeLottieOnCompositionLoadedListener(lottieOnCompositionLoadedListener: LottieOnCompositionLoadedListener): Boolean {
        return lottieOnCompositionLoadedListeners.remove(lottieOnCompositionLoadedListener)
    }

    fun removeAllLottieOnCompositionLoadedListener() {
        lottieOnCompositionLoadedListeners.clear()
    }

    private fun setLottieDrawable() {
        val wasAnimating = isAnimating
        // Set the drawable to null first because the underlying LottieDrawable's intrinsic bounds can change
        // if the composition changes.
        setImageDrawable(null)
        setImageDrawable(lottieDrawable)
        if (wasAnimating) {
            // This is necessary because lottieDrawable will get unscheduled and canceled when the drawable is set to null.
            lottieDrawable.resumeAnimation()
        }
    }

    private class SavedState : BaseSavedState {
        var animationName: String? = null
        var animationResId: Int = 0
        var progress: Float = 0f
        var isAnimating: Boolean = false
        var imageAssetsFolder: String? = null
        var repeatMode: Int = 0
        var repeatCount: Int = 0

        constructor(superState: Parcelable?) : super(superState)

        private constructor(`in`: Parcel) : super(`in`) {
            animationName = `in`.readString()
            progress = `in`.readFloat()
            isAnimating = `in`.readInt() == 1
            imageAssetsFolder = `in`.readString()
            repeatMode = `in`.readInt()
            repeatCount = `in`.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeString(animationName)
            out.writeFloat(progress)
            out.writeInt(if (isAnimating) 1 else 0)
            out.writeString(imageAssetsFolder)
            out.writeInt(repeatMode)
            out.writeInt(repeatCount)
        }

        companion object : Creator<SavedState?> {
            override fun createFromParcel(`in`: Parcel): SavedState {
                return SavedState(`in`)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }

    private enum class UserActionTaken {
        SET_ANIMATION,
        SET_PROGRESS,
        SET_REPEAT_MODE,
        SET_REPEAT_COUNT,
        SET_IMAGE_ASSETS,
        PLAY_OPTION,
    }

    companion object {
        private val TAG: String = LottieAnimationView::class.java.simpleName
        private val DEFAULT_FAILURE_LISTENER = LottieListener<Throwable?> { throwable: Throwable? ->
            // By default, fail silently for network errors.
            if (Utils.isNetworkException(throwable)) {
                Logger.warning("Unable to load composition.", throwable)
                return@LottieListener
            }
            throw IllegalStateException("Unable to parse composition", throwable)
        }
    }
}
