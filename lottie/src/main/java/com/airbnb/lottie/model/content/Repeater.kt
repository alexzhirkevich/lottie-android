package com.airbnb.lottie.model.content

import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.RepeaterContent
import com.airbnb.lottie.model.animatable.AnimatableFloatValue
import com.airbnb.lottie.model.animatable.AnimatableTransform
import com.airbnb.lottie.model.layer.BaseLayer

class Repeater(
  @JvmField val name: String,
  @JvmField val copies: AnimatableFloatValue,
  @JvmField val offset: AnimatableFloatValue,
  @JvmField val transform: AnimatableTransform,
  val isHidden: Boolean
) : ContentModel {
    override fun toContent(
        drawable: LottieDrawable,
        composition: LottieComposition,
        layer: BaseLayer
    ): Content {
        return RepeaterContent(drawable, layer, this)
    }
}
