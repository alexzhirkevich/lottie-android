package com.airbnb.lottie.animation.keyframe

import android.graphics.Matrix
import android.graphics.PointF
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.animatable.AnimatableTransform
import com.airbnb.lottie.model.layer.BaseLayer
import com.airbnb.lottie.value.Keyframe
import com.airbnb.lottie.value.LottieValueCallback
import com.airbnb.lottie.value.ScaleXY
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

class TransformKeyframeAnimation(animatableTransform: AnimatableTransform) {
    private val matrix = Matrix()
    private var skewMatrix1: Matrix? = null
    private var skewMatrix2: Matrix? = null
    private var skewMatrix3: Matrix? = null
    private val skewValues: FloatArray?

    private var anchorPoint: BaseKeyframeAnimation<PointF, PointF>?
    private var position: BaseKeyframeAnimation<PointF, PointF>?
    private var scale: BaseKeyframeAnimation<ScaleXY, ScaleXY>?
    private var rotation: BaseKeyframeAnimation<Float, Float>?
    private var opacity: BaseKeyframeAnimation<Int, Int>? = null
    private var skew: FloatKeyframeAnimation?
    private var skewAngle: FloatKeyframeAnimation?

    // Used for repeaters
    var startOpacity: BaseKeyframeAnimation<*, Float>? = null
        private set
    var endOpacity: BaseKeyframeAnimation<*, Float>? = null
        private set

    private val autoOrient: Boolean


    init {
        anchorPoint = if (animatableTransform.anchorPoint == null) null else animatableTransform.anchorPoint!!.createAnimation()
        position = if (animatableTransform.position == null) null else animatableTransform.position!!.createAnimation()
        scale = if (animatableTransform.scale == null) null else animatableTransform.scale!!.createAnimation()
        rotation = if (animatableTransform.rotation == null) null else animatableTransform.rotation!!.createAnimation()
        skew = if (animatableTransform.skew == null) null else animatableTransform.skew!!.createAnimation() as FloatKeyframeAnimation
        autoOrient = animatableTransform.isAutoOrient
        if (skew != null) {
            skewMatrix1 = Matrix()
            skewMatrix2 = Matrix()
            skewMatrix3 = Matrix()
            skewValues = FloatArray(9)
        } else {
            skewMatrix1 = null
            skewMatrix2 = null
            skewMatrix3 = null
            skewValues = null
        }
        skewAngle = if (animatableTransform.skewAngle == null) null else animatableTransform.skewAngle!!.createAnimation() as FloatKeyframeAnimation
        if (animatableTransform.opacity != null) {
            opacity = animatableTransform.opacity!!.createAnimation()
        }
        startOpacity = if (animatableTransform.startOpacity != null) {
            animatableTransform.startOpacity!!.createAnimation()
        } else {
            null
        }
        endOpacity = if (animatableTransform.endOpacity != null) {
            animatableTransform.endOpacity!!.createAnimation()
        } else {
            null
        }
    }

    fun addAnimationsToLayer(layer: BaseLayer) {
        layer.addAnimation(opacity)
        layer.addAnimation(startOpacity)
        layer.addAnimation(endOpacity)

        layer.addAnimation(anchorPoint)
        layer.addAnimation(position)
        layer.addAnimation(scale)
        layer.addAnimation(rotation)
        layer.addAnimation(skew)
        layer.addAnimation(skewAngle)
    }

    fun addListener(listener: BaseKeyframeAnimation.AnimationListener?) {
        if (opacity != null) {
            opacity!!.addUpdateListener(listener!!)
        }
        if (startOpacity != null) {
            startOpacity!!.addUpdateListener(listener!!)
        }
        if (endOpacity != null) {
            endOpacity!!.addUpdateListener(listener!!)
        }

        if (anchorPoint != null) {
            anchorPoint!!.addUpdateListener(listener!!)
        }
        if (position != null) {
            position!!.addUpdateListener(listener!!)
        }
        if (scale != null) {
            scale!!.addUpdateListener(listener!!)
        }
        if (rotation != null) {
            rotation!!.addUpdateListener(listener!!)
        }
        if (skew != null) {
            skew!!.addUpdateListener(listener!!)
        }
        if (skewAngle != null) {
            skewAngle!!.addUpdateListener(listener!!)
        }
    }

