package com.airbnb.lottie.value

import androidx.annotation.RestrictTo

/**
 * Data class for use with [LottieValueCallback].
 * You should *not* hold a reference to the frame info parameter passed to your callback. It will be reused.
 */
class LottieFrameInfo<T> {
    var startFrame: Float = 0f
        private set
    var endFrame: Float = 0f
        private set
    var startValue: T? = null
        private set
    var endValue: T? = null
        private set
    var linearKeyframeProgress: Float = 0f
        private set
    var interpolatedKeyframeProgress: Float = 0f
        private set
    var overallProgress: Float = 0f
        private set

    fun set(
        startFrame: Float,
        endFrame: Float,
        startValue: T?,
        endValue: T?,
        linearKeyframeProgress: Float,
        interpolatedKeyframeProgress: Float,
        overallProgress: Float
    ): LottieFrameInfo<T> {
        this.startFrame = startFrame
        this.endFrame = endFrame
        this.startValue = startValue
        this.endValue = endValue
        this.linearKeyframeProgress = linearKeyframeProgress
        this.interpolatedKeyframeProgress = interpolatedKeyframeProgress
        this.overallProgress = overallProgress
        return this
    }
}
