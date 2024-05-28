package com.airbnb.lottie.model.animatable

import android.graphics.PointF
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.PointKeyframeAnimation
import com.airbnb.lottie.value.Keyframe

class AnimatablePointValue(
    keyframes: List<Keyframe<PointF>>
) : BaseAnimatableValue<PointF, PointF>(keyframes) {
    override fun createAnimation(): BaseKeyframeAnimation<PointF, PointF> {
        return PointKeyframeAnimation(keyframes)
    }
}
