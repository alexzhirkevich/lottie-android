package com.airbnb.lottie.animation.content

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.FloatKeyframeAnimation
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.model.content.RectangleShape
import com.airbnb.lottie.model.content.ShapeTrimPath
import com.airbnb.lottie.model.layer.BaseLayer
import com.airbnb.lottie.utils.MiscUtils.resolveKeyPath
import com.airbnb.lottie.value.LottieValueCallback
import kotlin.math.min

class RectangleContent(
    private val lottieDrawable: LottieDrawable,
    layer: BaseLayer,
    rectShape: RectangleShape
) : BaseKeyframeAnimation.AnimationListener,
    KeyPathElementContent, PathContent {
    private val path = Path()
    private val rect = RectF()

    override val name: String = rectShape.name
    private val hidden = rectShape.isHidden
    private val positionAnimation: BaseKeyframeAnimation<PointF, PointF>?
    private val sizeAnimation: BaseKeyframeAnimation<PointF, PointF>?
    private val cornerRadiusAnimation: BaseKeyframeAnimation<Float, Float>?

    private val trimPaths = CompoundTrimPathContent()

    /** This corner radius is from a layer item. The first one is from the roundedness on this specific rect.  */
    private var roundedCornersAnimation: BaseKeyframeAnimation<Float, Float>? = null
    private var isPathValid = false

    init {
        positionAnimation = rectShape.position.createAnimation()
        sizeAnimation = rectShape.size.createAnimation()
        cornerRadiusAnimation = rectShape.cornerRadius.createAnimation()

        layer.addAnimation(positionAnimation)
        layer.addAnimation(sizeAnimation)
        layer.addAnimation(cornerRadiusAnimation)

        positionAnimation!!.addUpdateListener(this)
        sizeAnimation!!.addUpdateListener(this)
        cornerRadiusAnimation.addUpdateListener(this)
    }

    override fun onValueChanged() {
        invalidate()
    }

    private fun invalidate() {
        isPathValid = false
        lottieDrawable.invalidateSelf()
    }

    override fun setContents(contentsBefore: List<Content>, contentsAfter: List<Content>) {
        for (i in contentsBefore.indices) {
            val content = contentsBefore[i]
            if (content is TrimPathContent &&
                content.type == ShapeTrimPath.Type.SIMULTANEOUSLY
            ) {
                val trimPath = content
                trimPaths.addTrimPath(trimPath)
                trimPath.addListener(this)
            } else if (content is RoundedCornersContent) {
                roundedCornersAnimation = content.roundedCorners
            }
        }
    }

    override fun getPath(): Path {
        if (isPathValid) {
            return path
        }

        path.reset()

        if (hidden) {
            isPathValid = true
            return path
        }

        val size = sizeAnimation!!.value
        val halfWidth = size!!.x / 2f
        val halfHeight = size.y / 2f
        var radius = if (cornerRadiusAnimation == null) 0f else (cornerRadiusAnimation as FloatKeyframeAnimation).floatValue
        if (radius == 0f && this.roundedCornersAnimation != null) {
            radius = min(roundedCornersAnimation!!.value.toDouble(), min(halfWidth.toDouble(), halfHeight.toDouble())).toFloat()
        }
        val maxRadius = min(halfWidth.toDouble(), halfHeight.toDouble()).toFloat()
        if (radius > maxRadius) {
            radius = maxRadius
        }

        // Draw the rectangle top right to bottom left.
        val position = positionAnimation!!.value

        path.moveTo(position!!.x + halfWidth, position.y - halfHeight + radius)

        path.lineTo(position.x + halfWidth, position.y + halfHeight - radius)

        if (radius > 0) {
            rect[position.x + halfWidth - 2 * radius, position.y + halfHeight - 2 * radius, position.x + halfWidth] = position.y + halfHeight
            path.arcTo(rect, 0f, 90f, false)
        }

        path.lineTo(position.x - halfWidth + radius, position.y + halfHeight)

        if (radius > 0) {
            rect[position.x - halfWidth, position.y + halfHeight - 2 * radius, position.x - halfWidth + 2 * radius] = position.y + halfHeight
            path.arcTo(rect, 90f, 90f, false)
        }

        path.lineTo(position.x - halfWidth, position.y - halfHeight + radius)

        if (radius > 0) {
            rect[position.x - halfWidth, position.y - halfHeight, position.x - halfWidth + 2 * radius] = position.y - halfHeight + 2 * radius
            path.arcTo(rect, 180f, 90f, false)
        }

        path.lineTo(position.x + halfWidth - radius, position.y - halfHeight)

        if (radius > 0) {
            rect[position.x + halfWidth - 2 * radius, position.y - halfHeight, position.x + halfWidth] = position.y - halfHeight + 2 * radius
            path.arcTo(rect, 270f, 90f, false)
        }
        path.close()

        trimPaths.apply(path)

        isPathValid = true
        return path
    }

    override fun resolveKeyPath(
        keyPath: KeyPath, depth: Int, accumulator: MutableList<KeyPath>,
        currentPartialKeyPath: KeyPath
    ) {
        resolveKeyPath(keyPath, depth, accumulator, currentPartialKeyPath, this)
    }

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        if (property === LottieProperty.RECTANGLE_SIZE) {
            sizeAnimation!!.setValueCallback(callback as LottieValueCallback<PointF>?)
        } else if (property === LottieProperty.POSITION) {
            positionAnimation!!.setValueCallback(callback as LottieValueCallback<PointF>?)
        } else if (property == LottieProperty.CORNER_RADIUS) {
            cornerRadiusAnimation!!.setValueCallback(callback as LottieValueCallback<Float>?)
        }
    }
}
