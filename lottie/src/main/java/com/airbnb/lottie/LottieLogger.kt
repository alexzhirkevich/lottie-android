package com.airbnb.lottie

/**
 * Give ability to integrators to provide another logging mechanism.
 */
interface LottieLogger {
    fun debug(message: String?)

    fun debug(message: String?, exception: Throwable?)

    fun warning(message: String?)

    fun warning(message: String?, exception: Throwable?)

    fun error(message: String?, exception: Throwable?)
}
