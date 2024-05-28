package com.airbnb.lottie.model.content

import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.ContentGroup
import com.airbnb.lottie.model.layer.BaseLayer

class ShapeGroup(
    val name: String,
    val items: List<ContentModel>,
    val isHidden: Boolean
) : ContentModel {
    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return ContentGroup(drawable, layer, this, composition)
    }

    override fun toString(): String {
        return "ShapeGroup{" + "name='" + name + "\' Shapes: " + items.toTypedArray().contentToString() + '}'
    }
}
