package com.airbnb.lottie.value

import com.airbnb.lottie.utils.MiscUtils

/**
 * [LottieValueCallback] that provides a value offset from the original animation
 * rather than an absolute value.
 */
@Suppress("unused")
class LottieRelativeIntegerValueCallback : LottieValueCallback<Int>() {
    override fun getValue(frameInfo: LottieFrameInfo<Int>): Int {
        val originalValue = MiscUtils.lerp(
            frameInfo.startValue!!,
            frameInfo.endValue!!,
            frameInfo.interpolatedKeyframeProgress
        )
        val newValue = getOffset(frameInfo)
        return originalValue + newValue
    }

    /**
     * Override this to provide your own offset on every frame.
     */
    fun getOffset(frameInfo: LottieFrameInfo<Int>): Int {
        return requireNotNull(value) {
            "You must provide a static value in the constructor " +
                    ", call setValue, or override getValue."
        }
    }
}
