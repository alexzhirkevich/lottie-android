package com.airbnb.lottie.animation.content

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.TransformKeyframeAnimation
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.model.content.Repeater
import com.airbnb.lottie.model.layer.BaseLayer
import com.airbnb.lottie.utils.MiscUtils.lerp
import com.airbnb.lottie.utils.MiscUtils.resolveKeyPath
import com.airbnb.lottie.value.LottieValueCallback
import java.util.Collections

class RepeaterContent(
    private val lottieDrawable: LottieDrawable,
    private val layer: BaseLayer,
    repeater: Repeater
) : DrawingContent, PathContent,
    GreedyContent, BaseKeyframeAnimation.AnimationListener, KeyPathElementContent {
    private val matrix = Matrix()
    private val path = Path()

    override val name: String = repeater.name
    private val hidden = repeater.isHidden
    private val copies = repeater.copies.createAnimation()
    private val offset: BaseKeyframeAnimation<Float, Float>
    private val transform: TransformKeyframeAnimation
    private var contentGroup: ContentGroup? = null


    init {
        layer.addAnimation(copies)
        copies.addUpdateListener(this)

        offset = repeater.offset.createAnimation()
        layer.addAnimation(offset)
        offset.addUpdateListener(this)

        transform = repeater.transform.createAnimation()
        transform.addAnimationsToLayer(layer)
        transform.addListener(this)
    }

    override fun absorbContent(contentsIter: MutableListIterator<Content>) {
        // This check prevents a repeater from getting added twice.
        // This can happen in the following situation:
        //    RECTANGLE
        //    REPEATER 1
        //    FILL
        //    REPEATER 2
        // In this case, the expected structure would be:
        //     REPEATER 2
        //        REPEATER 1
        //            RECTANGLE
        //        FILL
        // Without this check, REPEATER 1 will try and absorb contents once it is already inside of
        // REPEATER 2.
        if (contentGroup != null) {
            return
        }
        // Fast forward the iterator until after this content.
        while (contentsIter.hasPrevious() && contentsIter.previous() !== this) {
        }
        val contents: MutableList<Content> = ArrayList()
        while (contentsIter.hasPrevious()) {
            contents.add(contentsIter.previous())
            contentsIter.remove()
        }
        contents.reverse()
        contentGroup = ContentGroup(lottieDrawable, layer, "Repeater", hidden, contents, null)
    }

    override fun setContents(contentsBefore: List<Content>, contentsAfter: List<Content>) {
        contentGroup!!.setContents(contentsBefore, contentsAfter)
    }

    override fun getPath(): Path {
        val contentPath = contentGroup!!.getPath()
        path.reset()
        val copies = copies.value
        val offset = offset.value
        for (i in copies.toInt() - 1 downTo 0) {
            matrix.set(transform.getMatrixForRepeater(i + offset))
            path.addPath(contentPath, matrix)
        }
        return path
    }

    override fun draw(canvas: Canvas, parentMatrix: Matrix, alpha: Int) {
        val copies = copies.value
        val offset = offset.value
        val startOpacity = transform.startOpacity!!.value / 100f
        val endOpacity = transform.endOpacity!!.value / 100f
        for (i in copies.toInt() - 1 downTo 0) {
            matrix.set(parentMatrix)
            matrix.preConcat(transform.getMatrixForRepeater(i + offset))
            val newAlpha = alpha * lerp(startOpacity, endOpacity, i / copies)
            contentGroup!!.draw(canvas, matrix, newAlpha.toInt())
        }
    }

    override fun getBounds(outBounds: RectF, parentMatrix: Matrix, applyParents: Boolean) {
        contentGroup!!.getBounds(outBounds, parentMatrix, applyParents)
    }

    override fun onValueChanged() {
        lottieDrawable.invalidateSelf()
    }

    override fun resolveKeyPath(
        keyPath: KeyPath, depth: Int, accumulator: MutableList<KeyPath>, currentPartialKeyPath: KeyPath
    ) {
        resolveKeyPath(keyPath, depth, accumulator, currentPartialKeyPath, this)
        for (i in contentGroup!!.contents.indices) {
            val content = contentGroup!!.contents[i]
            if (content is KeyPathElementContent) {
                resolveKeyPath(keyPath, depth, accumulator, currentPartialKeyPath, content)
            }
        }
    }

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        if (transform.applyValueCallback(property, callback)) {
            return
        }

        if (property == LottieProperty.REPEATER_COPIES) {
            copies.setValueCallback(callback as LottieValueCallback<Float>?)
        } else if (property == LottieProperty.REPEATER_OFFSET) {
            offset.setValueCallback(callback as LottieValueCallback<Float>?)
        }
    }
}
