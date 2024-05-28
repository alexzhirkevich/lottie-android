package com.airbnb.lottie.model.animatable

import com.airbnb.lottie.animation.keyframe.TextKeyframeAnimation
import com.airbnb.lottie.model.DocumentData
import com.airbnb.lottie.value.Keyframe

class AnimatableTextFrame(
    keyframes: List<Keyframe<DocumentData>>
) : BaseAnimatableValue<DocumentData, DocumentData>(keyframes) {
    override fun createAnimation(): TextKeyframeAnimation {
        return TextKeyframeAnimation(keyframes)
    }
}
