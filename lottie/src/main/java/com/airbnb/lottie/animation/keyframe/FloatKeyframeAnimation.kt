package com.airbnb.lottie.animation.keyframe

import com.airbnb.lottie.utils.MiscUtils.lerp
import com.airbnb.lottie.value.Keyframe

class FloatKeyframeAnimation(keyframes: List<Keyframe<Float>>) : KeyframeAnimation<Float>(keyframes) {
    override fun getValue(keyframe: Keyframe<Float>, keyframeProgress: Float): Float {
        return getFloatValue(keyframe, keyframeProgress)
    }

    /**
     * Optimization to avoid autoboxing.
     */
    fun getFloatValue(keyframe: Keyframe<Float>, keyframeProgress: Float): Float {
        check(!(keyframe.startValue == null || keyframe.endValue == null)) { "Missing values for keyframe." }

        if (valueCallback != null) {
            val value = valueCallback!!.getValueInternal(
                keyframe.startFrame, keyframe.endFrame!!,
                keyframe.startValue, keyframe.endValue,
                keyframeProgress, linearCurrentKeyframeProgress, getProgress()
            )
            if (value != null) {
                return value
            }
        }

        return lerp(keyframe.startValueFloat, keyframe.endValueFloat, keyframeProgress)
    }

    val floatValue: Float
        /**
         * Optimization to avoid autoboxing.
         */
        get() = getFloatValue(currentKeyframe, interpolatedCurrentKeyframeProgress)
}
