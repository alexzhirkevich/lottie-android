package com.airbnb.lottie

import android.graphics.Typeface

/**
 * Delegate to handle the loading of fonts that are not packaged in the assets of your app or don't
 * have the same file name.
 *
 * @see LottieDrawable.setFontAssetDelegate
 */
@Suppress("unused")
open class FontAssetDelegate {
    /**
     * Override this if you want to return a Typeface from a font family.
     */
    fun fetchFont(fontFamily: String?): Typeface? {
        return null
    }

    /**
     * Override this if you want to return a Typeface from a font family and style.
     */
    open fun fetchFont(fontFamily: String?, fontStyle: String?, fontName: String?): Typeface? {
        return null
    }

    /**
     * Override this if you want to specify the asset path for a given font family.
     */
    fun getFontPath(fontFamily: String?): String? {
        return null
    }

    /**
     * Override this if you want to specify the asset path for a given font family and style.
     */
    open fun getFontPath(fontFamily: String?, fontStyle: String?, fontName: String?): String? {
        return null
    }
}
