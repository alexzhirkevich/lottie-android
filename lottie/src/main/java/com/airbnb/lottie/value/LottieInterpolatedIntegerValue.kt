package com.airbnb.lottie.value

import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import com.airbnb.lottie.utils.MiscUtils

@Suppress("unused")
class LottieInterpolatedIntegerValue(
    startValue: Int, endValue: Int, interpolator: Interpolator = LinearInterpolator()
) : LottieInterpolatedValue<Int>(startValue, endValue, interpolator) {

    override fun interpolateValue(startValue: Int, endValue: Int, progress: Float): Int {
        return MiscUtils.lerp(startValue, endValue, progress)
    }
}
