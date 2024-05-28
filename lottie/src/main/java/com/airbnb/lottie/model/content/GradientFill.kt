package com.airbnb.lottie.model.content

import android.graphics.Path.FillType
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.GradientFillContent
import com.airbnb.lottie.model.animatable.AnimatableFloatValue
import com.airbnb.lottie.model.animatable.AnimatableGradientColorValue
import com.airbnb.lottie.model.animatable.AnimatableIntegerValue
import com.airbnb.lottie.model.animatable.AnimatablePointValue
import com.airbnb.lottie.model.layer.BaseLayer

class GradientFill(
  @JvmField val name: String,
  @JvmField val gradientType: GradientType,
  @JvmField val fillType: FillType,
  @JvmField val gradientColor: AnimatableGradientColorValue,
  @JvmField val opacity: AnimatableIntegerValue,
  @JvmField val startPoint: AnimatablePointValue,
  @JvmField val endPoint: AnimatablePointValue,
  private val highlightLength: AnimatableFloatValue?,
  private val highlightAngle: AnimatableFloatValue?,
  val isHidden: Boolean
) : ContentModel {
    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return GradientFillContent(drawable, composition, layer, this)
    }
}
