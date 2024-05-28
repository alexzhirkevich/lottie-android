package com.airbnb.lottie.model.content

import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.GradientStrokeContent
import com.airbnb.lottie.model.animatable.AnimatableFloatValue
import com.airbnb.lottie.model.animatable.AnimatableGradientColorValue
import com.airbnb.lottie.model.animatable.AnimatableIntegerValue
import com.airbnb.lottie.model.animatable.AnimatablePointValue
import com.airbnb.lottie.model.content.ShapeStroke.LineCapType
import com.airbnb.lottie.model.content.ShapeStroke.LineJoinType
import com.airbnb.lottie.model.layer.BaseLayer

class GradientStroke(
    @JvmField val name: String,
    @JvmField val gradientType: GradientType,
    @JvmField val gradientColor: AnimatableGradientColorValue,
    @JvmField val opacity: AnimatableIntegerValue,
    @JvmField val startPoint: AnimatablePointValue,
    @JvmField val endPoint: AnimatablePointValue,
    @JvmField val width: AnimatableFloatValue,
    @JvmField val capType: LineCapType,
    @JvmField val joinType: LineJoinType,
    @JvmField val miterLimit: Float,
    @JvmField val lineDashPattern: List<AnimatableFloatValue>,
    @JvmField val dashOffset: AnimatableFloatValue?,
    val isHidden: Boolean
) : ContentModel {
    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return GradientStrokeContent(drawable, layer, this)
    }
}
