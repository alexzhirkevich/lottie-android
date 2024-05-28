package com.airbnb.lottie.model.animatable

import android.graphics.Path
import com.airbnb.lottie.animation.keyframe.ShapeKeyframeAnimation
import com.airbnb.lottie.model.content.ShapeData
import com.airbnb.lottie.value.Keyframe

class AnimatableShapeValue(
    keyframes: List<Keyframe<ShapeData>>
) : BaseAnimatableValue<ShapeData, Path>(keyframes) {
    override fun createAnimation(): ShapeKeyframeAnimation {
        return ShapeKeyframeAnimation(keyframes)
    }
}
