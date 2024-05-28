package com.airbnb.lottie.animation

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.os.LocaleList
import com.airbnb.lottie.utils.MiscUtils

/**
 * Custom paint that doesn't set text locale.
 * It takes ~1ms on initialization and isn't needed so removing it speeds up
 * setComposition.
 */
class LPaint : Paint {
    constructor() : super()

    constructor(flags: Int) : super(flags)

    constructor(porterDuffMode: PorterDuff.Mode?) : super() {
        xfermode = PorterDuffXfermode(porterDuffMode)
    }

    constructor(flags: Int, porterDuffMode: PorterDuff.Mode?) : super(flags) {
        xfermode = PorterDuffXfermode(porterDuffMode)
    }

    override fun setTextLocales(locales: LocaleList) {
        // Do nothing.
    }

    /**
     * Overrides [android.graphics.Paint.setAlpha] to avoid
     * unnecessary [ColorSpace$Named[] ][android.graphics.ColorSpace.Named]
     * allocations when calling this method in Android 29 or lower.
     *
     * @param alpha set the alpha component [0..255] of the paint's color.
     */
    override fun setAlpha(alpha: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val color = color
            setColor((MiscUtils.clamp(alpha, 0, 255) shl 24) or (color and 0xFFFFFF))
        } else {
            super.setAlpha(MiscUtils.clamp(alpha, 0, 255))
        }
    }
}
