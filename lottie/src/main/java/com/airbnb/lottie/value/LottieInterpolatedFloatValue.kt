package com.airbnb.lottie.value

import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import com.airbnb.lottie.utils.MiscUtils

@Suppress("unused")
class LottieInterpolatedFloatValue(
    startValue: Float,
    endValue: Float,
    interpolator: Interpolator = LinearInterpolator()
) : LottieInterpolatedValue<Float>(startValue, endValue, interpolator) {

    override fun interpolateValue(startValue: Float, endValue: Float, progress: Float): Float {
        return MiscUtils.lerp(startValue, endValue, progress)
    }
}
