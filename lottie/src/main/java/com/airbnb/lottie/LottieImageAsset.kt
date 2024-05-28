package com.airbnb.lottie

import android.graphics.Bitmap
import androidx.annotation.RestrictTo

/**
 * Data class describing an image asset embedded in a Lottie json file.
 */
class LottieImageAsset @RestrictTo(RestrictTo.Scope.LIBRARY) constructor(
    @JvmField val width: Int, @JvmField val height: Int,
    /**
     * The reference id in the json file.
     */
    @JvmField val id: String, @JvmField val fileName: String, @get:Suppress("unused") val dirName: String
) {
    /**
     * Returns the bitmap that has been stored for this image asset if one was explicitly set.
     */
    /**
     * Permanently sets the bitmap on this LottieImageAsset. This will:
     * 1) Overwrite any existing Bitmaps.
     * 2) Apply to *all* animations that use this LottieComposition.
     *
     * If you only want to replace the bitmap for this animation, use dynamic properties
     * with [LottieProperty.IMAGE].
     */
    /**
     * Pre-set a bitmap for this asset
     */
    @JvmField
    var bitmap: Bitmap? = null

    /**
     * Returns a new [LottieImageAsset] with the same properties as this one but with the
     * dimensions and bitmap scaled.
     */
    fun copyWithScale(scale: Float): LottieImageAsset {
        val newAsset = LottieImageAsset((width * scale).toInt(), (height * scale).toInt(), id, fileName, dirName)
        if (bitmap != null) {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap!!, newAsset.width, newAsset.height, true)
            newAsset.bitmap = scaledBitmap
        }
        return newAsset
    }

    /**
     * Returns whether this asset has an embedded Bitmap or whether the fileName is a base64 encoded bitmap.
     */
    fun hasBitmap(): Boolean {
        return bitmap != null || (fileName.startsWith("data:") && fileName.indexOf("base64,") > 0)
    }
}
