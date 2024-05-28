package com.airbnb.lottie.value

/**
 * Delegate interface for [LottieValueCallback]. This is helpful for the Kotlin API because you can use a SAM conversion to write the
 * callback as a single abstract method block like this:
 * animationView.addValueCallback(keyPath, LottieProperty.TRANSFORM_OPACITY) { 50 }
 */
fun interface SimpleLottieValueCallback<T> {
    fun getValue(frameInfo: LottieFrameInfo<T>): T
}
