package com.airbnb.lottie.model.animatable

import android.graphics.PointF
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.ModifierContent
import com.airbnb.lottie.animation.keyframe.TransformKeyframeAnimation
import com.airbnb.lottie.model.content.ContentModel
import com.airbnb.lottie.model.layer.BaseLayer

class AnimatableTransform @JvmOverloads constructor(
    val anchorPoint: AnimatablePathValue? = null,
    val position: AnimatableValue<PointF, PointF>? = null,
    val scale: AnimatableScaleValue? = null,
    val rotation: AnimatableFloatValue? = null,
    val opacity: AnimatableIntegerValue? = null,
    // Used for repeaters
    val startOpacity: AnimatableFloatValue? = null,
    val endOpacity: AnimatableFloatValue? = null,
    val skew: AnimatableFloatValue? = null,
    val skewAngle: AnimatableFloatValue? = null
) : ModifierContent, ContentModel {
    /**
     * This is set as a property of the layer so it is parsed and set separately.
     */
    var isAutoOrient: Boolean = false

    fun createAnimation(): TransformKeyframeAnimation {
        return TransformKeyframeAnimation(this)
    }

    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content? {
        return null
    }
}
