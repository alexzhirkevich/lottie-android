package com.airbnb.lottie.animation.keyframe

import com.airbnb.lottie.utils.MiscUtils.lerp
import com.airbnb.lottie.value.Keyframe

class IntegerKeyframeAnimation(keyframes: List<Keyframe<Int>>) : KeyframeAnimation<Int>(keyframes) {
    override fun getValue(keyframe: Keyframe<Int>, keyframeProgress: Float): Int {
        return getIntValue(keyframe, keyframeProgress)
    }

    /**
     * Optimization to avoid autoboxing.
     */
    fun getIntValue(keyframe: Keyframe<Int>, keyframeProgress: Float): Int {
        checkNotNull(keyframe.startValue) { "Missing values for keyframe." }

        val endValue = if (keyframe.endValue == null) keyframe.startValueInt else keyframe.endValueInt

        if (valueCallback != null) {
            val value = valueCallback!!.getValueInternal(
                keyframe.startFrame, keyframe.endFrame!!,
                keyframe.startValue, endValue,
                keyframeProgress, linearCurrentKeyframeProgress, getProgress()
            )
            if (value != null) {
                return value
            }
        }

        return lerp(keyframe.startValueInt, endValue, keyframeProgress)
    }

    val intValue: Int
        /**
         * Optimization to avoid autoboxing.
         */
        get() = getIntValue(currentKeyframe, interpolatedCurrentKeyframeProgress)
}
