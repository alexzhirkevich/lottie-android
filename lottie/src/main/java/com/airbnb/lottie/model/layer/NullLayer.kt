package com.airbnb.lottie.model.layer

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import com.airbnb.lottie.LottieDrawable

class NullLayer internal constructor(
    lottieDrawable: LottieDrawable,
    layerModel: Layer
) : BaseLayer(lottieDrawable, layerModel) {
    override fun drawLayer(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int) {
        // Do nothing.
    }

    override fun getBounds(outBounds: RectF, parentMatrix: Matrix, applyParents: Boolean) {
        super.getBounds(outBounds, parentMatrix, applyParents)
        outBounds[0f, 0f, 0f] = 0f
    }
}
