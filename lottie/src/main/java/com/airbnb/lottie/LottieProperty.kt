package com.airbnb.lottie

import android.graphics.Bitmap
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import com.airbnb.lottie.value.ScaleXY

/**
 * Property values are the same type as the generic type of their corresponding
 * [LottieValueCallback]. With this, we can use generics to maintain type safety
 * of the callbacks.
 *
 *
 * Supported properties:
 * Transform:
 * [.TRANSFORM_ANCHOR_POINT]
 * [.TRANSFORM_POSITION]
 * [.TRANSFORM_OPACITY]
 * [.TRANSFORM_SCALE]
 * [.TRANSFORM_ROTATION]
 * [.TRANSFORM_SKEW]
 * [.TRANSFORM_SKEW_ANGLE]
 *
 *
 * Fill:
 * [.COLOR] (non-gradient)
 * [.OPACITY]
 * [.COLOR_FILTER]
 *
 *
 * Stroke:
 * [.COLOR] (non-gradient)
 * [.STROKE_WIDTH]
 * [.OPACITY]
 * [.COLOR_FILTER]
 *
 *
 * Ellipse:
 * [.POSITION]
 * [.ELLIPSE_SIZE]
 *
 *
 * Polystar:
 * [.POLYSTAR_POINTS]
 * [.POLYSTAR_ROTATION]
 * [.POSITION]
 * [.POLYSTAR_INNER_RADIUS] (star)
 * [.POLYSTAR_OUTER_RADIUS]
 * [.POLYSTAR_INNER_ROUNDEDNESS] (star)
 * [.POLYSTAR_OUTER_ROUNDEDNESS]
 *
 *
 * Repeater:
 * All transform properties
 * [.REPEATER_COPIES]
 * [.REPEATER_OFFSET]
 * [.TRANSFORM_ROTATION]
 * [.TRANSFORM_START_OPACITY]
 * [.TRANSFORM_END_OPACITY]
 *
 *
 * Layers:
 * All transform properties
 * [.TIME_REMAP] (composition layers only)
 */
interface LottieProperty {
    companion object {
        /**
         * ColorInt
         */
        const val COLOR: Int = 1
        const val STROKE_COLOR: Int = 2

        /**
         * Opacity value are 0-100 to match after effects
         */
        const val TRANSFORM_OPACITY: Int = 3

        /**
         * [0,100]
         */
        const val OPACITY: Int = 4
        const val DROP_SHADOW_COLOR: Int = 5

        /**
         * In Px
         */
        val TRANSFORM_ANCHOR_POINT: PointF = PointF()

        /**
         * In Px
         */
        val TRANSFORM_POSITION: PointF = PointF()

        /**
         * When split dimensions is enabled. In Px
         */
        const val TRANSFORM_POSITION_X: Float = 15f

        /**
         * When split dimensions is enabled. In Px
         */
        const val TRANSFORM_POSITION_Y: Float = 16f

        /**
         * In Px
         */
        const val BLUR_RADIUS: Float = 17f

        /**
         * In Px
         */
        val ELLIPSE_SIZE: PointF = PointF()

        /**
         * In Px
         */
        val RECTANGLE_SIZE: PointF = PointF()

        /**
         * In degrees
         */
        const val CORNER_RADIUS: Float = 0f

        /**
         * In Px
         */
        val POSITION: PointF = PointF()
        val TRANSFORM_SCALE: ScaleXY = ScaleXY()

        /**
         * In degrees
         */
        const val TRANSFORM_ROTATION: Float = 1f

        /**
         * 0-85
         */
        const val TRANSFORM_SKEW: Float = 0f

        /**
         * In degrees
         */
        const val TRANSFORM_SKEW_ANGLE: Float = 0f

        /**
         * In Px
         */
        const val STROKE_WIDTH: Float = 2f
        const val TEXT_TRACKING: Float = 3f
        const val REPEATER_COPIES: Float = 4f
        const val REPEATER_OFFSET: Float = 5f
        const val POLYSTAR_POINTS: Float = 6f

        /**
         * In degrees
         */
        const val POLYSTAR_ROTATION: Float = 7f

        /**
         * In Px
         */
        const val POLYSTAR_INNER_RADIUS: Float = 8f

        /**
         * In Px
         */
        const val POLYSTAR_OUTER_RADIUS: Float = 9f

        /**
         * [0,100]
         */
        const val POLYSTAR_INNER_ROUNDEDNESS: Float = 10f

        /**
         * [0,100]
         */
        const val POLYSTAR_OUTER_ROUNDEDNESS: Float = 11f

        /**
         * [0,100]
         */
        const val TRANSFORM_START_OPACITY: Float = 12f

        /**
         * [0,100]
         */
        const val TRANSFORM_END_OPACITY: Float = 12.1f

        /**
         * The time value in seconds
         */
        const val TIME_REMAP: Float = 13f

        /**
         * In Dp
         */
        const val TEXT_SIZE: Float = 14f

        /**
         * [0,100]
         * Lottie Android resolved drop shadows on drawing content such as fills and strokes.
         * If a drop shadow is applied to a layer, the dynamic properties must be set on all
         * of its child elements that draw. The easiest way to do this is to append "**" to your
         * Keypath after the layer name.
         */
        const val DROP_SHADOW_OPACITY: Float = 15f

        /**
         * Degrees from 12 o'clock.
         * Lottie Android resolved drop shadows on drawing content such as fills and strokes.
         * If a drop shadow is applied to a layer, the dynamic properties must be set on all
         * of its child elements that draw. The easiest way to do this is to append "**" to your
         * Keypath after the layer name.
         */
        const val DROP_SHADOW_DIRECTION: Float = 16f

        /**
         * In Px
         * Lottie Android resolved drop shadows on drawing content such as fills and strokes.
         * If a drop shadow is applied to a layer, the dynamic properties must be set on all
         * of its child elements that draw. The easiest way to do this is to append "**" to your
         * Keypath after the layer name.
         */
        const val DROP_SHADOW_DISTANCE: Float = 17f

        /**
         * In Px
         * Lottie Android resolved drop shadows on drawing content such as fills and strokes.
         * If a drop shadow is applied to a layer, the dynamic properties must be set on all
         * of its child elements that draw. The easiest way to do this is to append "**" to your
         * Keypath after the layer name.
         */
        const val DROP_SHADOW_RADIUS: Float = 18f

        /**
         * Set the color filter for an entire drawable content. Can be applied to fills, strokes, images, and solids.
         */
        val COLOR_FILTER: ColorFilter = ColorFilter()

        /**
         * Array of ARGB colors that map to position stops in the original gradient.
         * For example, a gradient from red to blue could be remapped with [0xFF00FF00, 0xFFFF00FF] (green to purple).
         */
        val GRADIENT_COLOR: Array<Int?> = arrayOfNulls(0)

        /**
         * Set on text layers.
         */
        val TYPEFACE: Typeface = Typeface.DEFAULT

        /**
         * Set on image layers.
         */
        val IMAGE: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)

        /**
         * Replace the text for a text layer.
         */
        val TEXT: CharSequence = "dynamic_text"

        /**
         * Replace a path. This can only be used on path contents. For other shapes such as rectangles and polystars,
         * use LottieProperties corresponding to their specific properties.
         *
         *
         * If you need to do any operations on the path such as morphing, use the Jetpack androidx.graphics.path library.
         *
         *
         * In After Effects, any of those other shapes can be converted to a bezier path by right clicking it and
         * selecting "Convert To Bezier Path".
         */
        val PATH: Path = Path()
    }
}
