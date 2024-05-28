package com.airbnb.lottie.model.content

import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.ShapeContent
import com.airbnb.lottie.model.animatable.AnimatableShapeValue
import com.airbnb.lottie.model.layer.BaseLayer

class ShapePath(
    @JvmField val name: String,
    private val index: Int,
    @JvmField val shapePath: AnimatableShapeValue,
    val isHidden: Boolean
) : ContentModel {
    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return ShapeContent(drawable, layer, this)
    }

    override fun toString(): String {
        return "ShapePath{" + "name=" + name +
                ", index=" + index +
                '}'
    }
}
