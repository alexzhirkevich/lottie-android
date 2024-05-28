package com.airbnb.lottie.model.content

import com.airbnb.lottie.model.animatable.AnimatableIntegerValue
import com.airbnb.lottie.model.animatable.AnimatableShapeValue

class Mask(
    val maskMode: MaskMode,
    val maskPath: AnimatableShapeValue,
    val opacity: AnimatableIntegerValue,
    val isInverted: Boolean
) {
    enum class MaskMode {
        MASK_MODE_ADD,
        MASK_MODE_SUBTRACT,
        MASK_MODE_INTERSECT,
        MASK_MODE_NONE
    }
}
