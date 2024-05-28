package com.airbnb.lottie

/**
 * @see LottieCompositionFactory
 *
 * @see LottieResult
 */
@Deprecated("")
interface OnCompositionLoadedListener {
    /**
     * Composition will be null if there was an error loading it. Check logcat for more details.
     */
    fun onCompositionLoaded(composition: LottieComposition?)
}
