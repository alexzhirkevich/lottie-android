package com.airbnb.lottie

/**
 * Register this listener via [LottieCompositionFactory.registerLottieTaskIdleListener].
 *
 * Can be used to create an espresso idle resource. Refer to [LottieCompositionFactory.registerLottieTaskIdleListener]
 * for more information.
 */
interface LottieTaskIdleListener {
    fun onIdleChanged(idle: Boolean)
}
