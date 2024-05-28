package com.airbnb.lottie.animation.keyframe

import android.graphics.Path
import com.airbnb.lottie.animation.content.ShapeModifierContent
import com.airbnb.lottie.model.content.ShapeData
import com.airbnb.lottie.utils.MiscUtils.getPathFromData
import com.airbnb.lottie.value.Keyframe

class ShapeKeyframeAnimation(keyframes: List<Keyframe<ShapeData>>) : BaseKeyframeAnimation<ShapeData, Path>(keyframes) {
    private val tempShapeData = ShapeData()
    private val tempPath = Path()
    private var valueCallbackStartPath: Path? = null
    private var valueCallbackEndPath: Path? = null

    private var shapeModifiers: List<ShapeModifierContent>? = null

    override fun getValue(keyframe: Keyframe<ShapeData>, keyframeProgress: Float): Path {
        val startShapeData = keyframe.startValue!!
        val endShapeData = keyframe.endValue

        tempShapeData.interpolateBetween(startShapeData, endShapeData ?: startShapeData, keyframeProgress)
        var modifiedShapeData: ShapeData? = tempShapeData
        if (shapeModifiers != null) {
            for (i in shapeModifiers!!.indices.reversed()) {
                modifiedShapeData = shapeModifiers!![i].modifyShape(modifiedShapeData)
            }
        }
        getPathFromData(modifiedShapeData!!, tempPath)
        if (valueCallback != null) {
            if (valueCallbackStartPath == null) {
                valueCallbackStartPath = Path()
                valueCallbackEndPath = Path()
            }
            getPathFromData(startShapeData!!, valueCallbackStartPath!!)
            if (endShapeData != null) {
                getPathFromData(endShapeData, valueCallbackEndPath!!)
            }

            return valueCallback!!.getValueInternal(
                keyframe.startFrame, keyframe.endFrame!!,
                valueCallbackStartPath, if (endShapeData == null) valueCallbackStartPath else valueCallbackEndPath,
                keyframeProgress, linearCurrentKeyframeProgress, getProgress()
            )!!
        }
        return tempPath
    }

    fun setShapeModifiers(shapeModifiers: List<ShapeModifierContent>?) {
        this.shapeModifiers = shapeModifiers
    }
}
