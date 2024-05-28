package com.airbnb.lottie

import android.os.Build

/**
 * Controls how Lottie should render.
 * Defaults to [RenderMode.AUTOMATIC].
 *
 * @see LottieAnimationView.setRenderMode
 */
enum class RenderMode {
    AUTOMATIC,
    HARDWARE,
    SOFTWARE;

    fun useSoftwareRendering(sdkInt: Int, hasDashPattern: Boolean, numMasksAndMattes: Int): Boolean {
        when (this) {
            HARDWARE -> return false
            SOFTWARE -> return true
            AUTOMATIC -> {
                if (hasDashPattern && sdkInt < Build.VERSION_CODES.P) {
                    // Hardware acceleration didn't support dash patterns until Pie.
                    return true
                } else if (numMasksAndMattes > 4) {
                    // This was chosen somewhat arbitrarily by trying a handful of animations.
                    // Animations with zero or few masks or mattes tend to perform much better with hardware
                    // acceleration. However, if there are many masks or mattes, it *may* perform worse.
                    // If you are hitting this case with AUTOMATIC set, please manually verify which one
                    // performs better.
                    return true
                }
                // There have been many reported crashes from many device that are running Nougat or below.
                // These devices also support far fewer hardware accelerated canvas operations.
                // https://developer.android.com/guide/topics/graphics/hardware-accel#unsupported
                return sdkInt <= Build.VERSION_CODES.N_MR1
            }

            else -> {
                if (hasDashPattern && sdkInt < Build.VERSION_CODES.P) {
                    return true
                } else if (numMasksAndMattes > 4) {
                    return true
                }
                return sdkInt <= Build.VERSION_CODES.N_MR1
            }
        }
    }
}
