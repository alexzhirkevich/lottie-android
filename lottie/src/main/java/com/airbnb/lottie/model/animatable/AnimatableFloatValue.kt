package com.airbnb.lottie.model.animatable

import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.FloatKeyframeAnimation
import com.airbnb.lottie.value.Keyframe

class AnimatableFloatValue(
    keyframes: List<Keyframe<Float>>
) : BaseAnimatableValue<Float, Float>(keyframes) {
    override fun createAnimation(): BaseKeyframeAnimation<Float, Float> {
        return FloatKeyframeAnimation(keyframes)
    }
}
