package com.airbnb.lottie.animation.keyframe

import com.airbnb.lottie.model.DocumentData
import com.airbnb.lottie.value.Keyframe
import com.airbnb.lottie.value.LottieFrameInfo
import com.airbnb.lottie.value.LottieValueCallback

class TextKeyframeAnimation(keyframes: List<Keyframe<DocumentData>>) : KeyframeAnimation<DocumentData>(keyframes) {
    override fun getValue(keyframe: Keyframe<DocumentData>, keyframeProgress: Float): DocumentData {
        return if (valueCallback != null) {
            valueCallback!!.getValueInternal(
                keyframe.startFrame, (if (keyframe.endFrame == null) Float.MAX_VALUE else keyframe.endFrame)!!,
                keyframe.startValue, if (keyframe.endValue == null) keyframe.startValue else keyframe.endValue, keyframeProgress,
                interpolatedCurrentKeyframeProgress, getProgress()
            )!!
        } else if (keyframeProgress != 1.0f || keyframe.endValue == null) {
            keyframe.startValue!!
        } else {
            keyframe.endValue!!
        }
    }

    fun setStringValueCallback(valueCallback: LottieValueCallback<String>) {
        val stringFrameInfo = LottieFrameInfo<String>()
        val documentData = DocumentData()
        super.setValueCallback(object : LottieValueCallback<DocumentData>() {
            override fun getValue(frameInfo: LottieFrameInfo<DocumentData>): DocumentData {
                stringFrameInfo.set(
                    frameInfo.startFrame, frameInfo.endFrame, frameInfo.startValue!!.text,
                    frameInfo.endValue!!.text, frameInfo.linearKeyframeProgress, frameInfo.interpolatedKeyframeProgress,
                    frameInfo.overallProgress
                )
                val text = valueCallback.getValue(stringFrameInfo)
                val baseDocumentData = if (frameInfo.interpolatedKeyframeProgress == 1f) frameInfo.endValue else frameInfo.startValue
                documentData.set(
                    text = text,
                    fontName = baseDocumentData!!.fontName,
                    size = baseDocumentData.size,
                    justification = baseDocumentData.justification,
                    tracking = baseDocumentData.tracking,
                    lineHeight = baseDocumentData.lineHeight,
                    baselineShift = baseDocumentData.baselineShift,
                    color = baseDocumentData.color,
                    strokeColor = baseDocumentData.strokeColor,
                    strokeWidth = baseDocumentData.strokeWidth,
                    strokeOverFill = baseDocumentData.strokeOverFill,
                    boxPosition = baseDocumentData.boxPosition,
                    boxSize = baseDocumentData.boxSize
                )
                return documentData
            }
        })
    }
}
