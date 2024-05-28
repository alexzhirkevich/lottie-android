package com.airbnb.lottie.animation.content

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.PaintCompat
import com.airbnb.lottie.L.beginSection
import com.airbnb.lottie.L.endSection
import com.airbnb.lottie.L.isTraceEnabled
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.LPaint
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ColorKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.DropShadowKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ValueCallbackKeyframeAnimation
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.model.content.ShapeFill
import com.airbnb.lottie.model.layer.BaseLayer
import com.airbnb.lottie.utils.MiscUtils.clamp
import com.airbnb.lottie.utils.MiscUtils.resolveKeyPath
import com.airbnb.lottie.value.LottieValueCallback


class FillContent
    (private val lottieDrawable: LottieDrawable, private val layer: BaseLayer, fill: ShapeFill) : DrawingContent,
    BaseKeyframeAnimation.AnimationListener, KeyPathElementContent {
    private val path = Path()
    private val paint: Paint = LPaint(Paint.ANTI_ALIAS_FLAG)
    override val name: String = fill.name
    private val hidden = fill.isHidden
    private val paths: MutableList<PathContent> = ArrayList()
    private var colorAnimation: BaseKeyframeAnimation<Int, Int>?
    private var opacityAnimation: BaseKeyframeAnimation<Int, Int>?
    private var colorFilterAnimation: BaseKeyframeAnimation<ColorFilter, ColorFilter?>? = null
    private var blurAnimation: BaseKeyframeAnimation<Float, Float>? = null
    var blurMaskFilterRadius: Float = 0f

    private var dropShadowAnimation: DropShadowKeyframeAnimation? = null

    init {
        if (layer.blurEffect != null) {
            blurAnimation = layer.blurEffect!!.blurriness.createAnimation()
            blurAnimation!!.addUpdateListener(this)
            layer.addAnimation(blurAnimation)
        }
        if (layer.dropShadowEffect != null) {
            dropShadowAnimation = DropShadowKeyframeAnimation(this, layer, layer.dropShadowEffect!!)
        }

        if (fill.color == null || fill.opacity == null) {
            colorAnimation = null
            opacityAnimation = null
        } else {
            PaintCompat.setBlendMode(paint, layer.blendMode!!.toNativeBlendMode())

            path.fillType = fill.fillType

            colorAnimation = fill.color.createAnimation().also {
                it.addUpdateListener(this)
                layer.addAnimation(it)
            }

            opacityAnimation = fill.opacity.createAnimation().also {
                it.addUpdateListener(this)
                layer.addAnimation(it)
            }
        }
    }

    override fun onValueChanged() {
        lottieDrawable.invalidateSelf()
    }

    override fun setContents(contentsBefore: List<Content>, contentsAfter: List<Content>) {
        for (i in contentsAfter.indices) {
            val content = contentsAfter[i]
            if (content is PathContent) {
                paths.add(content)
            }
        }
    }

    override fun draw(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int) {
        if (hidden) {
            return
        }
        if (isTraceEnabled()) {
            beginSection("FillContent#draw")
        }
        val color = (colorAnimation as ColorKeyframeAnimation?)!!.intValue
        val alpha = ((parentAlpha / 255f * opacityAnimation!!.value!! / 100f) * 255).toInt()
        paint.color = clamp(alpha, 0, 255) shl 24 or (color and 0xFFFFFF)

        if (colorFilterAnimation != null) {
            paint.setColorFilter(colorFilterAnimation!!.value)
        }

        if (blurAnimation != null) {
            val blurRadius = blurAnimation!!.value
            if (blurRadius == 0f) {
                paint.setMaskFilter(null)
            } else if (blurRadius != blurMaskFilterRadius) {
                val blur = layer.getBlurMaskFilter(blurRadius)
                paint.setMaskFilter(blur)
            }
            blurMaskFilterRadius = blurRadius
        }
        dropShadowAnimation?.applyTo(paint)

        path.reset()
        for (i in paths.indices) {
            path.addPath(paths[i].getPath(), parentMatrix)
        }

        canvas.drawPath(path, paint)

        if (isTraceEnabled()) {
            endSection("FillContent#draw")
        }
    }

    override fun getBounds(outBounds: RectF, parentMatrix: Matrix, applyParents: Boolean) {
        path.reset()
        for (i in paths.indices) {
            path.addPath(paths[i].getPath(), parentMatrix)
        }
        path.computeBounds(outBounds, false)
        // Add padding to account for rounding errors.
        outBounds[outBounds.left - 1, outBounds.top - 1, outBounds.right + 1] = outBounds.bottom + 1
    }

    override fun resolveKeyPath(
        keyPath: KeyPath, depth: Int, accumulator: MutableList<KeyPath>, currentPartialKeyPath: KeyPath
    ) {
        resolveKeyPath(keyPath, depth, accumulator, currentPartialKeyPath, this)
    }

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {

        val dropShadowAnim = dropShadowAnimation

        if (property == LottieProperty.COLOR) {
            colorAnimation!!.setValueCallback(callback as LottieValueCallback<Int>?)
        } else if (property == LottieProperty.OPACITY) {
            opacityAnimation!!.setValueCallback(callback as LottieValueCallback<Int>?)
        } else if (property === LottieProperty.COLOR_FILTER) {
            if (colorFilterAnimation != null) {
                layer.removeAnimation(colorFilterAnimation!!)
            }

            if (callback == null) {
                colorFilterAnimation = null
            } else {
                colorFilterAnimation = ValueCallbackKeyframeAnimation<ColorFilter, ColorFilter?>(callback as LottieValueCallback<ColorFilter?>?).also {
                    it.addUpdateListener(this)
                    layer.addAnimation(it)
                }
            }
        } else if (property == LottieProperty.BLUR_RADIUS) {
            if (blurAnimation != null) {
                blurAnimation!!.setValueCallback(callback as LottieValueCallback<Float>?)
            } else {
                blurAnimation = ValueCallbackKeyframeAnimation<Float, Float>(callback as LottieValueCallback<Float>?).also {
                    it.addUpdateListener(this)
                    layer.addAnimation(it)
                }
            }
        } else if (property == LottieProperty.DROP_SHADOW_COLOR && dropShadowAnim != null) {
            dropShadowAnim.setColorCallback(callback as LottieValueCallback<Int>?)
        } else if (property == LottieProperty.DROP_SHADOW_OPACITY && dropShadowAnim != null) {
            dropShadowAnim.setOpacityCallback(callback as LottieValueCallback<Float>?)
        } else if (property == LottieProperty.DROP_SHADOW_DIRECTION && dropShadowAnim != null) {
            dropShadowAnim.setDirectionCallback(callback as LottieValueCallback<Float>?)
        } else if (property == LottieProperty.DROP_SHADOW_DISTANCE && dropShadowAnim != null) {
            dropShadowAnim.setDistanceCallback(callback as LottieValueCallback<Float>?)
        } else if (property == LottieProperty.DROP_SHADOW_RADIUS && dropShadowAnim != null) {
            dropShadowAnim.setRadiusCallback(callback as LottieValueCallback<Float>?)
        }
    }
}
