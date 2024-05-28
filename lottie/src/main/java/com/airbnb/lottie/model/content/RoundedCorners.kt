package com.airbnb.lottie.model.content

import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.RoundedCornersContent
import com.airbnb.lottie.model.animatable.AnimatableValue
import com.airbnb.lottie.model.layer.BaseLayer

class RoundedCorners(
    @JvmField val name: String,
    @JvmField val cornerRadius: AnimatableValue<Float, Float>
) : ContentModel {
    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return RoundedCornersContent(drawable, layer, this)
    }
}
