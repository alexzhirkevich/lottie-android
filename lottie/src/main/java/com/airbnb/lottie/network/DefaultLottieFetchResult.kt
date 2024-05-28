package com.airbnb.lottie.network

import androidx.annotation.RestrictTo
import com.airbnb.lottie.utils.Logger.warning
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection

@RestrictTo(RestrictTo.Scope.LIBRARY)
class DefaultLottieFetchResult(
    private val connection: HttpURLConnection) : LottieFetchResult {
    override val isSuccessful: Boolean
        get() = try {
            connection.responseCode / 100 == 2
        } catch (e: IOException) {
            false
        }


    @Throws(IOException::class)
    override fun bodyByteStream(): InputStream {
        return connection.inputStream
    }

    override fun contentType(): String? {
        return connection.contentType
    }

    override fun error(): String? {
        try {
            return if (isSuccessful) null else "Unable to fetch " + connection.url + ". Failed with " + connection.responseCode + "\n" + getErrorFromConnection(
                connection
            )
        } catch (e: IOException) {
            warning("get error failed ", e)
            return e.message
        }
    }

    override fun close() {
        connection.disconnect()
    }

    @Throws(IOException::class)
    private fun getErrorFromConnection(connection: HttpURLConnection): String {
        val r = BufferedReader(InputStreamReader(connection.errorStream))
        val error = StringBuilder()
        var line: String?

        try {
            while ((r.readLine().also { line = it }) != null) {
                error.append(line).append('\n')
            }
        } finally {
            try {
                r.close()
            } catch (e: Exception) {
                // Do nothing.
            }
        }
        return error.toString()
    }
}
