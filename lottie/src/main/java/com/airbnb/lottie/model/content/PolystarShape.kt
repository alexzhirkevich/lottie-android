package com.airbnb.lottie.model.content

import android.graphics.PointF
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.PolystarContent
import com.airbnb.lottie.model.animatable.AnimatableFloatValue
import com.airbnb.lottie.model.animatable.AnimatableValue
import com.airbnb.lottie.model.layer.BaseLayer

class PolystarShape(
    @JvmField val name: String,
    @JvmField val type: Type,
    @JvmField val points: AnimatableFloatValue,
    @JvmField val position: AnimatableValue<PointF, PointF>,
    @JvmField val rotation: AnimatableFloatValue,
    @JvmField val innerRadius: AnimatableFloatValue,
    @JvmField val outerRadius: AnimatableFloatValue,
    @JvmField val innerRoundedness: AnimatableFloatValue,
    @JvmField val outerRoundedness: AnimatableFloatValue,
    val isHidden: Boolean,
    @JvmField val isReversed: Boolean
) : ContentModel {
    enum class Type(private val value: Int) {
        STAR(1),
        POLYGON(2);

        companion object {
            @JvmStatic
            fun forValue(value: Int): Type? {
                for (type in entries) {
                    if (type.value == value) {
                        return type
                    }
                }
                return null
            }
        }
    }

    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return PolystarContent(drawable, layer, this)
    }
}
