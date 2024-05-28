package com.airbnb.lottie.model.animatable

import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.GradientColorKeyframeAnimation
import com.airbnb.lottie.model.content.GradientColor
import com.airbnb.lottie.value.Keyframe
import java.util.Arrays

class AnimatableGradientColorValue(
    keyframes: MutableList<Keyframe<GradientColor>>
) : BaseAnimatableValue<GradientColor, GradientColor>(
    ensureInterpolatableKeyframes(keyframes)
) {
    override fun createAnimation(): BaseKeyframeAnimation<GradientColor, GradientColor> {
        return GradientColorKeyframeAnimation(keyframes)
    }

    companion object {
        private fun ensureInterpolatableKeyframes(keyframes: MutableList<Keyframe<GradientColor>>): List<Keyframe<GradientColor>> {
            for (i in keyframes.indices) {
                keyframes[i] = ensureInterpolatableKeyframe(keyframes[i])
            }
            return keyframes
        }

        private fun ensureInterpolatableKeyframe(keyframe: Keyframe<GradientColor>): Keyframe<GradientColor> {
            val startValue = keyframe.startValue
            val endValue = keyframe.endValue
            if (startValue == null || endValue == null || startValue.positions.size == endValue.positions.size) {
                return keyframe
            }
            val mergedPositions = mergePositions(startValue.positions, endValue.positions)
            // The start/end has opacity stops which required adding extra positions in between the existing colors.
            return keyframe.copyWith(startValue.copyWithPositions(mergedPositions), endValue.copyWithPositions(mergedPositions))
        }

        @JvmStatic
        fun mergePositions(startPositions: FloatArray, endPositions: FloatArray): FloatArray {
            val mergedArray = FloatArray(startPositions.size + endPositions.size)
            System.arraycopy(startPositions, 0, mergedArray, 0, startPositions.size)
            System.arraycopy(endPositions, 0, mergedArray, startPositions.size, endPositions.size)
            Arrays.sort(mergedArray)
            var uniqueValues = 0
            var lastValue = Float.NaN
            for (i in mergedArray.indices) {
                if (mergedArray[i] != lastValue) {
                    mergedArray[uniqueValues] = mergedArray[i]
                    uniqueValues++
                    lastValue = mergedArray[i]
                }
            }
            return Arrays.copyOfRange(mergedArray, 0, uniqueValues)
        }
    }
}
