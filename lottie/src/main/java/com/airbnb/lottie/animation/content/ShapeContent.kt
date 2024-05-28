package com.airbnb.lottie.animation.content

import android.graphics.Path
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.model.content.ShapePath
import com.airbnb.lottie.model.content.ShapeTrimPath
import com.airbnb.lottie.model.layer.BaseLayer
import com.airbnb.lottie.utils.MiscUtils.resolveKeyPath
import com.airbnb.lottie.value.LottieValueCallback

class ShapeContent(
    private val lottieDrawable: LottieDrawable,
    layer: BaseLayer,
    shape: ShapePath
) : PathContent,
    BaseKeyframeAnimation.AnimationListener, KeyPathElementContent {
    private val path = Path()

    override val name: String = shape.name
    private val hidden = shape.isHidden
    private val shapeAnimation = shape.shapePath.createAnimation()
    private val shapeModifierContents: List<ShapeModifierContent>? = null

    private var isPathValid = false
    private val trimPaths = CompoundTrimPathContent()

    init {
        layer.addAnimation(shapeAnimation)
        shapeAnimation.addUpdateListener(this)
    }

    override fun onValueChanged() {
        invalidate()
    }

    private fun invalidate() {
        isPathValid = false
        lottieDrawable.invalidateSelf()
    }

    override fun setContents(contentsBefore: List<Content>, contentsAfter: List<Content>) {
        var shapeModifierContents: MutableList<ShapeModifierContent>? = null
        for (i in contentsBefore.indices) {
            val content = contentsBefore[i]
            if (content is TrimPathContent &&
                content.type == ShapeTrimPath.Type.SIMULTANEOUSLY
            ) {
                // Trim path individually will be handled by the stroke where paths are combined.
                val trimPath = content
                trimPaths.addTrimPath(trimPath)
                trimPath.addListener(this)
            } else if (content is ShapeModifierContent) {
                if (shapeModifierContents == null) {
                    shapeModifierContents = ArrayList()
                }
                shapeModifierContents.add(content)
            }
        }
        shapeAnimation.setShapeModifiers(shapeModifierContents)
    }

    override fun getPath(): Path {
        if (isPathValid && !shapeAnimation.hasValueCallback()) {
            return path
        }

        path.reset()

        if (hidden) {
            isPathValid = true
            return path
        }

        val shapeAnimationPath = shapeAnimation.value
            ?: // It is unclear why this ever returns null but it seems to in rare cases.
// https://github.com/airbnb/lottie-android/issues/1632
            return path
        path.set(shapeAnimationPath)
        path.fillType = Path.FillType.EVEN_ODD

        trimPaths.apply(path)

        isPathValid = true
        return path
    }

    override fun resolveKeyPath(
        keyPath: KeyPath, depth: Int, accumulator: MutableList<KeyPath>, currentPartialKeyPath: KeyPath
    ) {
        resolveKeyPath(keyPath, depth, accumulator, currentPartialKeyPath, this)
    }

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        if (property === LottieProperty.PATH) {
            shapeAnimation.setValueCallback(callback as LottieValueCallback<Path>?)
        }
    }
}
