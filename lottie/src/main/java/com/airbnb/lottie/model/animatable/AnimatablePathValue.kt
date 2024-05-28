package com.airbnb.lottie.model.animatable

import android.graphics.PointF
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.PathKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.PointKeyframeAnimation
import com.airbnb.lottie.value.Keyframe

class AnimatablePathValue(
    override val keyframes: List<Keyframe<PointF>>
) : AnimatableValue<PointF, PointF> {
    override val isStatic: Boolean
        get() = keyframes.size == 1 && keyframes[0].isStatic

    override fun createAnimation(): BaseKeyframeAnimation<PointF, PointF> {
        if (keyframes[0].isStatic) {
            return PointKeyframeAnimation(keyframes)
        }
        return PathKeyframeAnimation(keyframes)
    }
}
