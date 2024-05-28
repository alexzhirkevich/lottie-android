package com.airbnb.lottie.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import com.airbnb.lottie.L.beginSection
import com.airbnb.lottie.L.endSection
import com.airbnb.lottie.L.isTraceEnabled
import com.airbnb.lottie.animation.LPaint
import com.airbnb.lottie.animation.content.TrimPathContent
import com.airbnb.lottie.animation.keyframe.FloatKeyframeAnimation
import com.airbnb.lottie.utils.MiscUtils.floorMod
import java.io.Closeable
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.SocketException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.nio.channels.ClosedChannelException
import javax.net.ssl.SSLException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object Utils {
    const val SECOND_IN_NANOS: Int = 1000000000

    /**
     * Wrap in Local Thread is necessary for prevent race condition in multi-threaded mode
     */
    private val threadLocalPathMeasure: ThreadLocal<PathMeasure> = object : ThreadLocal<PathMeasure>() {
        override fun initialValue(): PathMeasure {
            return PathMeasure()
        }
    }

    private val threadLocalTempPath: ThreadLocal<Path> = object : ThreadLocal<Path>() {
        override fun initialValue(): Path {
            return Path()
        }
    }

    private val threadLocalTempPath2: ThreadLocal<Path> = object : ThreadLocal<Path>() {
        override fun initialValue(): Path {
            return Path()
        }
    }

    private val threadLocalPoints: ThreadLocal<FloatArray> = object : ThreadLocal<FloatArray>() {
        override fun initialValue(): FloatArray {
            return FloatArray(4)
        }
    }

    private val INV_SQRT_2 = (sqrt(2.0) / 2.0).toFloat()

    @JvmStatic
    fun createPath(startPoint: PointF, endPoint: PointF, cp1: PointF?, cp2: PointF?): Path {
        val path = Path()
        path.moveTo(startPoint.x, startPoint.y)

        if (cp1 != null && cp2 != null && (cp1.length() != 0f || cp2.length() != 0f)) {
            path.cubicTo(
                startPoint.x + cp1.x, startPoint.y + cp1.y,
                endPoint.x + cp2.x, endPoint.y + cp2.y,
                endPoint.x, endPoint.y
            )
        } else {
            path.lineTo(endPoint.x, endPoint.y)
        }
        return path
    }

    fun closeQuietly(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (rethrown: RuntimeException) {
                throw rethrown
            } catch (ignored: Exception) {
                // Ignore.
            }
        }
    }

    @JvmStatic
    fun getScale(matrix: Matrix): Float {
        val points = threadLocalPoints.get()

        points[0] = 0f
        points[1] = 0f
        // Use 1/sqrt(2) so that the hypotenuse is of length 1.
        points[2] = INV_SQRT_2
        points[3] = INV_SQRT_2
        matrix.mapPoints(points)
        val dx = points[2] - points[0]
        val dy = points[3] - points[1]

        return hypot(dx.toDouble(), dy.toDouble()).toFloat()
    }

    @JvmStatic
    fun hasZeroScaleAxis(matrix: Matrix): Boolean {
        val points = threadLocalPoints.get()

        points[0] = 0f
        points[1] = 0f
        // Random numbers. The only way these should map to the same thing as 0,0 is if the scale is 0.
        points[2] = 37394.729378f
        points[3] = 39575.2343807f
        matrix.mapPoints(points)
        return points[0] == points[2] || points[1] == points[3]
    }

    @JvmStatic
    fun applyTrimPathIfNeeded(path: Path, trimPath: TrimPathContent?) {
        if (trimPath == null || trimPath.isHidden) {
            return
        }
        val start = (trimPath.start as FloatKeyframeAnimation).floatValue
        val end = (trimPath.end as FloatKeyframeAnimation).floatValue
        val offset = (trimPath.offset as FloatKeyframeAnimation).floatValue
        applyTrimPathIfNeeded(path, start / 100f, end / 100f, offset / 360f)
    }

    @JvmStatic
    fun applyTrimPathIfNeeded(
        path: Path, startValue: Float, endValue: Float, offsetValue: Float
    ) {
        if (isTraceEnabled()) {
            beginSection("applyTrimPathIfNeeded")
        }
        val pathMeasure = threadLocalPathMeasure.get()
        val tempPath = threadLocalTempPath.get()
        val tempPath2 = threadLocalTempPath2.get()

        pathMeasure.setPath(path, false)

        val length = pathMeasure.length
        if (startValue == 1f && endValue == 0f) {
            if (isTraceEnabled()) {
                endSection("applyTrimPathIfNeeded")
            }
            return
        }
        if (length < 1f || abs((endValue - startValue - 1).toDouble()) < .01) {
            if (isTraceEnabled()) {
                endSection("applyTrimPathIfNeeded")
            }
            return
        }
        val start = length * startValue
        val end = length * endValue
        var newStart = min(start.toDouble(), end.toDouble()).toFloat()
        var newEnd = max(start.toDouble(), end.toDouble()).toFloat()

        val offset = offsetValue * length
        newStart += offset
        newEnd += offset

        // If the trim path has rotated around the path, we need to shift it back.
        if (newStart >= length && newEnd >= length) {
            newStart = floorMod(newStart, length).toFloat()
            newEnd = floorMod(newEnd, length).toFloat()
        }

        if (newStart < 0) {
            newStart = floorMod(newStart, length).toFloat()
        }
        if (newEnd < 0) {
            newEnd = floorMod(newEnd, length).toFloat()
        }

        // If the start and end are equals, return an empty path.
        if (newStart == newEnd) {
            path.reset()
            if (isTraceEnabled()) {
                endSection("applyTrimPathIfNeeded")
            }
            return
        }

        if (newStart >= newEnd) {
            newStart -= length
        }

        tempPath.reset()
        pathMeasure.getSegment(
            newStart,
            newEnd,
            tempPath,
            true
        )

        if (newEnd > length) {
            tempPath2.reset()
            pathMeasure.getSegment(
                0f,
                newEnd % length,
                tempPath2,
                true
            )
            tempPath.addPath(tempPath2)
        } else if (newStart < 0) {
            tempPath2.reset()
            pathMeasure.getSegment(
                length + newStart,
                length,
                tempPath2,
                true
            )
            tempPath.addPath(tempPath2)
        }
        path.set(tempPath)
        if (isTraceEnabled()) {
            endSection("applyTrimPathIfNeeded")
        }
    }

    @JvmStatic
    fun isAtLeastVersion(major: Int, minor: Int, patch: Int, minMajor: Int, minMinor: Int, minPatch: Int): Boolean {
        if (major < minMajor) {
            return false
        } else if (major > minMajor) {
            return true
        }

        if (minor < minMinor) {
            return false
        } else if (minor > minMinor) {
            return true
        }

        return patch >= minPatch
    }

    @JvmStatic
    fun hashFor(a: Float, b: Float, c: Float, d: Float): Int {
        var result = 17
        if (a != 0f) {
            result = (31 * result * a).toInt()
        }
        if (b != 0f) {
            result = (31 * result * b).toInt()
        }
        if (c != 0f) {
            result = (31 * result * c).toInt()
        }
        if (d != 0f) {
            result = (31 * result * d).toInt()
        }
        return result
    }

    @JvmStatic
    fun dpScale(): Float {
        return Resources.getSystem().displayMetrics.density
    }

    fun getAnimationScale(context: Context): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f
            )
        } else {
            Settings.System.getFloat(
                context.contentResolver,
                Settings.System.ANIMATOR_DURATION_SCALE, 1.0f
            )
        }
    }

    /**
     * Resize the bitmap to exactly the same size as the specified dimension, changing the aspect ratio if needed.
     * Returns the original bitmap if the dimensions already match.
     */
    @JvmStatic
    fun resizeBitmapIfNeeded(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) {
            return bitmap
        }
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        bitmap.recycle()
        return resizedBitmap
    }

    /**
     * From http://vaibhavblogs.org/2012/12/common-java-networking-exceptions/
     */
    fun isNetworkException(e: Throwable?): Boolean {
        return e is SocketException || e is ClosedChannelException ||
                e is InterruptedIOException || e is ProtocolException ||
                e is SSLException || e is UnknownHostException ||
                e is UnknownServiceException
    }

    @JvmStatic
    @JvmOverloads
    fun saveLayerCompat(canvas: Canvas, rect: RectF?, paint: Paint?, flag: Int = Canvas.ALL_SAVE_FLAG) {
        if (isTraceEnabled()) {
            beginSection("Utils#saveLayer")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // This method was deprecated in API level 26 and not recommended since 22, but its
            // 2-parameter replacement is only available starting at API level 21.
            canvas.saveLayer(rect, paint, flag)
        } else {
            canvas.saveLayer(rect, paint)
        }
        if (isTraceEnabled()) {
            endSection("Utils#saveLayer")
        }
    }

    /**
     * For testing purposes only. DO NOT USE IN PRODUCTION.
     */
    @Suppress("unused")
    fun renderPath(path: Path): Bitmap {
        val bounds = RectF()
        path.computeBounds(bounds, false)
        val bitmap = Bitmap.createBitmap(bounds.right.toInt(), bounds.bottom.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint: Paint = LPaint()
        paint.isAntiAlias = true
        paint.color = Color.BLUE
        canvas.drawPath(path, paint)
        return bitmap
    }
}
