package com.airbnb.lottie.animation.keyframe

import com.airbnb.lottie.model.content.GradientColor
import com.airbnb.lottie.value.Keyframe
import kotlin.math.max

class GradientColorKeyframeAnimation(keyframes: List<Keyframe<GradientColor>>) : KeyframeAnimation<GradientColor>(keyframes) {
    private val gradientColor: GradientColor

    init {
        // Not all keyframes that this GradientColor are used for will have the same length.
        // AnimatableGradientColorValue.ensureInterpolatableKeyframes may add extra positions
        // for some keyframes but not others to ensure that it is interpolatable.
        // Ensure that there is enough space for the largest keyframe.
        var size = 0
        for (i in keyframes.indices) {
            val startValue = keyframes[i].startValue
            if (startValue != null) {
                size = max(size.toDouble(), startValue.size.toDouble()).toInt()
            }
        }
        gradientColor = GradientColor(FloatArray(size), IntArray(size))
    }

    override fun getValue(keyframe: Keyframe<GradientColor>, keyframeProgress: Float): GradientColor {
        gradientColor.lerp(keyframe.startValue!!, keyframe.endValue!!, keyframeProgress)
        return gradientColor
    }
}
