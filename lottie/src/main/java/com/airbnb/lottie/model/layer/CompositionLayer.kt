package com.airbnb.lottie.model.layer

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.FloatRange
import androidx.collection.LongSparseArray
import com.airbnb.lottie.L.beginSection
import com.airbnb.lottie.L.endSection
import com.airbnb.lottie.L.isTraceEnabled
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ValueCallbackKeyframeAnimation
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.model.layer.Layer.MatteType
import com.airbnb.lottie.utils.Utils.saveLayerCompat
import com.airbnb.lottie.value.LottieValueCallback

class CompositionLayer(
    lottieDrawable: LottieDrawable,
    layerModel: Layer,
    layerModels: List<Layer>,
    composition: LottieComposition
) : BaseLayer(lottieDrawable, layerModel) {
    private var timeRemapping: BaseKeyframeAnimation<Float, Float>? = null
    private val layers: MutableList<BaseLayer> = ArrayList()
    private val rect = RectF()
    private val newClipRect = RectF()
    private val layerPaint = Paint()

    private var hasMatte: Boolean? = null
    private var hasMasks: Boolean? = null
    private var progress = 0f

    private var clipToCompositionBounds = true

    init {
        val timeRemapping = layerModel.timeRemapping
        if (timeRemapping != null) {
            this.timeRemapping = timeRemapping.createAnimation()
            addAnimation(this.timeRemapping)
            this.timeRemapping?.addUpdateListener(this)
        } else {
            this.timeRemapping = null
        }

        val layerMap =
            LongSparseArray<BaseLayer>(composition.layers!!.size)

        var mattedLayer: BaseLayer? = null
        for (i in layerModels.indices.reversed()) {
            val lm = layerModels[i]
            val layer = forModel(this, lm, lottieDrawable, composition) ?: continue
            layerMap.put(layer.layerModel.id, layer)
            if (mattedLayer != null) {
                mattedLayer.setMatteLayer(layer)
                mattedLayer = null
            } else {
                layers.add(0, layer)
                when (lm.matteType) {
                    MatteType.ADD,
                    MatteType.INVERT -> mattedLayer = layer
                    else -> {}
                }
            }
        }

        for (i in 0 until layerMap.size()) {
            val key = layerMap.keyAt(i)
            val layerView = layerMap[key] ?: continue
            // This shouldn't happen but it appears as if sometimes on pre-lollipop devices when
            // compiled with d8, layerView is null sometimes.
            // https://github.com/airbnb/lottie-android/issues/524
            val parentLayer = layerMap[layerView.layerModel.parentId]
            if (parentLayer != null) {
                layerView.setParentLayer(parentLayer)
            }
        }
    }

    fun setClipToCompositionBounds(clipToCompositionBounds: Boolean) {
        this.clipToCompositionBounds = clipToCompositionBounds
    }

    override fun setOutlineMasksAndMattes(outline: Boolean) {
        super.setOutlineMasksAndMattes(outline)
        for (layer in layers) {
            layer.setOutlineMasksAndMattes(outline)
        }
    }

    override fun drawLayer(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int) {
        if (isTraceEnabled()) {
            beginSection("CompositionLayer#draw")
        }
        newClipRect[0f, 0f, layerModel.preCompWidth] = layerModel.preCompHeight
        parentMatrix.mapRect(newClipRect)

        // Apply off-screen rendering only when needed in order to improve rendering performance.
        val isDrawingWithOffScreen = lottieDrawable.isApplyingOpacityToLayersEnabled && layers.size > 1 && parentAlpha != 255
        if (isDrawingWithOffScreen) {
            layerPaint.alpha = parentAlpha
            saveLayerCompat(canvas, newClipRect, layerPaint)
        } else {
            canvas.save()
        }

        val childAlpha = if (isDrawingWithOffScreen) 255 else parentAlpha
        for (i in layers.indices.reversed()) {
            var nonEmptyClip = true
            // Only clip precomps. This mimics the way After Effects renders animations.
            val ignoreClipOnThisLayer = !clipToCompositionBounds && "__container" == layerModel.name
            if (!ignoreClipOnThisLayer && !newClipRect.isEmpty) {
                nonEmptyClip = canvas.clipRect(newClipRect)
            }
            if (nonEmptyClip) {
                val layer = layers[i]
                layer.draw(canvas, parentMatrix, childAlpha)
            }
        }
        canvas.restore()
        if (isTraceEnabled()) {
            endSection("CompositionLayer#draw")
        }
    }

    override fun getBounds(outBounds: RectF, parentMatrix: Matrix, applyParents: Boolean) {
        super.getBounds(outBounds, parentMatrix, applyParents)
        for (i in layers.indices.reversed()) {
            rect[0f, 0f, 0f] = 0f
            layers[i].getBounds(rect, boundsMatrix, true)
            outBounds.union(rect)
        }
    }

    override fun setProgress(@FloatRange(from = 0.0, to = 1.0) progress: Float) {
        var progress = progress
        if (isTraceEnabled()) {
            beginSection("CompositionLayer#setProgress")
        }
        this.progress = progress
        super.setProgress(progress)
        if (timeRemapping != null) {
            // The duration has 0.01 frame offset to show end of animation properly.
            // https://github.com/airbnb/lottie-android/pull/766
            // Ignore this offset for calculating time-remapping because time-remapping value is based on original duration.
            val durationFrames = lottieDrawable.composition!!.durationFrames + 0.01f
            val compositionDelayFrames = layerModel.composition.startFrame
            val remappedFrames = timeRemapping!!.value!! * layerModel.composition.frameRate - compositionDelayFrames
            progress = remappedFrames / durationFrames
        }
        if (timeRemapping == null) {
            progress -= layerModel.startProgress
        }
        //Time stretch needs to be divided if is not "__container"
        if (layerModel.timeStretch != 0f && "__container" != layerModel.name) {
            progress /= layerModel.timeStretch
        }
        for (i in layers.indices.reversed()) {
            layers[i].setProgress(progress)
        }
        if (isTraceEnabled()) {
            endSection("CompositionLayer#setProgress")
        }
    }

    fun getProgress(): Float {
        return progress
    }

    fun hasMasks(): Boolean {
        if (hasMasks == null) {
            for (i in layers.indices.reversed()) {
                val layer = layers[i]
                if (layer is ShapeLayer) {
                    if (layer.hasMasksOnThisLayer()) {
                        hasMasks = true
                        return true
                    }
                } else if (layer is CompositionLayer && layer.hasMasks()) {
                    hasMasks = true
                    return true
                }
            }
            hasMasks = false
        }
        return hasMasks!!
    }

    fun hasMatte(): Boolean {
        if (hasMatte == null) {
            if (hasMatteOnThisLayer()) {
                hasMatte = true
                return true
            }

            for (i in layers.indices.reversed()) {
                if (layers[i].hasMatteOnThisLayer()) {
                    hasMatte = true
                    return true
                }
            }
            hasMatte = false
        }
        return hasMatte!!
    }


    override fun resolveChildKeyPath(
        keyPath: KeyPath,
        depth: Int,
        accumulator: MutableList<KeyPath>,
        currentPartialKeyPath: KeyPath
    ) {
        for (i in layers.indices) {
            layers[i].resolveKeyPath(keyPath, depth, accumulator, currentPartialKeyPath)
        }
    }

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        super.addValueCallback(property, callback)

        if (property == LottieProperty.TIME_REMAP) {
            if (callback == null) {
                if (timeRemapping != null) {
                    timeRemapping!!.setValueCallback(null)
                }
            } else {
                timeRemapping = ValueCallbackKeyframeAnimation<Float, Float>(callback as LottieValueCallback<Float>?).also {
                    it.addUpdateListener(this)
                }
                addAnimation(timeRemapping)
            }
        }
    }
}
