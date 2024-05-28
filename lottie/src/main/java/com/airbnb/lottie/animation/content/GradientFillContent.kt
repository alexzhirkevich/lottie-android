package com.airbnb.lottie.animation.content

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.collection.LongSparseArray
import com.airbnb.lottie.L.beginSection
import com.airbnb.lottie.L.endSection
import com.airbnb.lottie.L.isTraceEnabled
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.LPaint
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.DropShadowKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ValueCallbackKeyframeAnimation
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.model.content.GradientColor
import com.airbnb.lottie.model.content.GradientFill
import com.airbnb.lottie.model.content.GradientType
import com.airbnb.lottie.model.layer.BaseLayer
import com.airbnb.lottie.utils.MiscUtils.clamp
import com.airbnb.lottie.utils.MiscUtils.resolveKeyPath
import com.airbnb.lottie.value.LottieValueCallback
import kotlin.math.hypot

class GradientFillContent(
    private val lottieDrawable: LottieDrawable,
    composition: LottieComposition,
    private val layer: BaseLayer,
    fill: GradientFill
) : DrawingContent,
    BaseKeyframeAnimation.AnimationListener, KeyPathElementContent {
    override val name: String = fill.name
    private val hidden = fill.isHidden
    private val linearGradientCache = LongSparseArray<LinearGradient>()
    private val radialGradientCache = LongSparseArray<RadialGradient>()
    private val path = Path()
    private val paint: Paint = LPaint(Paint.ANTI_ALIAS_FLAG)
    private val boundsRect = RectF()
    private val paths: MutableList<PathContent> = ArrayList()
    private val type = fill.gradientType
    private val colorAnimation: BaseKeyframeAnimation<GradientColor, GradientColor>
    private val opacityAnimation: BaseKeyframeAnimation<Int, Int>
    private val startPointAnimation: BaseKeyframeAnimation<PointF, PointF>
    private val endPointAnimation: BaseKeyframeAnimation<PointF, PointF>
    private var colorFilterAnimation: BaseKeyframeAnimation<ColorFilter, ColorFilter?>? = null
    private var colorCallbackAnimation: ValueCallbackKeyframeAnimation<*, *>? = null
    private val cacheSteps: Int
    private var blurAnimation: BaseKeyframeAnimation<Float, Float>? = null
    var blurMaskFilterRadius: Float = 0f
    private var dropShadowAnimation: DropShadowKeyframeAnimation? = null

    init {
        path.fillType = fill.fillType
        cacheSteps = (composition.duration / CACHE_STEPS_MS).toInt()

        colorAnimation = fill.gradientColor.createAnimation()
        colorAnimation.addUpdateListener(this)
        layer.addAnimation(colorAnimation)

        opacityAnimation = fill.opacity.createAnimation()
        opacityAnimation.addUpdateListener(this)
        layer.addAnimation(opacityAnimation)

        startPointAnimation = fill.startPoint.createAnimation()
        startPointAnimation.addUpdateListener(this)
        layer.addAnimation(startPointAnimation)

        endPointAnimation = fill.endPoint.createAnimation()
        endPointAnimation.addUpdateListener(this)
        layer.addAnimation(endPointAnimation)

        if (layer.blurEffect != null) {
            blurAnimation = layer.blurEffect!!.blurriness.createAnimation()
            blurAnimation!!.addUpdateListener(this)
            layer.addAnimation(blurAnimation)
        }
        if (layer.dropShadowEffect != null) {
            dropShadowAnimation = DropShadowKeyframeAnimation(this, layer, layer.dropShadowEffect!!)
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
            beginSection("GradientFillContent#draw")
        }
        path.reset()
        for (i in paths.indices) {
            path.addPath(paths[i].getPath(), parentMatrix)
        }

        path.computeBounds(boundsRect, false)
        val shader = if (type == GradientType.LINEAR) {
            linearGradient
        } else {
            radialGradient
        }
        shader.setLocalMatrix(parentMatrix)
        paint.setShader(shader)

        if (colorFilterAnimation != null) {
            paint.setColorFilter(colorFilterAnimation!!.value)
        }

        if (blurAnimation != null) {
            val blurRadius = blurAnimation!!.value
            if (blurRadius == 0f) {
                paint.setMaskFilter(null)
            } else if (blurRadius != blurMaskFilterRadius) {
                val blur = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                paint.setMaskFilter(blur)
            }
            blurMaskFilterRadius = blurRadius
        }
        dropShadowAnimation?.applyTo(paint)

        val alpha = ((parentAlpha / 255f * opacityAnimation.value!! / 100f) * 255).toInt()
        paint.alpha = clamp(alpha, 0, 255)

        canvas.drawPath(path, paint)
        if (isTraceEnabled()) {
            endSection("GradientFillContent#draw")
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

    private val linearGradient: LinearGradient
        get() {
            val gradientHash = gradientHash
            var gradient = linearGradientCache[gradientHash.toLong()]
            if (gradient != null) {
                return gradient
            }
            val startPoint = startPointAnimation.value
            val endPoint = endPointAnimation.value
            val gradientColor = colorAnimation.value
            val colors = applyDynamicColorsIfNeeded(gradientColor.colors)
            val positions = gradientColor.positions
            gradient = LinearGradient(
                startPoint.x, startPoint.y, endPoint.x, endPoint.y, colors,
                positions, Shader.TileMode.CLAMP
            )
            linearGradientCache.put(gradientHash.toLong(), gradient)
            return gradient
        }

    private val radialGradient: RadialGradient
        get() {
            val gradientHash = gradientHash
            var gradient = radialGradientCache[gradientHash.toLong()]
            if (gradient != null) {
                return gradient
            }
            val startPoint = startPointAnimation.value
            val endPoint = endPointAnimation.value
            val gradientColor = colorAnimation.value
            val colors = applyDynamicColorsIfNeeded(gradientColor!!.colors)
            val positions = gradientColor.positions
            val x0 = startPoint!!.x
            val y0 = startPoint.y
            val x1 = endPoint!!.x
            val y1 = endPoint.y
            var r = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
            if (r <= 0) {
                r = 0.001f
            }
            gradient = RadialGradient(x0, y0, r, colors, positions, Shader.TileMode.CLAMP)
            radialGradientCache.put(gradientHash.toLong(), gradient)
            return gradient
        }

    private val gradientHash: Int
        get() {
            val startPointProgress = Math.round(startPointAnimation.getProgress() * cacheSteps)
            val endPointProgress = Math.round(endPointAnimation.getProgress() * cacheSteps)
            val colorProgress = Math.round(colorAnimation.getProgress() * cacheSteps)
            var hash = 17
            if (startPointProgress != 0) {
                hash = hash * 31 * startPointProgress
            }
            if (endPointProgress != 0) {
                hash = hash * 31 * endPointProgress
            }
            if (colorProgress != 0) {
                hash = hash * 31 * colorProgress
            }
            return hash
        }

    private fun applyDynamicColorsIfNeeded(colors: IntArray): IntArray {
        var colors = colors
        if (colorCallbackAnimation != null) {
            val dynamicColors = colorCallbackAnimation!!.value as Array<Int>
            if (colors.size == dynamicColors.size) {
                for (i in colors.indices) {
                    colors[i] = dynamicColors[i]
                }
            } else {
                colors = IntArray(dynamicColors.size)
                for (i in dynamicColors.indices) {
                    colors[i] = dynamicColors[i]
                }
            }
        }
        return colors
    }

    override fun resolveKeyPath(
        keyPath: KeyPath, depth: Int, accumulator: MutableList<KeyPath>, currentPartialKeyPath: KeyPath
    ) {
        resolveKeyPath(keyPath, depth, accumulator, currentPartialKeyPath, this)
    }

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        if (property == LottieProperty.OPACITY) {
            opacityAnimation.setValueCallback(callback as LottieValueCallback<Int>?)
        } else if (property === LottieProperty.COLOR_FILTER) {
            if (colorFilterAnimation != null) {
                layer.removeAnimation(colorFilterAnimation!!)
            }

            if (callback == null) {
                colorFilterAnimation = null
            } else {
                colorFilterAnimation = ValueCallbackKeyframeAnimation(callback as LottieValueCallback<ColorFilter?>?)
                colorFilterAnimation!!.addUpdateListener(this)
                layer.addAnimation(colorFilterAnimation)
            }
        } else if (property === LottieProperty.GRADIENT_COLOR) {
            if (colorCallbackAnimation != null) {
                layer.removeAnimation(colorCallbackAnimation!!)
            }

            if (callback == null) {
                colorCallbackAnimation = null
            } else {
                linearGradientCache.clear()
                radialGradientCache.clear()
                colorCallbackAnimation = ValueCallbackKeyframeAnimation<Any, T>(callback)
                colorCallbackAnimation!!.addUpdateListener(this)
                layer.addAnimation(colorCallbackAnimation)
            }
        } else if (property == LottieProperty.BLUR_RADIUS) {
            if (blurAnimation != null) {
                blurAnimation!!.setValueCallback(callback as LottieValueCallback<Float>?)
            } else {
                blurAnimation = ValueCallbackKeyframeAnimation(callback as LottieValueCallback<Float>?)
                blurAnimation!!.addUpdateListener(this)
                layer.addAnimation(blurAnimation)
            }
        } else if (property == LottieProperty.DROP_SHADOW_COLOR && dropShadowAnimation != null) {
            dropShadowAnimation!!.setColorCallback(callback as LottieValueCallback<Int>?)
        } else if (property == LottieProperty.DROP_SHADOW_OPACITY && dropShadowAnimation != null) {
            dropShadowAnimation!!.setOpacityCallback(callback as LottieValueCallback<Float>?)
        } else if (property == LottieProperty.DROP_SHADOW_DIRECTION && dropShadowAnimation != null) {
            dropShadowAnimation!!.setDirectionCallback(callback as LottieValueCallback<Float>?)
        } else if (property == LottieProperty.DROP_SHADOW_DISTANCE && dropShadowAnimation != null) {
            dropShadowAnimation!!.setDistanceCallback(callback as LottieValueCallback<Float>?)
        } else if (property == LottieProperty.DROP_SHADOW_RADIUS && dropShadowAnimation != null) {
            dropShadowAnimation!!.setRadiusCallback(callback as LottieValueCallback<Float>?)
        }
    }

    companion object {
        /**
         * Cache the gradients such that it runs at 30fps.
         */
        private const val CACHE_STEPS_MS = 32
    }
}
