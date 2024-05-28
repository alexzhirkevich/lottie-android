package com.airbnb.lottie.model.layer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.LPaint
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ValueCallbackKeyframeAnimation
import com.airbnb.lottie.utils.Utils.dpScale
import com.airbnb.lottie.value.LottieValueCallback

class ImageLayer internal constructor(lottieDrawable: LottieDrawable, layerModel: Layer) : BaseLayer(lottieDrawable, layerModel) {
    private val paint: Paint = LPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()
    private val lottieImageAsset = lottieDrawable.getLottieImageAssetForId(layerModel.refId)
    private var colorFilterAnimation: BaseKeyframeAnimation<ColorFilter, ColorFilter>? = null
    private var imageAnimation: BaseKeyframeAnimation<Bitmap, Bitmap>? = null

    override fun drawLayer(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int) {
        val bitmap = bitmap
        if (bitmap == null || bitmap.isRecycled || lottieImageAsset == null) {
            return
        }
        val density = dpScale()

        paint.alpha = parentAlpha
        if (colorFilterAnimation != null) {
            paint.setColorFilter(colorFilterAnimation!!.value)
        }
        canvas.save()
        canvas.concat(parentMatrix)
        src[0, 0, bitmap.width] = bitmap.height
        if (lottieDrawable.maintainOriginalImageBounds) {
            dst[0, 0, (lottieImageAsset.width * density).toInt()] = (lottieImageAsset.height * density).toInt()
        } else {
            dst[0, 0, (bitmap.width * density).toInt()] = (bitmap.height * density).toInt()
        }

        canvas.drawBitmap(bitmap, src, dst, paint)
        canvas.restore()
    }

    override fun getBounds(outBounds: RectF, parentMatrix: Matrix, applyParents: Boolean) {
        super.getBounds(outBounds, parentMatrix, applyParents)
        if (lottieImageAsset != null) {
            val scale = dpScale()
            outBounds[0f, 0f, lottieImageAsset.width * scale] = lottieImageAsset.height * scale
            boundsMatrix.mapRect(outBounds)
        }
    }

    private val bitmap: Bitmap?
        get() {
            if (imageAnimation != null) {
                val callbackBitmap = imageAnimation!!.value
                if (callbackBitmap != null) {
                    return callbackBitmap
                }
            }
            val refId = layerModel.refId
            val bitmapFromDrawable = lottieDrawable.getBitmapForId(refId)
            if (bitmapFromDrawable != null) {
                return bitmapFromDrawable
            }
            val asset = this.lottieImageAsset
            if (asset != null) {
                return asset.bitmap
            }
            return null
        }

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        super.addValueCallback(property, callback)
        if (property === LottieProperty.COLOR_FILTER) {
            colorFilterAnimation = if (callback == null) {
                null
            } else {
                ValueCallbackKeyframeAnimation(callback as LottieValueCallback<ColorFilter>?)
            }
        } else if (property === LottieProperty.IMAGE) {
            imageAnimation = if (callback == null) {
                null
            } else {
                ValueCallbackKeyframeAnimation(callback as LottieValueCallback<Bitmap>?)
            }
        }
    }
}
