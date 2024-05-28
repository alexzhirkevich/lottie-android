package com.airbnb.lottie.model.animatable

import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.value.Keyframe

interface AnimatableValue<K : Any, A : Any> {
    val keyframes: List<Keyframe<K>>

    val isStatic: Boolean

    fun createAnimation(): BaseKeyframeAnimation<K, A>?
}
