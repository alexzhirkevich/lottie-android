package com.airbnb.lottie

/**
 * Receive a result with either the value or exception for a [LottieTask]
 */
fun interface LottieListener<T> {
    fun onResult(result: T)
}
