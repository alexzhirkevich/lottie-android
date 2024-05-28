package com.airbnb.lottie.animation.keyframe

import android.graphics.PathMeasure
import android.graphics.PointF
import com.airbnb.lottie.value.Keyframe

class PathKeyframeAnimation(keyframes: List<Keyframe<PointF>>) : KeyframeAnimation<PointF>(keyframes) {
    private val point = PointF()
    private val pos = FloatArray(2)
    private val tangent = FloatArray(2)
    private val pathMeasure = PathMeasure()
    private var pathMeasureKeyframe: PathKeyframe? = null

    override fun getValue(keyframe: Keyframe<PointF>, keyframeProgress: Float): PointF {
        val pathKeyframe = keyframe as PathKeyframe
        val path = pathKeyframe.path ?: return keyframe.startValue!!

        if (valueCallback != null) {
            val value = valueCallback!!.getValueInternal(
                pathKeyframe.startFrame, pathKeyframe.endFrame!!,
                pathKeyframe.startValue, pathKeyframe.endValue, linearCurrentKeyframeProgress,
                keyframeProgress, getProgress()
            )
            if (value != null) {
                return value
            }
        }

        if (pathMeasureKeyframe != pathKeyframe) {
            pathMeasure.setPath(path, false)
            pathMeasureKeyframe = pathKeyframe
        }

        // allow bounce easings to calculate positions outside the path
        // by using the tangent at the extremities
        val length = pathMeasure.length

        val distance = keyframeProgress * length
        pathMeasure.getPosTan(distance, pos, tangent)
        point[pos[0]] = pos[1]

        if (distance < 0) {
            point.offset(tangent[0] * distance, tangent[1] * distance)
        } else if (distance > length) {
            point.offset(tangent[0] * (distance - length), tangent[1] * (distance - length))
        }
        return point
    }
}
