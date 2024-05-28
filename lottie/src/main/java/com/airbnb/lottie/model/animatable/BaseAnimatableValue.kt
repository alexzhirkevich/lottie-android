package com.airbnb.lottie.model.animatable

import com.airbnb.lottie.value.Keyframe

abstract class BaseAnimatableValue<V : Any, O : Any>(
    override val keyframes: List<Keyframe<V>>
) : AnimatableValue<V, O> {
    /**
     * Create a default static animatable path.
     */
    constructor(value: V) : this(listOf<Keyframe<V>>(Keyframe<V>(value)))

    override val isStatic: Boolean
        get() = keyframes.isEmpty() || (keyframes.size == 1 && keyframes[0].isStatic)

    override fun toString(): String {
        val sb = StringBuilder()
        if (keyframes.isNotEmpty()) {
            sb.append("values=").append(keyframes.toTypedArray().contentToString())
        }
        return sb.toString()
    }
}
