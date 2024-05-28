package com.airbnb.lottie.animation.keyframe

import com.airbnb.lottie.value.Keyframe
import com.airbnb.lottie.value.LottieValueCallback

class ValueCallbackKeyframeAnimation<K : Any, A> @JvmOverloads constructor(
    valueCallback: LottieValueCallback<A>?,
    valueCallbackValue: A? = null
) : BaseKeyframeAnimation<K, A>(emptyList()) {

    private val valueCallbackValue: A?

    init {
        setValueCallback(valueCallback)
        this.valueCallbackValue = valueCallbackValue
    }

    override fun setProgress(progress: Float) {
        this._progress = progress
    }

    /**
     * If this doesn't return 1, then [.setProgress] will always clamp the progress
     * to 0.
     */
    override val endProgress: Float
        get() = 1f

    override fun notifyListeners() {
        if (this.valueCallback != null) {
            super.notifyListeners()
        }
    }

    override val value: A
        get() = valueCallback?.getValueInternal(0f, 0f, valueCallbackValue, valueCallbackValue, getProgress(), getProgress(), getProgress())!!


    public override fun getValue(keyframe: Keyframe<K>, keyframeProgress: Float): A {
        return value
    }
}
