package com.airbnb.lottie.value

import com.airbnb.lottie.utils.MiscUtils

/**
 * [LottieValueCallback] that provides a value offset from the original animation
 * rather than an absolute value.
 */
@Suppress("unused")
class LottieRelativeFloatValueCallback : LottieValueCallback<Float> {
    constructor()

    constructor(staticValue: Float) : super(staticValue)

    override fun getValue(frameInfo: LottieFrameInfo<Float>): Float {
        val originalValue = MiscUtils.lerp(
            frameInfo.startValue!!,
            frameInfo.endValue!!,
            frameInfo.interpolatedKeyframeProgress
        )
        val offset = getOffset(frameInfo)
        return originalValue + offset
    }

    fun getOffset(frameInfo: LottieFrameInfo<Float>): Float {
        return requireNotNull(value) {
            "You must provide a static value in the constructor " +
                    ", call setValue, or override getValue."
        }
    }
}
