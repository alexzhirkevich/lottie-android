package com.airbnb.lottie.model.animatable

import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.IntegerKeyframeAnimation
import com.airbnb.lottie.value.Keyframe

class AnimatableIntegerValue(
    keyframes: List<Keyframe<Int>>
) : BaseAnimatableValue<Int, Int>(keyframes) {
    override fun createAnimation(): BaseKeyframeAnimation<Int, Int> {
        return IntegerKeyframeAnimation(keyframes)
    }
}
