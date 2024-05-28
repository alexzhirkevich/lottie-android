package com.airbnb.lottie.model.content

import com.airbnb.lottie.utils.GammaEvaluator.evaluate
import com.airbnb.lottie.utils.MiscUtils.lerp
import java.util.Arrays

class GradientColor(
    @JvmField val positions: FloatArray,
    @JvmField val colors: IntArray
) {
    val size: Int
        get() = colors.size

    fun lerp(gc1: GradientColor, gc2: GradientColor, progress: Float) {
        // Fast return in case start and end is the same
        // or if progress is at start/end or out of [0,1] bounds
        if (gc1 == gc2) {
            copyFrom(gc1)
            return
        } else if (progress <= 0f) {
            copyFrom(gc1)
            return
        } else if (progress >= 1f) {
            copyFrom(gc2)
            return
        }

        require(gc1.colors.size == gc2.colors.size) {
            "Cannot interpolate between gradients. Lengths vary (" +
                    gc1.colors.size + " vs " + gc2.colors.size + ")"
        }

        for (i in gc1.colors.indices) {
            positions[i] = lerp(gc1.positions[i], gc2.positions[i], progress)
            colors[i] = evaluate(progress, gc1.colors[i], gc2.colors[i])
        }

        // Not all keyframes that this GradientColor are used for will have the same length.
        // AnimatableGradientColorValue.ensureInterpolatableKeyframes may add extra positions
        // for some keyframes but not others to ensure that it is interpolatable.
        // If there are extra positions here, just duplicate the last value in the gradient.
        for (i in gc1.colors.size until positions.size) {
            positions[i] = positions[gc1.colors.size - 1]
            colors[i] = colors[gc1.colors.size - 1]
        }
    }

    fun copyWithPositions(positions: FloatArray): GradientColor {
        val colors = IntArray(positions.size)
        for (i in positions.indices) {
            colors[i] = getColorForPosition(positions[i])
        }
        return GradientColor(positions, colors)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as GradientColor
        return positions.contentEquals(that.positions) && colors.contentEquals(that.colors)
    }

    override fun hashCode(): Int {
        var result = positions.contentHashCode()
        result = 31 * result + colors.contentHashCode()
        return result
    }

    private fun getColorForPosition(position: Float): Int {
        val existingIndex = Arrays.binarySearch(positions, position)
        if (existingIndex >= 0) {
            return colors[existingIndex]
        }
        // binarySearch returns -insertionPoint - 1 if it is not found.
        val insertionPoint = -(existingIndex + 1)
        if (insertionPoint == 0) {
            return colors[0]
        } else if (insertionPoint == colors.size - 1) {
            return colors[colors.size - 1]
        }
        val startPosition = positions[insertionPoint - 1]
        val endPosition = positions[insertionPoint]
        val startColor = colors[insertionPoint - 1]
        val endColor = colors[insertionPoint]

        val fraction = (position - startPosition) / (endPosition - startPosition)
        return evaluate(fraction, startColor, endColor)
    }

    private fun copyFrom(other: GradientColor) {
        for (i in other.colors.indices) {
            positions[i] = other.positions[i]
            colors[i] = other.colors[i]
        }
    }
}
