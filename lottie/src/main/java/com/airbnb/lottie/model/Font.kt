package com.airbnb.lottie.model

import android.graphics.Typeface
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
class Font(
    @JvmField @get:Suppress("unused") val family: String,
    @JvmField val name: String,
    @JvmField val style: String,
    @get:Suppress("unused") val ascent: Float
) {
    @JvmField
    var typeface: Typeface? = null
}
