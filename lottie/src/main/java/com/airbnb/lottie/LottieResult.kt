package com.airbnb.lottie

/**
 * Contains class to hold the resulting value of an async task or an exception if it failed.
 *
 *
 * Either value or exception will be non-null.
 */
class LottieResult<V> {
    @JvmField
    val value: V?
    @JvmField
    val exception: Throwable?

    constructor(value: V) {
        this.value = value
        exception = null
    }

    constructor(exception: Throwable?) {
        this.exception = exception
        value = null
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is LottieResult<*>) {
            return false
        }
        if (value != null && value == o.value) {
            return true
        }
        if (exception != null && o.exception != null) {
            return exception.toString() == exception.toString()
        }
        return false
    }

    override fun hashCode(): Int {
        return arrayOf(value, exception).contentHashCode()
    }
}
