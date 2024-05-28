package com.airbnb.lottie.animation.keyframe

import android.graphics.Path
import com.airbnb.lottie.model.content.Mask
import com.airbnb.lottie.model.content.ShapeData

class MaskKeyframeAnimation(@JvmField val masks: List<Mask>) {
    private val maskAnimations: MutableList<BaseKeyframeAnimation<ShapeData, Path>> = ArrayList(masks.size)
    private val opacityAnimations: MutableList<BaseKeyframeAnimation<Int, Int>> = ArrayList(masks.size)

    init {
        for (i in masks.indices) {
            maskAnimations.add(masks[i].maskPath.createAnimation())
            val opacity = masks[i].opacity
            opacityAnimations.add(opacity.createAnimation())
        }
    }

    fun getMaskAnimations(): List<BaseKeyframeAnimation<ShapeData, Path>> {
        return maskAnimations
    }

    fun getOpacityAnimations(): List<BaseKeyframeAnimation<Int, Int>> {
        return opacityAnimations
    }
}
