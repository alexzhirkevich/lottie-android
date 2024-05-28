package com.airbnb.lottie.value

import android.graphics.PointF
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import com.airbnb.lottie.utils.MiscUtils

@Suppress("unused")
class LottieInterpolatedPointValue(
    startValue: PointF, endValue: PointF, interpolator: Interpolator = LinearInterpolator()
) : LottieInterpolatedValue<PointF>(startValue, endValue, interpolator) {
    private val point = PointF()


    override fun interpolateValue(startValue: PointF, endValue: PointF, progress: Float): PointF {
        point[MiscUtils.lerp(startValue.x, endValue.x, progress)] = MiscUtils.lerp(startValue.y, endValue.y, progress)
        return point
    }
}
