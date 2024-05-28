package com.airbnb.lottie.network

import java.io.Closeable
import java.io.IOException
import java.io.InputStream

/**
 * The result of the operation of obtaining a Lottie animation
 */
interface LottieFetchResult : Closeable {
    /**
     * @return Is the operation successful
     */
    val isSuccessful: Boolean

    /**
     * @return Received content stream
     */
    @Throws(IOException::class)
    fun bodyByteStream(): InputStream

    /**
     * @return Type of content received
     */
    fun contentType(): String?

    /**
     * @return Operation error
     */
    fun error(): String?
}
