package com.airbnb.lottie.model.layer

import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.model.animatable.AnimatableFloatValue
import com.airbnb.lottie.model.animatable.AnimatableTextFrame
import com.airbnb.lottie.model.animatable.AnimatableTextProperties
import com.airbnb.lottie.model.animatable.AnimatableTransform
import com.airbnb.lottie.model.content.BlurEffect
import com.airbnb.lottie.model.content.ContentModel
import com.airbnb.lottie.model.content.LBlendMode
import com.airbnb.lottie.model.content.Mask
import com.airbnb.lottie.parser.DropShadowEffect
import com.airbnb.lottie.value.Keyframe
import java.util.Locale

class Layer(
  @JvmField val shapes: List<ContentModel>,
  @JvmField val composition: LottieComposition,
  val name: String, val id: Long,
  @JvmField val layerType: LayerType,
  val parentId: Long,
  @JvmField val refId: String?,
  val masks: List<Mask>,
  val transform: AnimatableTransform,
  @JvmField val solidWidth: Int,
  @JvmField val solidHeight: Int,
  @JvmField val solidColor: Int,
  val timeStretch: Float,
  private val startFrame: Float,
  val preCompWidth: Float,
  val preCompHeight: Float,
  @JvmField val text: AnimatableTextFrame?,
  @JvmField val textProperties: AnimatableTextProperties?,
  val inOutKeyframes: List<Keyframe<Float>>,
  val matteType: MatteType,
  val timeRemapping: AnimatableFloatValue?,
  val isHidden: Boolean, val blurEffect: BlurEffect?,
  val dropShadowEffect: DropShadowEffect?,
  val blendMode: LBlendMode?
) {
    enum class LayerType {
        PRE_COMP,
        SOLID,
        IMAGE,
        NULL,
        SHAPE,
        TEXT,
        UNKNOWN
    }

    enum class MatteType {
        NONE,
        ADD,
        INVERT,
        LUMA,
        LUMA_INVERTED,
        UNKNOWN
    }


    val startProgress: Float
        get() = startFrame / composition.durationFrames

    override fun toString(): String {
        return toString("")
    }

    fun toString(prefix: String?): String {
        val sb = StringBuilder()
        sb.append(prefix).append(name).append("\n")
        var parent = composition.layerModelForId(parentId)
        if (parent != null) {
            sb.append("\t\tParents: ").append(parent.name)
            parent = composition.layerModelForId(parent.parentId)
            while (parent != null) {
                sb.append("->").append(parent.name)
                parent = composition.layerModelForId(parent.parentId)
            }
            sb.append(prefix).append("\n")
        }
        if (!masks.isEmpty()) {
            sb.append(prefix).append("\tMasks: ").append(masks.size).append("\n")
        }
        if (solidWidth != 0 && solidHeight != 0) {
            sb.append(prefix).append("\tBackground: ").append(String.format(Locale.US, "%dx%d %X\n", solidWidth, solidHeight, solidColor))
        }
        if (!shapes.isEmpty()) {
            sb.append(prefix).append("\tShapes:\n")
            for (shape in shapes) {
                sb.append(prefix).append("\t\t").append(shape).append("\n")
            }
        }
        return sb.toString()
    }
}
