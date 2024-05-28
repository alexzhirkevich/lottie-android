package com.airbnb.lottie.model.content

import android.graphics.PointF
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.EllipseContent
import com.airbnb.lottie.model.animatable.AnimatablePointValue
import com.airbnb.lottie.model.animatable.AnimatableValue
import com.airbnb.lottie.model.layer.BaseLayer

class CircleShape(
    @JvmField val name: String,
    @JvmField val position: AnimatableValue<PointF, PointF>,
    @JvmField val size: AnimatablePointValue,
    @JvmField val isReversed: Boolean,
    val isHidden: Boolean
) : ContentModel {
    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return EllipseContent(drawable, layer, this)
    }
}
