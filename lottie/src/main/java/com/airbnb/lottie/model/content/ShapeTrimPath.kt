package com.airbnb.lottie.model.content

import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.TrimPathContent
import com.airbnb.lottie.model.animatable.AnimatableFloatValue
import com.airbnb.lottie.model.layer.BaseLayer

class ShapeTrimPath(
    @JvmField val name: String,
    @JvmField val type: Type,
    @JvmField val start: AnimatableFloatValue,
    @JvmField val end: AnimatableFloatValue,
    @JvmField val offset: AnimatableFloatValue,
    val isHidden: Boolean
) : ContentModel {
    enum class Type {
        SIMULTANEOUSLY,
        INDIVIDUALLY;

        companion object {
            @JvmStatic
            fun forId(id: Int): Type {
                return when (id) {
                    1 -> SIMULTANEOUSLY
                    2 -> INDIVIDUALLY
                    else -> throw IllegalArgumentException("Unknown trim path type $id")
                }
            }
        }
    }

    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return TrimPathContent(layer, this)
    }

    override fun toString(): String {
        return "Trim Path: {start: $start, end: $end, offset: $offset}"
    }
}
