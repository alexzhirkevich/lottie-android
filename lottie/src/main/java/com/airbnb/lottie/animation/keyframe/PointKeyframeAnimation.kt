package com.airbnb.lottie.animation.keyframe

import android.graphics.PointF
import com.airbnb.lottie.value.Keyframe

class PointKeyframeAnimation(keyframes: List<Keyframe<PointF>>) : KeyframeAnimation<PointF>(keyframes) {
    private val point = PointF()

    override fun getValue(keyframe: Keyframe<PointF>, keyframeProgress: Float): PointF {
        return getValue(keyframe, keyframeProgress, keyframeProgress, keyframeProgress)
    }

    protected override fun getValue(
        keyframe: Keyframe<PointF>,
        linearKeyframeProgress: Float,
        xKeyframeProgress: Float,
        yKeyframeProgress: Float
    ): PointF {
        check(!(keyframe.startValue == null || keyframe.endValue == null)) { "Missing values for keyframe." }

        val startPoint = keyframe.startValue
        val endPoint = keyframe.endValue

        if (valueCallback != null) {
            val value = valueCallback!!.getValueInternal(
                keyframe.startFrame, keyframe.endFrame!!, startPoint,
                endPoint, linearKeyframeProgress, linearCurrentKeyframeProgress, getProgress()
            )
            if (value != null) {
                return value
            }
        }

        point[startPoint.x + xKeyframeProgress * (endPoint!!.x - startPoint.x)] = startPoint.y + yKeyframeProgress * (endPoint.y - startPoint.y)
        return point
    }
}
