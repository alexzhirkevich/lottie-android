package com.airbnb.lottie.model.layer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.LPaint
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ValueCallbackKeyframeAnimation
import com.airbnb.lottie.value.LottieValueCallback

class SolidLayer internal constructor(
    lottieDrawable: LottieDrawable,
    layerModel: Layer
) : BaseLayer(
    lottieDrawable, layerModel
) {
    private val rect = RectF()
    private val paint: Paint = LPaint()
    private val points = FloatArray(8)
    private val path = Path()
    private var colorFilterAnimation: BaseKeyframeAnimation<ColorFilter, ColorFilter>? = null
    private var colorAnimation: BaseKeyframeAnimation<Int, Int>? = null

    init {
        paint.alpha = 0
        paint.style = Paint.Style.FILL
        paint.color = layerModel.solidColor
    }

    override fun drawLayer(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int) {
        val backgroundAlpha = Color.alpha(layerModel.solidColor)
        if (backgroundAlpha == 0) {
            return
        }

        val color = if (colorAnimation == null) null else colorAnimation!!.value
        if (color != null) {
            paint.color = color
        } else {
            paint.color = layerModel.solidColor
        }

        val opacity = if (transform.getOpacity() == null) 100 else transform.getOpacity()!!.value
        val alpha = (parentAlpha / 255f * (backgroundAlpha / 255f * opacity / 100f) * 255).toInt()
        paint.alpha = alpha

        if (colorFilterAnimation != null) {
            paint.setColorFilter(colorFilterAnimation!!.value)
        }
        if (alpha > 0) {
            points[0] = 0f
            points[1] = 0f
            points[2] = layerModel.solidWidth.toFloat()
            points[3] = 0f
            points[4] = layerModel.solidWidth.toFloat()
            points[5] = layerModel.solidHeight.toFloat()
            points[6] = 0f
            points[7] = layerModel.solidHeight.toFloat()

            // We can't map rect here because if there is rotation on the transform then we aren't
            // actually drawing a rect.
            parentMatrix.mapPoints(points)
            path.reset()
            path.moveTo(points[0], points[1])
            path.lineTo(points[2], points[3])
            path.lineTo(points[4], points[5])
            path.lineTo(points[6], points[7])
            path.lineTo(points[0], points[1])
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    override fun getBounds(outBounds: RectF, parentMatrix: Matrix, applyParents: Boolean) {
        super.getBounds(outBounds, parentMatrix, applyParents)
        rect[0f, 0f, layerModel.solidWidth.toFloat()] = layerModel.solidHeight.toFloat()
        boundsMatrix.mapRect(rect)
        outBounds.set(rect)
    }

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        super.addValueCallback(property, callback)
        if (property === LottieProperty.COLOR_FILTER) {
            colorFilterAnimation = if (callback == null) {
                null
            } else {
                ValueCallbackKeyframeAnimation(callback as LottieValueCallback<ColorFilter>?)
            }
        } else if (property == LottieProperty.COLOR) {
            if (callback == null) {
                colorAnimation = null
                paint.color = layerModel.solidColor
            } else {
                colorAnimation = ValueCallbackKeyframeAnimation(callback as LottieValueCallback<Int>?)
            }
        }
    }
}
