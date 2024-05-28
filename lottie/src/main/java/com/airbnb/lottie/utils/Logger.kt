package com.airbnb.lottie.utils

import com.airbnb.lottie.LottieLogger

/**
 * Singleton object for logging. If you want to provide a custom logger implementation,
 * implements LottieLogger interface in a custom class and replace Logger.instance
 */
object Logger {
    private var INSTANCE: LottieLogger = LogcatLogger()

    fun setInstance(instance: LottieLogger) {
        INSTANCE = instance
    }

    @JvmStatic
    fun debug(message: String?) {
        INSTANCE.debug(message)
    }

    fun debug(message: String?, exception: Throwable?) {
        INSTANCE.debug(message, exception)
    }

    @JvmStatic
    fun warning(message: String?) {
        INSTANCE.warning(message)
    }

    @JvmStatic
    fun warning(message: String?, exception: Throwable?) {
        INSTANCE.warning(message, exception)
    }

    fun error(message: String?, exception: Throwable?) {
        INSTANCE.error(message, exception)
    }
}
