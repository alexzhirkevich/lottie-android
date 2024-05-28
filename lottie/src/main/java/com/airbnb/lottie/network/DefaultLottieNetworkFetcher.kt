package com.airbnb.lottie.network

import androidx.annotation.RestrictTo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@RestrictTo(RestrictTo.Scope.LIBRARY)
class DefaultLottieNetworkFetcher : LottieNetworkFetcher {
    @Throws(IOException::class)
    override fun fetchSync(url: String): LottieFetchResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()
        return DefaultLottieFetchResult(connection)
    }
}
