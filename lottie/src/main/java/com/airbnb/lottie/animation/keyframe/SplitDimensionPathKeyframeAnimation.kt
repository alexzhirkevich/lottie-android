package com.airbnb.lottie.animation.keyframe

import android.graphics.PointF
import com.airbnb.lottie.value.Keyframe
import com.airbnb.lottie.value.LottieValueCallback

class SplitDimensionPathKeyframeAnimation(
    private val xAnimation: BaseKeyframeAnimation<Float, Float>,
    private val yAnimation: BaseKeyframeAnimation<Float, Float>
) : BaseKeyframeAnimation<PointF, PointF>(emptyList()) {
    private val point = PointF()
    private val pointWithCallbackValues = PointF()

    protected var _xValueCallback: LottieValueCallback<Float>? = null

    protected var _yValueCallback: LottieValueCallback<Float>? = null


    init {
        // We need to call an initial setProgress so point gets set with the initial value.
        setProgress(getProgress())
    }

    fun setXValueCallback(xValueCallback: LottieValueCallback<Float>?) {
        if (this._xValueCallback != null) {
            this._xValueCallback!!.setAnimation(null)
        }
        this._xValueCallback = xValueCallback
        xValueCallback?.setAnimation(this)
    }

    fun setYValueCallback(yValueCallback: LottieValueCallback<Float>?) {
        if (this._yValueCallback != null) {
            this._yValueCallback!!.setAnimation(null)
        }
        this._yValueCallback = yValueCallback
        yValueCallback?.setAnimation(this)
    }

    override fun setProgress(progress: Float) {
        xAnimation.setProgress(progress)
        yAnimation.setProgress(progress)
        point[xAnimation.value] = yAnimation.value
        for (i in listeners.indices) {
            listeners[i].onValueChanged()
        }
    }

    override val value: PointF
        get() = point

    override fun getValue(keyframe: Keyframe<PointF>, keyframeProgress: Float): PointF {
        var xCallbackValue: Float? = null
        var yCallbackValue: Float? = null

        if (_xValueCallback != null) {
            val xKeyframe = xAnimation.currentKeyframe
            if (xKeyframe != null) {
                val progress = xAnimation.interpolatedCurrentKeyframeProgress
                val endFrame = xKeyframe.endFrame
                xCallbackValue =
                    _xValueCallback!!.getValueInternal(
                        xKeyframe.startFrame, endFrame ?: xKeyframe.startFrame, xKeyframe.startValue,
                        xKeyframe.endValue, keyframeProgress, keyframeProgress, progress
                    )
            }
        }
        if (_yValueCallback != null) {
            val yKeyframe = yAnimation.currentKeyframe
            if (yKeyframe != null) {
                val progress = yAnimation.interpolatedCurrentKeyframeProgress
                val endFrame = yKeyframe.endFrame
                yCallbackValue =
                    _yValueCallback!!.getValueInternal(
                        yKeyframe.startFrame,
                        endFrame ?: yKeyframe.startFrame,
                        yKeyframe.startValue,
                        yKeyframe.endValue, keyframeProgress, keyframeProgress, progress
                    )
            }
        }

        if (xCallbackValue == null) {
            pointWithCallbackValues[point.x] = 0f
        } else {
            pointWithCallbackValues[xCallbackValue] = 0f
        }

        if (yCallbackValue == null) {
            pointWithCallbackValues[pointWithCallbackValues.x] = point.y
        } else {
            pointWithCallbackValues[pointWithCallbackValues.x] = yCallbackValue
        }

        return pointWithCallbackValues
    }
}
