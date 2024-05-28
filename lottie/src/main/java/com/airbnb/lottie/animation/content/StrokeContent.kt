package com.airbnb.lottie.animation.content

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ColorKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ValueCallbackKeyframeAnimation
import com.airbnb.lottie.model.content.ShapeStroke
import com.airbnb.lottie.model.layer.BaseLayer
import com.airbnb.lottie.value.LottieValueCallback

class StrokeContent(
    lottieDrawable: LottieDrawable,
    layer: BaseLayer,
    stroke: ShapeStroke
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
    private val colorAnimation: BaseKeyframeAnimation<Int, Int>
    private var colorFilterAnimation: BaseKeyframeAnimation<ColorFilter, ColorFilter?>? = null

    init {
        colorAnimation = stroke.color.createAnimation()
        colorAnimation.addUpdateListener(this)
        layer.addAnimation(colorAnimation)
    }

    override fun draw(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int) {
        if (hidden) {
            return
        }
        paint.color = (colorAnimation as ColorKeyframeAnimation).intValue
        if (colorFilterAnimation != null) {
            paint.setColorFilter(colorFilterAnimation!!.value)
        }
        super.draw(canvas, parentMatrix, parentAlpha)
    }

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        super.addValueCallback(property, callback)
        if (property == LottieProperty.STROKE_COLOR) {
            colorAnimation.setValueCallback(callback as LottieValueCallback<Int>?)
        } else if (property === LottieProperty.COLOR_FILTER) {
            if (colorFilterAnimation != null) {
                layer.removeAnimation(colorFilterAnimation!!)
            }

            if (callback == null) {
                colorFilterAnimation = null
            } else {
                colorFilterAnimation =
                    ValueCallbackKeyframeAnimation(callback as LottieValueCallback<ColorFilter?>?)
                colorFilterAnimation!!.addUpdateListener(this)
                layer.addAnimation(colorAnimation)
            }
        }
    }
}
