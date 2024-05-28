package com.airbnb.lottie.model.content

import android.graphics.PointF
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.RectangleContent
import com.airbnb.lottie.model.animatable.AnimatableFloatValue
import com.airbnb.lottie.model.animatable.AnimatableValue
import com.airbnb.lottie.model.layer.BaseLayer

class RectangleShape(
  @JvmField val name: String,
  @JvmField val position: AnimatableValue<PointF, PointF>,
  @JvmField val size: AnimatableValue<PointF, PointF>,
  @JvmField val cornerRadius: AnimatableFloatValue,
  val isHidden: Boolean
) : ContentModel {
    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return RectangleContent(drawable, layer, this)
    }

    override fun toString(): String {
        return "RectangleShape{position=" + position +
                ", size=" + size +
                '}'
    }
}
