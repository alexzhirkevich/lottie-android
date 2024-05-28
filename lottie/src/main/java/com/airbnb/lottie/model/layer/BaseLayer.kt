package com.airbnb.lottie.model.layer

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import androidx.annotation.CallSuper
import androidx.annotation.FloatRange
import com.airbnb.lottie.L.beginSection
import com.airbnb.lottie.L.endSection
import com.airbnb.lottie.L.isTraceEnabled
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.LPaint
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.DrawingContent
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.FloatKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.MaskKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.TransformKeyframeAnimation
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.model.KeyPathElement
import com.airbnb.lottie.model.content.BlurEffect
import com.airbnb.lottie.model.content.LBlendMode
import com.airbnb.lottie.model.content.Mask.MaskMode
import com.airbnb.lottie.model.content.ShapeData
import com.airbnb.lottie.model.layer.Layer.LayerType
import com.airbnb.lottie.parser.DropShadowEffect
import com.airbnb.lottie.utils.Logger.warning
import com.airbnb.lottie.utils.Utils.saveLayerCompat
import com.airbnb.lottie.value.LottieValueCallback
import kotlin.math.max
import kotlin.math.min

abstract class BaseLayer
internal constructor(
    @JvmField val lottieDrawable: LottieDrawable,
    @JvmField val layerModel: Layer
) : DrawingContent, BaseKeyframeAnimation.AnimationListener, KeyPathElement {
    private val path = Path()
    private val matrix = Matrix()
    private val canvasMatrix = Matrix()
    private val contentPaint: Paint = LPaint(Paint.ANTI_ALIAS_FLAG)
    private val dstInPaint: Paint = LPaint(Paint.ANTI_ALIAS_FLAG, PorterDuff.Mode.DST_IN)
    private val dstOutPaint: Paint = LPaint(Paint.ANTI_ALIAS_FLAG, PorterDuff.Mode.DST_OUT)
    private val mattePaint: Paint = LPaint(Paint.ANTI_ALIAS_FLAG)
    private val clearPaint: Paint = LPaint(PorterDuff.Mode.CLEAR)
    private val rect = RectF()
    private val canvasBounds = RectF()
    private val maskBoundsRect = RectF()
    private val matteBoundsRect = RectF()
    private val tempMaskBoundsRect = RectF()
    private val drawTraceName = layerModel.name + "#draw"
    @JvmField
    val boundsMatrix: Matrix = Matrix()
    private var mask: MaskKeyframeAnimation? = null
    private var inOutAnimation: FloatKeyframeAnimation? = null
    private var matteLayer: BaseLayer? = null

    /**
     * This should only be used by [.buildParentLayerListIfNeeded]
     * to construct the list of parent layers.
     */
    private var parentLayer: BaseLayer? = null
    private var parentLayers: List<BaseLayer>? = null

    private val animations: MutableList<BaseKeyframeAnimation<*, *>> = ArrayList()
    @JvmField
    val transform: TransformKeyframeAnimation
    private var visible = true

    private var outlineMasksAndMattes = false
    private var outlineMasksAndMattesPaint: Paint? = null

    var blurMaskFilterRadius: Float = 0f
    var blurMaskFilter: BlurMaskFilter? = null

    init {
        if (layerModel.matteType == Layer.MatteType.INVERT) {
            mattePaint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.DST_OUT))
        } else {
            mattePaint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.DST_IN))
        }

        this.transform = layerModel.transform.createAnimation()
        transform.addListener(this)

        if (layerModel.masks != null && !layerModel.masks.isEmpty()) {
            this.mask = MaskKeyframeAnimation(layerModel.masks)?.also {
                for (animation in it.getMaskAnimations()) {
                    // Don't call addAnimation() because progress gets set manually in setProgress to
                    // properly handle time scale.
                    animation.addUpdateListener(this)
                }
                for (animation in it.getOpacityAnimations()) {
                    addAnimation(animation)
                    animation.addUpdateListener(this)
                }
            }
        }
        setupInOutAnimations()
    }

    /**
     * Enable this to debug slow animations by outlining masks and mattes. The performance overhead of the masks and mattes will
     * be proportional to the surface area of all of the masks/mattes combined.
     *
     *
     * DO NOT leave this enabled in production.
     */
    open fun setOutlineMasksAndMattes(outline: Boolean) {
        if (outline && outlineMasksAndMattesPaint == null) {
            outlineMasksAndMattesPaint = LPaint()
        }
        outlineMasksAndMattes = outline
    }

    override fun onValueChanged() {
        invalidateSelf()
    }

    fun setMatteLayer(matteLayer: BaseLayer?) {
        this.matteLayer = matteLayer
    }

    fun hasMatteOnThisLayer(): Boolean {
        return matteLayer != null
    }

    fun setParentLayer(parentLayer: BaseLayer?) {
        this.parentLayer = parentLayer
    }

    private fun setupInOutAnimations() {
        if (!layerModel.inOutKeyframes.isEmpty()) {
            inOutAnimation = FloatKeyframeAnimation(layerModel.inOutKeyframes)
            inOutAnimation!!.setIsDiscrete()
            inOutAnimation!!.addUpdateListener {
                setVisible(
                    inOutAnimation!!.floatValue == 1f
                )
            }
            setVisible(inOutAnimation!!.value == 1f)
            addAnimation(inOutAnimation)
        } else {
            setVisible(true)
        }
    }

    private fun invalidateSelf() {
        lottieDrawable.invalidateSelf()
    }

    fun addAnimation(newAnimation: BaseKeyframeAnimation<*, *>?) {
        if (newAnimation == null) {
            return
        }
        animations.add(newAnimation)
    }

    fun removeAnimation(animation: BaseKeyframeAnimation<*, *>) {
        animations.remove(animation)
    }

    @CallSuper
    override fun getBounds(
        outBounds: RectF, parentMatrix: Matrix, applyParents: Boolean
    ) {
        rect[0f, 0f, 0f] = 0f
        buildParentLayerListIfNeeded()
        boundsMatrix.set(parentMatrix)

        if (applyParents) {
            if (parentLayers != null) {
                for (i in parentLayers!!.indices.reversed()) {
                    boundsMatrix.preConcat(parentLayers!![i].transform.getMatrix())
                }
            } else if (parentLayer != null) {
                boundsMatrix.preConcat(parentLayer!!.transform.getMatrix())
            }
        }

        boundsMatrix.preConcat(transform.getMatrix())
    }

    override fun draw(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int) {
        beginSection(drawTraceName)
        if (!visible || layerModel.isHidden) {
            endSection(drawTraceName)
            return
        }
        buildParentLayerListIfNeeded()
        if (isTraceEnabled()) {
            beginSection("Layer#parentMatrix")
        }
        matrix.reset()
        matrix.set(parentMatrix)
        for (i in parentLayers!!.indices.reversed()) {
            matrix.preConcat(parentLayers!![i].transform.getMatrix())
        }
        if (isTraceEnabled()) {
            endSection("Layer#parentMatrix")
        }
        // It is unclear why but getting the opacity here would sometimes NPE.
        // The extra code here is designed to avoid this.
        // https://github.com/airbnb/lottie-android/issues/2083
        var opacity = 100
        val opacityAnimation: BaseKeyframeAnimation<*, Int>? = transform.getOpacity()
        if (opacityAnimation != null) {
            val opacityValue = opacityAnimation.value
            if (opacityValue != null) {
                opacity = opacityValue
            }
        }
        val alpha = ((parentAlpha / 255f * opacity.toFloat() / 100f) * 255).toInt()
        if (!hasMatteOnThisLayer() && !hasMasksOnThisLayer()) {
            matrix.preConcat(transform.getMatrix())
            if (isTraceEnabled()) {
                beginSection("Layer#drawLayer")
            }
            drawLayer(canvas, matrix, alpha)
            if (isTraceEnabled()) {
                endSection("Layer#drawLayer")
            }
            recordRenderTime(endSection(drawTraceName))
            return
        }

        if (isTraceEnabled()) {
            beginSection("Layer#computeBounds")
        }
        getBounds(rect, matrix, false)

        intersectBoundsWithMatte(rect, parentMatrix)

        matrix.preConcat(transform.getMatrix())
        intersectBoundsWithMask(rect, matrix)

        // Intersect the mask and matte rect with the canvas bounds.
        // If the canvas has a transform, then we need to transform its bounds by its matrix
        // so that we know the coordinate space that the canvas is showing.
        canvasBounds[0f, 0f, canvas.width.toFloat()] = canvas.height.toFloat()
        canvas.getMatrix(canvasMatrix)
        if (!canvasMatrix.isIdentity) {
            canvasMatrix.invert(canvasMatrix)
            canvasMatrix.mapRect(canvasBounds)
        }
        if (!rect.intersect(canvasBounds)) {
            rect[0f, 0f, 0f] = 0f
        }

        if (isTraceEnabled()) {
            endSection("Layer#computeBounds")
        }

        // Ensure that what we are drawing is >=1px of width and height.
        // On older devices, drawing to an offscreen buffer of <1px would draw back as a black bar.
        // https://github.com/airbnb/lottie-android/issues/1625
        if (rect.width() >= 1f && rect.height() >= 1f) {
            if (isTraceEnabled()) {
                beginSection("Layer#saveLayer")
            }
            contentPaint.alpha = 255
            saveLayerCompat(canvas, rect, contentPaint)
            if (isTraceEnabled()) {
                endSection("Layer#saveLayer")
            }

            // Clear the off screen buffer. This is necessary for some phones.
            clearCanvas(canvas)
            if (isTraceEnabled()) {
                beginSection("Layer#drawLayer")
            }
            drawLayer(canvas, matrix, alpha)
            if (isTraceEnabled()) {
                endSection("Layer#drawLayer")
            }

            if (hasMasksOnThisLayer()) {
                applyMasks(canvas, matrix)
            }

            if (hasMatteOnThisLayer()) {
                if (isTraceEnabled()) {
                    beginSection("Layer#drawMatte")
                    beginSection("Layer#saveLayer")
                }
                saveLayerCompat(canvas, rect, mattePaint, SAVE_FLAGS)
                if (isTraceEnabled()) {
                    endSection("Layer#saveLayer")
                }
                clearCanvas(canvas)
                matteLayer!!.draw(canvas, parentMatrix, alpha)
                if (isTraceEnabled()) {
                    beginSection("Layer#restoreLayer")
                }
                canvas.restore()
                if (isTraceEnabled()) {
                    endSection("Layer#restoreLayer")
                    endSection("Layer#drawMatte")
                }
            }

            if (isTraceEnabled()) {
                beginSection("Layer#restoreLayer")
            }
            canvas.restore()
            if (isTraceEnabled()) {
                endSection("Layer#restoreLayer")
            }
        }

        if (outlineMasksAndMattes && outlineMasksAndMattesPaint != null) {
            outlineMasksAndMattesPaint!!.style = Paint.Style.STROKE
            outlineMasksAndMattesPaint!!.color = -0x3d7fd
            outlineMasksAndMattesPaint!!.strokeWidth = 4f
            canvas.drawRect(rect, outlineMasksAndMattesPaint!!)
            outlineMasksAndMattesPaint!!.style = Paint.Style.FILL
            outlineMasksAndMattesPaint!!.color = 0x50EBEBEB
            canvas.drawRect(rect, outlineMasksAndMattesPaint!!)
        }

        recordRenderTime(endSection(drawTraceName))
    }

    private fun recordRenderTime(ms: Float) {
        lottieDrawable.composition!!.performanceTracker.recordRenderTime(layerModel.name, ms)
    }

    private fun clearCanvas(canvas: Canvas) {
        if (isTraceEnabled()) {
            beginSection("Layer#clearLayer")
        }
        // If we don't pad the clear draw, some phones leave a 1px border of the graphics buffer.
        canvas.drawRect(rect.left - 1, rect.top - 1, rect.right + 1, rect.bottom + 1, clearPaint)
        if (isTraceEnabled()) {
            endSection("Layer#clearLayer")
        }
    }

    private fun intersectBoundsWithMask(rect: RectF, matrix: Matrix) {
        maskBoundsRect[0f, 0f, 0f] = 0f
        if (!hasMasksOnThisLayer()) {
            return
        }
        val size = mask!!.masks.size
        for (i in 0 until size) {
            val mask = mask!!.masks[i]
            val maskAnimation: BaseKeyframeAnimation<*, Path> = this.mask!!.getMaskAnimations()[i]
            val maskPath = maskAnimation.value
                ?: // This should never happen but seems to happen occasionally.
// There is no known repro for this but is is probably best to just skip this mask if that is the case.
// https://github.com/airbnb/lottie-android/issues/1879
                continue
            path.set(maskPath)
            path.transform(matrix)

            when (mask.maskMode) {
                MaskMode.MASK_MODE_NONE ->           // Mask mode none will just render the original content so it is the whole bounds.
                    return

                MaskMode.MASK_MODE_SUBTRACT ->           // If there is a subtract mask, the mask could potentially be the size of the entire
                    // canvas so we can't use the mask bounds.
                    return

                MaskMode.MASK_MODE_INTERSECT, MaskMode.MASK_MODE_ADD -> {
                    if (mask.isInverted) {
                        return
                    }
                    path.computeBounds(tempMaskBoundsRect, false)
                    // As we iterate through the masks, we want to calculate the union region of the masks.
                    // We initialize the rect with the first mask. If we don't call set() on the first call,
                    // the rect will always extend to (0,0).
                    if (i == 0) {
                        maskBoundsRect.set(tempMaskBoundsRect)
                    } else {
                        maskBoundsRect[min(
                            maskBoundsRect.left.toDouble(),
                            tempMaskBoundsRect.left.toDouble()
                        ).toFloat(), min(
                            maskBoundsRect.top.toDouble(),
                            tempMaskBoundsRect.top.toDouble()
                        ).toFloat(), max(maskBoundsRect.right.toDouble(), tempMaskBoundsRect.right.toDouble()).toFloat()] =
                            max(maskBoundsRect.bottom.toDouble(), tempMaskBoundsRect.bottom.toDouble()).toFloat()
                    }
                }

                else -> {
                    path.computeBounds(tempMaskBoundsRect, false)
                    if (i == 0) {
                        maskBoundsRect.set(tempMaskBoundsRect)
                    } else {
                        maskBoundsRect[min(
                            maskBoundsRect.left.toDouble(),
                            tempMaskBoundsRect.left.toDouble()
                        ).toFloat(), min(
                            maskBoundsRect.top.toDouble(),
                            tempMaskBoundsRect.top.toDouble()
                        ).toFloat(), max(maskBoundsRect.right.toDouble(), tempMaskBoundsRect.right.toDouble()).toFloat()] =
                            max(maskBoundsRect.bottom.toDouble(), tempMaskBoundsRect.bottom.toDouble()).toFloat()
                    }
                }
            }
        }

        val intersects = rect.intersect(maskBoundsRect)
        if (!intersects) {
            rect[0f, 0f, 0f] = 0f
        }
    }

    private fun intersectBoundsWithMatte(rect: RectF, matrix: Matrix) {
        if (!hasMatteOnThisLayer()) {
            return
        }

        if (layerModel.matteType == Layer.MatteType.INVERT) {
            // We can't trim the bounds if the mask is inverted since it extends all the way to the
            // composition bounds.
            return
        }
        matteBoundsRect[0f, 0f, 0f] = 0f
        matteLayer!!.getBounds(matteBoundsRect, matrix, true)
        val intersects = rect.intersect(matteBoundsRect)
        if (!intersects) {
            rect[0f, 0f, 0f] = 0f
        }
    }

    abstract fun drawLayer(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int)

    private fun applyMasks(canvas: Canvas, matrix: Matrix) {
        if (isTraceEnabled()) {
            beginSection("Layer#saveLayer")
        }
        saveLayerCompat(canvas, rect, dstInPaint, SAVE_FLAGS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // Pre-Pie, offscreen buffers were opaque which meant that outer border of a mask
            // might get drawn depending on the result of float rounding.
            clearCanvas(canvas)
        }
        if (isTraceEnabled()) {
            endSection("Layer#saveLayer")
        }
        for (i in mask!!.masks.indices) {
            val mask = mask!!.masks[i]
            val maskAnimation = this.mask!!.getMaskAnimations()[i]
            val opacityAnimation = this.mask!!.getOpacityAnimations()[i]
            when (mask.maskMode) {
                MaskMode.MASK_MODE_NONE ->           // None mask should have no effect. If all masks are NONE, fill the
                    // mask canvas with a rectangle so it fully covers the original layer content.
                    // However, if there are other masks, they should be the only ones that have an effect so
                    // this should noop.
                    if (areAllMasksNone()) {
                        contentPaint.alpha = 255
                        canvas.drawRect(rect, contentPaint)
                    }

                MaskMode.MASK_MODE_ADD -> if (mask.isInverted) {
                    applyInvertedAddMask(canvas, matrix, maskAnimation, opacityAnimation)
                } else {
                    applyAddMask(canvas, matrix, maskAnimation, opacityAnimation)
                }

                MaskMode.MASK_MODE_SUBTRACT -> {
                    if (i == 0) {
                        contentPaint.color = Color.BLACK
                        contentPaint.alpha = 255
                        canvas.drawRect(rect, contentPaint)
                    }
                    if (mask.isInverted) {
                        applyInvertedSubtractMask(canvas, matrix, maskAnimation, opacityAnimation)
                    } else {
                        applySubtractMask(canvas, matrix, maskAnimation)
                    }
                }

                MaskMode.MASK_MODE_INTERSECT -> if (mask.isInverted) {
                    applyInvertedIntersectMask(canvas, matrix, maskAnimation, opacityAnimation)
                } else {
                    applyIntersectMask(canvas, matrix, maskAnimation, opacityAnimation)
                }
            }
        }
        if (isTraceEnabled()) {
            beginSection("Layer#restoreLayer")
        }
        canvas.restore()
        if (isTraceEnabled()) {
            endSection("Layer#restoreLayer")
        }
    }

    private fun areAllMasksNone(): Boolean {
        if (mask!!.getMaskAnimations().isEmpty()) {
            return false
        }
        for (i in mask!!.masks.indices) {
            if (mask!!.masks[i].maskMode != MaskMode.MASK_MODE_NONE) {
                return false
            }
        }
        return true
    }

    private fun applyAddMask(
        canvas: Canvas, matrix: Matrix,
        maskAnimation: BaseKeyframeAnimation<ShapeData, Path>, opacityAnimation: BaseKeyframeAnimation<Int, Int>
    ) {
        val maskPath = maskAnimation.value
        path.set(maskPath)
        path.transform(matrix)
        contentPaint.alpha = (opacityAnimation.value * 2.55f).toInt()
        canvas.drawPath(path, contentPaint)
    }

    private fun applyInvertedAddMask(
        canvas: Canvas, matrix: Matrix,
        maskAnimation: BaseKeyframeAnimation<ShapeData, Path>, opacityAnimation: BaseKeyframeAnimation<Int, Int>
    ) {
        saveLayerCompat(canvas, rect, contentPaint)
        canvas.drawRect(rect, contentPaint)
        val maskPath = maskAnimation.value
        path.set(maskPath)
        path.transform(matrix)
        contentPaint.alpha = (opacityAnimation.value * 2.55f).toInt()
        canvas.drawPath(path, dstOutPaint)
        canvas.restore()
    }

    private fun applySubtractMask(canvas: Canvas, matrix: Matrix, maskAnimation: BaseKeyframeAnimation<ShapeData, Path>) {
        val maskPath = maskAnimation.value
        path.set(maskPath)
        path.transform(matrix)
        canvas.drawPath(path, dstOutPaint)
    }

    private fun applyInvertedSubtractMask(
        canvas: Canvas, matrix: Matrix,
        maskAnimation: BaseKeyframeAnimation<ShapeData, Path>, opacityAnimation: BaseKeyframeAnimation<Int, Int>
    ) {
        saveLayerCompat(canvas, rect, dstOutPaint)
        canvas.drawRect(rect, contentPaint)
        dstOutPaint.alpha = (opacityAnimation.value * 2.55f).toInt()
        val maskPath = maskAnimation.value
        path.set(maskPath)
        path.transform(matrix)
        canvas.drawPath(path, dstOutPaint)
        canvas.restore()
    }

    private fun applyIntersectMask(
        canvas: Canvas, matrix: Matrix,
        maskAnimation: BaseKeyframeAnimation<ShapeData, Path>, opacityAnimation: BaseKeyframeAnimation<Int, Int>
    ) {
        saveLayerCompat(canvas, rect, dstInPaint)
        val maskPath = maskAnimation.value
        path.set(maskPath)
        path.transform(matrix)
        contentPaint.alpha = (opacityAnimation.value * 2.55f).toInt()
        canvas.drawPath(path, contentPaint)
        canvas.restore()
    }

    private fun applyInvertedIntersectMask(
        canvas: Canvas, matrix: Matrix,
        maskAnimation: BaseKeyframeAnimation<ShapeData, Path>, opacityAnimation: BaseKeyframeAnimation<Int, Int>
    ) {
        saveLayerCompat(canvas, rect, dstInPaint)
        canvas.drawRect(rect, contentPaint)
        dstOutPaint.alpha = (opacityAnimation.value * 2.55f).toInt()
        val maskPath = maskAnimation.value
        path.set(maskPath)
        path.transform(matrix)
        canvas.drawPath(path, dstOutPaint)
        canvas.restore()
    }

    fun hasMasksOnThisLayer(): Boolean {
        return !mask?.getMaskAnimations().isNullOrEmpty()
    }

    private fun setVisible(visible: Boolean) {
        if (visible != this.visible) {
            this.visible = visible
            invalidateSelf()
        }
    }

    open fun setProgress(@FloatRange(from = 0.0, to = 1.0) progress: Float) {
        if (isTraceEnabled()) {
            beginSection("BaseLayer#setProgress")
            // Time stretch should not be applied to the layer transform.
            beginSection("BaseLayer#setProgress.transform")
        }
        transform.setProgress(progress)
        if (isTraceEnabled()) {
            endSection("BaseLayer#setProgress.transform")
        }
        mask?.let {
            if (isTraceEnabled()) {
                beginSection("BaseLayer#setProgress.mask")
            }
            for (i in it.getMaskAnimations().indices) {
                it.getMaskAnimations()[i].setProgress(progress)
            }
            if (isTraceEnabled()) {
                endSection("BaseLayer#setProgress.mask")
            }
        }

        inOutAnimation?.let {
            if (isTraceEnabled()) {
                beginSection("BaseLayer#setProgress.inout")
            }
            it.setProgress(progress)
            if (isTraceEnabled()) {
                endSection("BaseLayer#setProgress.inout")
            }
        }

        matteLayer?.let {
            if (isTraceEnabled()) {
                beginSection("BaseLayer#setProgress.matte")
            }
            it.setProgress(progress)
            if (isTraceEnabled()) {
                endSection("BaseLayer#setProgress.matte")
            }
        }
        if (isTraceEnabled()) {
            beginSection("BaseLayer#setProgress.animations." + animations.size)
        }
        for (i in animations.indices) {
            animations[i].setProgress(progress)
        }
        if (isTraceEnabled()) {
            endSection("BaseLayer#setProgress.animations." + animations.size)
            endSection("BaseLayer#setProgress")
        }
    }

    private fun buildParentLayerListIfNeeded() {
        if (parentLayers != null) {
            return
        }
        if (parentLayer == null) {
            parentLayers = emptyList()
            return
        }

        parentLayers = ArrayList()
        var layer = parentLayer
        while (layer != null) {
            (parentLayers as ArrayList<BaseLayer>).add(layer)
            layer = layer.parentLayer
        }
    }

    override val name: String
        get() = layerModel.name

    open val blurEffect: BlurEffect?
        get() = layerModel.blurEffect

    val blendMode: LBlendMode?
        get() = layerModel.blendMode

    fun getBlurMaskFilter(radius: Float): BlurMaskFilter? {
        if (blurMaskFilterRadius == radius) {
            return blurMaskFilter
        }
        blurMaskFilter = BlurMaskFilter(radius / 2f, BlurMaskFilter.Blur.NORMAL)
        blurMaskFilterRadius = radius
        return blurMaskFilter
    }

    open val dropShadowEffect: DropShadowEffect?
        get() = layerModel.dropShadowEffect

    override fun setContents(contentsBefore: List<Content>, contentsAfter: List<Content>) {
        // Do nothing
    }

    override fun resolveKeyPath(
        keyPath: KeyPath, depth: Int, accumulator: MutableList<KeyPath>, currentPartialKeyPath: KeyPath
    ) {
        var currentPartialKeyPath = currentPartialKeyPath
        if (matteLayer != null) {
            val matteCurrentPartialKeyPath = currentPartialKeyPath.addKey(matteLayer!!.name)
            if (keyPath.fullyResolvesTo(matteLayer!!.name, depth)) {
                accumulator.add(matteCurrentPartialKeyPath.resolve(matteLayer!!))
            }

            if (keyPath.propagateToChildren(name, depth)) {
                val newDepth = depth + keyPath.incrementDepthBy(matteLayer!!.name, depth)
                matteLayer!!.resolveChildKeyPath(keyPath, newDepth, accumulator, matteCurrentPartialKeyPath)
            }
        }

        if (!keyPath.matches(name, depth)) {
            return
        }

        if ("__container" != name) {
            currentPartialKeyPath = currentPartialKeyPath.addKey(name)

            if (keyPath.fullyResolvesTo(name, depth)) {
                accumulator.add(currentPartialKeyPath.resolve(this))
            }
        }

        if (keyPath.propagateToChildren(name, depth)) {
            val newDepth = depth + keyPath.incrementDepthBy(name, depth)
            resolveChildKeyPath(keyPath, newDepth, accumulator, currentPartialKeyPath)
        }
    }

    protected open fun resolveChildKeyPath(
        keyPath: KeyPath,
        depth: Int,
        accumulator: MutableList<KeyPath>,
        currentPartialKeyPath: KeyPath
    ) {
    }

    @CallSuper
    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        transform.applyValueCallback(property, callback)
    }

    companion object {
        /**
         * These flags were in Canvas but they were deprecated and removed.
         * TODO: test removing these on older versions of Android.
         */
        private const val CLIP_SAVE_FLAG = 0x02
        private const val CLIP_TO_LAYER_SAVE_FLAG = 0x10
        private const val MATRIX_SAVE_FLAG = 0x01
        private const val SAVE_FLAGS = CLIP_SAVE_FLAG or CLIP_TO_LAYER_SAVE_FLAG or MATRIX_SAVE_FLAG

        @JvmStatic
        fun forModel(
            compositionLayer: CompositionLayer,
            layerModel: Layer,
            drawable: LottieDrawable,
            composition: LottieComposition
        ): BaseLayer? {
            when (layerModel.layerType) {
                LayerType.SHAPE -> return ShapeLayer(drawable, layerModel, compositionLayer, composition)
                LayerType.PRE_COMP -> return CompositionLayer(
                    drawable,
                    layerModel,
                    composition.getPrecomps(layerModel.refId!!),
                    composition
                )

                LayerType.SOLID -> return SolidLayer(drawable, layerModel)
                LayerType.IMAGE -> return ImageLayer(drawable, layerModel)
                LayerType.NULL -> return NullLayer(drawable, layerModel)
                LayerType.TEXT -> return TextLayer(drawable, layerModel)
                LayerType.UNKNOWN -> {
                    // Do nothing
                    warning("Unknown layer type " + layerModel.layerType)
                    return null
                }

                else -> {
                    warning("Unknown layer type " + layerModel.layerType)
                    return null
                }
            }
        }
    }
}
