package com.airbnb.lottie.model.animatable

import android.graphics.PointF
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.SplitDimensionPathKeyframeAnimation
import com.airbnb.lottie.value.Keyframe

class AnimatableSplitDimensionPathValue(
    private val animatableXDimension: AnimatableFloatValue,
    private val animatableYDimension: AnimatableFloatValue
) : AnimatableValue<PointF, PointF> {
    override val keyframes: List<Keyframe<PointF>>
        get() {
            throw UnsupportedOperationException("Cannot call getKeyframes on AnimatableSplitDimensionPathValue.")
        }

    override val isStatic: Boolean
        get() = animatableXDimension.isStatic && animatableYDimension.isStatic

    override fun createAnimation(): BaseKeyframeAnimation<PointF, PointF> {
        return SplitDimensionPathKeyframeAnimation(
            animatableXDimension.createAnimation(), animatableYDimension.createAnimation()
        )
    }
}
