package com.airbnb.lottie.value

import android.graphics.PointF
import com.airbnb.lottie.utils.MiscUtils

/**
 * [LottieValueCallback] that provides a value offset from the original animation
 * rather than an absolute value.
 */
@Suppress("unused")
class LottieRelativePointValueCallback : LottieValueCallback<PointF> {
    private val point = PointF()

    constructor()

    constructor(staticValue: PointF) : super(staticValue)

    override fun getValue(frameInfo: LottieFrameInfo<PointF>): PointF {
        point.set(
            MiscUtils.lerp(
                frameInfo.startValue!!.x,
                frameInfo.endValue!!.x,
                frameInfo.interpolatedKeyframeProgress
            ),
            MiscUtils.lerp(
                frameInfo.startValue!!.y,
                frameInfo.endValue!!.y,
                frameInfo.interpolatedKeyframeProgress
            )
        )

        val offset = getOffset(frameInfo)
        point.offset(offset.x, offset.y)
        return point
    }

    /**
     * Override this to provide your own offset on every frame.
     */
    fun getOffset(frameInfo: LottieFrameInfo<PointF>): PointF {
        return requireNotNull(value) {
            "You must provide a static value in the constructor " +
                    ", call setValue, or override getValue."
        }
    }
}
