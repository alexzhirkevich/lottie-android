package com.airbnb.lottie.animation.content

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF

interface DrawingContent : Content {
    fun draw(canvas: Canvas, parentMatrix: Matrix, alpha: Int)

    fun getBounds(outBounds: RectF, parentMatrix: Matrix, applyParents: Boolean)
}
