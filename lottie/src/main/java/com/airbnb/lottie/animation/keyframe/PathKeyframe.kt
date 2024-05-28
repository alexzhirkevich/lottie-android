package com.airbnb.lottie.animation.keyframe

import android.graphics.Path
import android.graphics.PointF
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.utils.Utils.createPath
import com.airbnb.lottie.value.Keyframe

class PathKeyframe(composition: LottieComposition?, private val pointKeyFrame: Keyframe<PointF>) : Keyframe<PointF?>(
    composition,
    pointKeyFrame.startValue,
    pointKeyFrame.endValue,
    pointKeyFrame.interpolator,
    pointKeyFrame.xInterpolator,
    pointKeyFrame.yInterpolator,
    pointKeyFrame.startFrame,
    pointKeyFrame.endFrame
) {
    /**
     * This will be null if the startValue and endValue are the same.
     */
    var path: Path? = null
        private set

    init {
        createPath()
    }

    fun createPath() {
        // This must use equals(float, float) because PointF didn't have an equals(PathF) method
        // until KitKat...
        val equals = endValue != null && startValue != null &&
                startValue.equals(endValue!!.x, endValue!!.y)
        if (startValue != null && endValue != null && !equals) {
            path = createPath(startValue, endValue!!, pointKeyFrame.pathCp1, pointKeyFrame.pathCp2)
        }
    }
}