    fun setProgress(progress: Float) {
        if (opacity != null) {
            opacity!!.setProgress(progress)
        }
        if (startOpacity != null) {
            startOpacity!!.setProgress(progress)
        }
        if (endOpacity != null) {
            endOpacity!!.setProgress(progress)
        }

        if (anchorPoint != null) {
            anchorPoint!!.setProgress(progress)
        }
        if (position != null) {
            position!!.setProgress(progress)
        }
        if (scale != null) {
            scale!!.setProgress(progress)
        }
        if (rotation != null) {
            rotation!!.setProgress(progress)
        }
        if (skew != null) {
            skew!!.setProgress(progress)
        }
        if (skewAngle != null) {
            skewAngle!!.setProgress(progress)
        }
    }

    fun getOpacity(): BaseKeyframeAnimation<Int, Int>? {
        return opacity
    }

    fun getMatrix(): Matrix {
        matrix.reset()
        val position = this.position
        if (position != null) {
            val positionValue = position.value
            if (positionValue != null && (positionValue.x != 0f || positionValue.y != 0f)) {
                matrix.preTranslate(positionValue.x, positionValue.y)
            }
        }

        // If autoOrient is true, the rotation should follow the derivative of the position rather
        // than the rotation property.
        if (autoOrient) {
            if (position != null) {
                val currentProgress = position.getProgress()
                val startPosition = position.value
                // Store the start X and Y values because the pointF will be overwritten by the next getValue call.
                val startX = startPosition.x
                val startY = startPosition.y
                // 1) Find the next position value.
                // 2) Create a vector from the current position to the next position.
                // 3) Find the angle of that vector to the X axis (0 degrees).
                position.setProgress(currentProgress + 0.0001f)
                val nextPosition = position.value
                position.setProgress(currentProgress)
                val rotationValue = Math.toDegrees(atan2((nextPosition.y - startY).toDouble(), (nextPosition.x - startX).toDouble()))
                matrix.preRotate(rotationValue.toFloat())
            }
        } else {
            val rotation = this.rotation
            if (rotation != null) {
                val rotationValue = if (rotation is ValueCallbackKeyframeAnimation<*, *>) {
                    rotation.value
                } else {
                    (rotation as FloatKeyframeAnimation).floatValue
                }
                if (rotationValue != 0f) {
                    matrix.preRotate(rotationValue)
                }
            }
        }

        val skew = this.skew
        if (skew != null) {
            val mCos = if (skewAngle == null) 0f else cos(Math.toRadians((-skewAngle!!.floatValue + 90).toDouble()))
                .toFloat()
            val mSin = if (skewAngle == null) 1f else sin(Math.toRadians((-skewAngle!!.floatValue + 90).toDouble()))
                .toFloat()
            val aTan = tan(Math.toRadians(skew.floatValue.toDouble())).toFloat()
            clearSkewValues()
            skewValues!![0] = mCos
            skewValues[1] = mSin
            skewValues[3] = -mSin
            skewValues[4] = mCos
            skewValues[8] = 1f
            skewMatrix1!!.setValues(skewValues)
            clearSkewValues()
            skewValues[0] = 1f
            skewValues[3] = aTan
            skewValues[4] = 1f
            skewValues[8] = 1f
            skewMatrix2!!.setValues(skewValues)
            clearSkewValues()
            skewValues[0] = mCos
            skewValues[1] = -mSin
            skewValues[3] = mSin
            skewValues[4] = mCos
            skewValues[8] = 1f
            skewMatrix3!!.setValues(skewValues)
            skewMatrix2!!.preConcat(skewMatrix1)
            skewMatrix3!!.preConcat(skewMatrix2)

            matrix.preConcat(skewMatrix3)
        }

        val scale = this.scale
        if (scale != null) {
            val scaleTransform = scale.value
            if (scaleTransform != null && (scaleTransform.scaleX != 1f || scaleTransform.scaleY != 1f)) {
                matrix.preScale(scaleTransform.scaleX, scaleTransform.scaleY)
            }
        }

        val anchorPoint = this.anchorPoint
        if (anchorPoint != null) {
            val anchorPointValue = anchorPoint.value
            if (anchorPointValue != null && (anchorPointValue.x != 0f || anchorPointValue.y != 0f)) {
                matrix.preTranslate(-anchorPointValue.x, -anchorPointValue.y)
            }
        }

        return matrix
    }

    private fun clearSkewValues() {
        for (i in 0..8) {
            skewValues!![i] = 0f
        }
    }

