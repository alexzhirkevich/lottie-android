package com.airbnb.lottie.model.content

import android.graphics.Paint
import android.graphics.Paint.Cap
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.StrokeContent
import com.airbnb.lottie.model.animatable.AnimatableColorValue
import com.airbnb.lottie.model.animatable.AnimatableFloatValue
import com.airbnb.lottie.model.animatable.AnimatableIntegerValue
import com.airbnb.lottie.model.layer.BaseLayer

class ShapeStroke(
  @JvmField val name: String,
  val dashOffset: AnimatableFloatValue?,
  @JvmField val lineDashPattern: List<AnimatableFloatValue>,
  @JvmField val color: AnimatableColorValue,
  @JvmField val opacity: AnimatableIntegerValue,
  @JvmField val width: AnimatableFloatValue,
  @JvmField val capType: LineCapType,
  @JvmField val joinType: LineJoinType,
  @JvmField val miterLimit: Float,
  val isHidden: Boolean
) : ContentModel {
    enum class LineCapType {
        BUTT,
        ROUND,
        UNKNOWN;

        fun toPaintCap(): Cap {
            return when (this) {
                BUTT -> Cap.BUTT
                ROUND -> Cap.ROUND
                UNKNOWN -> Cap.SQUARE
                else -> Cap.SQUARE
            }
        }
    }

    enum class LineJoinType {
        MITER,
        ROUND,
        BEVEL;

        fun toPaintJoin(): Paint.Join? {
            return when (this) {
                BEVEL -> Paint.Join.BEVEL
                MITER -> Paint.Join.MITER
                ROUND -> Paint.Join.ROUND
            }
            return null
        }
    }

    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content {
        return StrokeContent(drawable, layer, this)
    }
}
