package com.airbnb.lottie.model.animatable

import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ScaleKeyframeAnimation
import com.airbnb.lottie.value.Keyframe
import com.airbnb.lottie.value.ScaleXY

class AnimatableScaleValue : BaseAnimatableValue<ScaleXY, ScaleXY> {
    constructor(value: ScaleXY) : super(value)

    constructor(keyframes: List<Keyframe<ScaleXY>>) : super(keyframes)

    override fun createAnimation(): BaseKeyframeAnimation<ScaleXY, ScaleXY> {
        return ScaleKeyframeAnimation(keyframes)
    }
}
