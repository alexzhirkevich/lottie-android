package com.airbnb.lottie.model.animatable

import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ColorKeyframeAnimation
import com.airbnb.lottie.value.Keyframe

class AnimatableColorValue(
    keyframes: List<Keyframe<Int>>
) : BaseAnimatableValue<Int, Int>(keyframes) {
    override fun createAnimation(): BaseKeyframeAnimation<Int, Int> {
        return ColorKeyframeAnimation(keyframes)
    }
}
