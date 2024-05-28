package com.airbnb.lottie.model.layer

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.ContentGroup
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.model.content.BlurEffect
import com.airbnb.lottie.model.content.ShapeGroup
import com.airbnb.lottie.parser.DropShadowEffect

class ShapeLayer internal constructor(
    lottieDrawable: LottieDrawable,
    layerModel: Layer,
    private val compositionLayer: CompositionLayer,
    composition: LottieComposition?
) : BaseLayer(lottieDrawable, layerModel) {
    private val contentGroup: ContentGroup

    init {
        // Naming this __container allows it to be ignored in KeyPath matching.
        val shapeGroup = ShapeGroup("__container", layerModel.shapes, false)
        contentGroup = ContentGroup(lottieDrawable!!, this, shapeGroup, composition!!)
        contentGroup.setContents(emptyList(), emptyList())
    }

    override fun drawLayer(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int) {
        contentGroup.draw(canvas, parentMatrix, parentAlpha)
    }

    override fun getBounds(outBounds: RectF, parentMatrix: Matrix, applyParents: Boolean) {
        super.getBounds(outBounds, parentMatrix, applyParents)
        contentGroup.getBounds(outBounds, boundsMatrix, applyParents)
    }

    override val blurEffect: BlurEffect?
        get() {
            val layerBlur = super.blurEffect
            if (layerBlur != null) {
                return layerBlur
            }
            return compositionLayer.blurEffect
        }

    override val dropShadowEffect: DropShadowEffect?
        get() {
            val layerDropShadow = super.dropShadowEffect
            if (layerDropShadow != null) {
                return layerDropShadow
            }
            return compositionLayer.dropShadowEffect
        }

    override fun resolveChildKeyPath(
        keyPath: KeyPath, depth: Int, accumulator: MutableList<KeyPath>,
        currentPartialKeyPath: KeyPath
    ) {
        contentGroup.resolveKeyPath(keyPath, depth, accumulator, currentPartialKeyPath)
    }
}
