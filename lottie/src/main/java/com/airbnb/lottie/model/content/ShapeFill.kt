package com.airbnb.lottie.model.content

import android.graphics.Path.FillType
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.FillContent
import com.airbnb.lottie.model.animatable.AnimatableColorValue
import com.airbnb.lottie.model.animatable.AnimatableIntegerValue
import com.airbnb.lottie.model.layer.BaseLayer

class ShapeFill(
  @JvmField val name: String,
  private val fillEnabled: Boolean,
  @JvmField val fillType: FillType,
  @JvmField val color: AnimatableColorValue?,
  @JvmField val opacity: AnimatableIntegerValue?,
  val isHidden: Boolean
) : ContentModel {
    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return FillContent(drawable, layer, this)
    }

    override fun toString(): String {
        return "ShapeFill{" + "color=" +
                ", fillEnabled=" + fillEnabled +
                '}'
    }
}
