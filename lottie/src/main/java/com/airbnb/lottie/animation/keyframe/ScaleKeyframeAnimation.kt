package com.airbnb.lottie.animation.keyframe

import com.airbnb.lottie.utils.MiscUtils.lerp
import com.airbnb.lottie.value.Keyframe
import com.airbnb.lottie.value.ScaleXY

class ScaleKeyframeAnimation(keyframes: List<Keyframe<ScaleXY>>) : KeyframeAnimation<ScaleXY>(keyframes) {
    private val scaleXY = ScaleXY()

    override fun getValue(keyframe: Keyframe<ScaleXY>, keyframeProgress: Float): ScaleXY {
        check(!(keyframe.startValue == null || keyframe.endValue == null)) { "Missing values for keyframe." }
        val startTransform = keyframe.startValue
        val endTransform = keyframe.endValue

        if (valueCallback != null) {
            val value = valueCallback!!.getValueInternal(
                keyframe.startFrame, keyframe.endFrame!!,
                startTransform, endTransform,
                keyframeProgress, linearCurrentKeyframeProgress, getProgress()
            )
            if (value != null) {
                return value
            }
        }

        scaleXY.set(
            lerp(startTransform.scaleX, endTransform!!.scaleX, keyframeProgress),
            lerp(startTransform.scaleY, endTransform.scaleY, keyframeProgress)
        )
        return scaleXY
    }
}
