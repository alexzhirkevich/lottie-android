package com.airbnb.lottie.model

import android.graphics.PointF
import androidx.annotation.ColorInt
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
class DocumentData {
    enum class Justification {
        LEFT_ALIGN,
        RIGHT_ALIGN,
        CENTER
    }

    @JvmField
    var text: String? = null
    @JvmField
    var fontName: String? = null
    @JvmField
    var size: Float = 0f
    @JvmField
    var justification: Justification? = null
    @JvmField
    var tracking: Int = 0

    /** Extra space in between lines.  */
    @JvmField
    var lineHeight: Float = 0f
    @JvmField
    var baselineShift: Float = 0f

    @JvmField
    @ColorInt
    var color: Int = 0

    @JvmField
    @ColorInt
    var strokeColor: Int = 0
    @JvmField
    var strokeWidth: Float = 0f
    @JvmField
    var strokeOverFill: Boolean = false
    @JvmField
    var boxPosition: PointF? = null
    @JvmField
    var boxSize: PointF? = null


    constructor(
        text: String?, fontName: String?, size: Float, justification: Justification?, tracking: Int,
        lineHeight: Float, baselineShift: Float, @ColorInt color: Int, @ColorInt strokeColor: Int,
        strokeWidth: Float, strokeOverFill: Boolean, boxPosition: PointF?, boxSize: PointF?
    ) {
        set(
            text,
            fontName,
            size,
            justification,
            tracking,
            lineHeight,
            baselineShift,
            color,
            strokeColor,
            strokeWidth,
            strokeOverFill,
            boxPosition,
            boxSize
        )
    }

    constructor()

    fun set(
        text: String?,
        fontName: String?,
        size: Float,
        justification: Justification?,
        tracking: Int,
        lineHeight: Float,
        baselineShift: Float,
        @ColorInt color: Int,
        @ColorInt strokeColor: Int,
        strokeWidth: Float,
        strokeOverFill: Boolean,
        boxPosition: PointF?,
        boxSize: PointF?
    ) {
        this.text = text
        this.fontName = fontName
        this.size = size
        this.justification = justification
        this.tracking = tracking
        this.lineHeight = lineHeight
        this.baselineShift = baselineShift
        this.color = color
        this.strokeColor = strokeColor
        this.strokeWidth = strokeWidth
        this.strokeOverFill = strokeOverFill
        this.boxPosition = boxPosition
        this.boxSize = boxSize
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + fontName.hashCode()
        result = (31 * result + size).toInt()
        result = 31 * result + justification!!.ordinal
        result = 31 * result + tracking
        val temp = java.lang.Float.floatToRawIntBits(lineHeight).toLong()
        result = 31 * result + (temp xor (temp ushr 32)).toInt()
        result = 31 * result + color
        return result
    }
}
