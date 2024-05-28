package com.airbnb.lottie.utils

import android.util.Log
import com.airbnb.lottie.L
import com.airbnb.lottie.LottieLogger

/**
 * Default logger.
 * Warnings with same message will only be logged once.
 */
class LogcatLogger : LottieLogger {
    override fun debug(message: String?) {
        debug(message, null)
    }

    override fun debug(message: String?, exception: Throwable?) {
        if (L.DBG) {
            Log.d(L.TAG, message, exception)
        }
    }

    override fun warning(message: String?) {
        warning(message, null)
    }

    override fun warning(message: String?, exception: Throwable?) {
        if (loggedMessages.contains(message)) {
            return
        }

        Log.w(L.TAG, message, exception)

        loggedMessages.add(message)
    }

    override fun error(message: String?, exception: Throwable?) {
        if (L.DBG) {
            Log.d(L.TAG, message, exception)
        }
    }

    companion object {
        /**
         * Set to ensure that we only log each message one time max.
         */
        private val loggedMessages: MutableSet<String?> = HashSet()
    }
}