    /**
     * TODO: see if we can use this for the main [.getMatrix] method.
     */
    fun getMatrixForRepeater(amount: Float): Matrix {
        val position = if (this.position == null) null else position!!.value
        val scale = if (this.scale == null) null else scale!!.value

        matrix.reset()
        if (position != null) {
            matrix.preTranslate(position.x * amount, position.y * amount)
        }
        if (scale != null) {
            matrix.preScale(
                scale.scaleX.pow(amount),
                scale.scaleY.pow(amount)
            )
        }
        if (this.rotation != null) {
            val rotation = rotation!!.value
            val anchorPoint = if (this.anchorPoint == null) null else anchorPoint!!.value
            matrix.preRotate(rotation * amount, anchorPoint?.x ?: 0f, anchorPoint?.y ?: 0f)
        }

        return matrix
    }

    /**
     * Returns whether the callback was applied.
     */
    fun <T> applyValueCallback(property: T, callback: LottieValueCallback<T>?): Boolean {
        if (property === LottieProperty.TRANSFORM_ANCHOR_POINT) {
            if (anchorPoint == null) {
                anchorPoint = ValueCallbackKeyframeAnimation(callback as LottieValueCallback<PointF>?, PointF())
            } else {
                anchorPoint!!.setValueCallback(callback as LottieValueCallback<PointF>?)
            }
        } else if (property === LottieProperty.TRANSFORM_POSITION) {
            if (position == null) {
                position = ValueCallbackKeyframeAnimation(callback as LottieValueCallback<PointF>?, PointF())
            } else {
                position!!.setValueCallback(callback as LottieValueCallback<PointF>?)
            }
        } else if (property == LottieProperty.TRANSFORM_POSITION_X && position is SplitDimensionPathKeyframeAnimation) {
            (position as SplitDimensionPathKeyframeAnimation).setXValueCallback(callback as LottieValueCallback<Float>)
        } else if (property == LottieProperty.TRANSFORM_POSITION_Y && position is SplitDimensionPathKeyframeAnimation) {
            (position as SplitDimensionPathKeyframeAnimation).setYValueCallback(callback as LottieValueCallback<Float>)
        } else if (property === LottieProperty.TRANSFORM_SCALE) {
            if (scale == null) {
                scale = ValueCallbackKeyframeAnimation(callback as LottieValueCallback<ScaleXY>?, ScaleXY())
            } else {
                scale!!.setValueCallback(callback as LottieValueCallback<ScaleXY>?)
            }
        } else if (property == LottieProperty.TRANSFORM_ROTATION) {
            if (rotation == null) {
                rotation = ValueCallbackKeyframeAnimation(callback as LottieValueCallback<Float>, 0f)
            } else {
                rotation!!.setValueCallback(callback as LottieValueCallback<Float>?)
            }
        } else if (property == LottieProperty.TRANSFORM_OPACITY) {
            if (opacity == null) {
                opacity = ValueCallbackKeyframeAnimation(callback as LottieValueCallback<Int>?, 100)
            } else {
                opacity!!.setValueCallback(callback as LottieValueCallback<Int>?)
            }
        } else if (property == LottieProperty.TRANSFORM_START_OPACITY) {
            if (startOpacity == null) {
                startOpacity = ValueCallbackKeyframeAnimation<Any, Float>(callback as LottieValueCallback<Float>?, 100f)
            } else {
                startOpacity!!.setValueCallback(callback as LottieValueCallback<Float>?)
            }
        } else if (property == LottieProperty.TRANSFORM_END_OPACITY) {
            if (endOpacity == null) {
                endOpacity = ValueCallbackKeyframeAnimation<Any, Float>(callback as LottieValueCallback<Float>?, 100f)
            } else {
                endOpacity!!.setValueCallback(callback as LottieValueCallback<Float>?)
            }
        } else if (property == LottieProperty.TRANSFORM_SKEW) {
            if (skew == null) {
                skew = FloatKeyframeAnimation(listOf(Keyframe(0f)))
            }
            skew!!.setValueCallback(callback as LottieValueCallback<Float>?)
        } else if (property == LottieProperty.TRANSFORM_SKEW_ANGLE) {
            if (skewAngle == null) {
                skewAngle = FloatKeyframeAnimation(listOf(Keyframe(0f)))
            }
            skewAngle!!.setValueCallback(callback as LottieValueCallback<Float>?)
        } else {
            return false
        }
        return true
    }
}
