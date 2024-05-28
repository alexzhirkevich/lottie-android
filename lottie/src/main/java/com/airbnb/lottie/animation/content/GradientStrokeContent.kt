package com.airbnb.lottie.animation.content

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.collection.LongSparseArray
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ValueCallbackKeyframeAnimation
import com.airbnb.lottie.model.content.GradientColor
import com.airbnb.lottie.model.content.GradientStroke
import com.airbnb.lottie.model.content.GradientType
import com.airbnb.lottie.model.layer.BaseLayer
import com.airbnb.lottie.value.LottieValueCallback
import kotlin.math.hypot

class GradientStrokeContent(
    lottieDrawable: LottieDrawable,
    layer: BaseLayer,
    stroke: GradientStroke
) : BaseStrokeContent(
    lottieDrawable,
    layer,
    stroke.capType.toPaintCap(),
    stroke.joinType.toPaintJoin(),
    stroke.miterLimit,
    stroke.opacity,
    stroke.width,
    stroke.lineDashPattern,
    stroke.dashOffset
) {
    override val name: String = stroke.name
    private val hidden = stroke.isHidden
    private val linearGradientCache = LongSparseArray<LinearGradient>()
    private val radialGradientCache = LongSparseArray<RadialGradient>()
    private val boundsRect = RectF()

    private val type = stroke.gradientType
    private val cacheSteps: Int = (lottieDrawable.composition!!.duration / CACHE_STEPS_MS).toInt()
    private val colorAnimation: BaseKeyframeAnimation<GradientColor, GradientColor>
    private val startPointAnimation: BaseKeyframeAnimation<PointF, PointF>
    private val endPointAnimation: BaseKeyframeAnimation<PointF, PointF>
    private var colorCallbackAnimation: ValueCallbackKeyframeAnimation<*, *>? = null

    init {

        colorAnimation = stroke.gradientColor.createAnimation()
        colorAnimation.addUpdateListener(this)
        layer.addAnimation(colorAnimation)

        startPointAnimation = stroke.startPoint.createAnimation()
        startPointAnimation.addUpdateListener(this)
        layer.addAnimation(startPointAnimation)

        endPointAnimation = stroke.endPoint.createAnimation()
        endPointAnimation.addUpdateListener(this)
        layer.addAnimation(endPointAnimation)
    }

    override fun draw(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int) {
        if (hidden) {
            return
        }
        getBounds(boundsRect, parentMatrix, false)
        val shader = if (type == GradientType.LINEAR) {
            linearGradient
        } else {
            radialGradient
        }
        shader.setLocalMatrix(parentMatrix)
        paint.setShader(shader)

        super.draw(canvas, parentMatrix, parentAlpha)
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
            val colors = applyDynamicColorsIfNeeded(gradientColor!!.colors)
            val positions = gradientColor.positions
            val x0 = startPoint!!.x
            val y0 = startPoint.y
            val x1 = endPoint!!.x
            val y1 = endPoint.y
            gradient = LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP)
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
            val r = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
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

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        super.addValueCallback(property, callback)
        if (property === LottieProperty.GRADIENT_COLOR) {
            if (colorCallbackAnimation != null) {
                layer.removeAnimation(colorCallbackAnimation!!)
            }

            if (callback == null) {
                colorCallbackAnimation = null
            } else {
                colorCallbackAnimation = ValueCallbackKeyframeAnimation<Any, T>(callback)
                colorCallbackAnimation!!.addUpdateListener(this)
                layer.addAnimation(colorCallbackAnimation)
            }
        }
    }

    companion object {
        /**
         * Cache the gradients such that it runs at 30fps.
         */
        private const val CACHE_STEPS_MS = 32
    }
}
