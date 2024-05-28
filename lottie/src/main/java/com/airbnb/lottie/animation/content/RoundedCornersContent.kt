package com.airbnb.lottie.animation.content

import android.graphics.PointF
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.model.CubicCurveData
import com.airbnb.lottie.model.content.RoundedCorners
import com.airbnb.lottie.model.content.ShapeData
import com.airbnb.lottie.model.layer.BaseLayer
import kotlin.math.hypot
import kotlin.math.min

class RoundedCornersContent(
    private val lottieDrawable: LottieDrawable,
    layer: BaseLayer,
    roundedCorners: RoundedCorners
) : ShapeModifierContent,
    BaseKeyframeAnimation.AnimationListener {
    override val name: String = roundedCorners.name
    val roundedCorners: BaseKeyframeAnimation<Float, Float>? = roundedCorners.cornerRadius.createAnimation()
    private var shapeData: ShapeData? = null

    init {
        layer.addAnimation(this.roundedCorners)
        this.roundedCorners!!.addUpdateListener(this)
    }

    override fun onValueChanged() {
        lottieDrawable.invalidateSelf()
    }

    override fun setContents(contentsBefore: List<Content>, contentsAfter: List<Content>) {
        // Do nothing.
    }

    /**
     * Rounded corner algorithm:
     * Iterate through each vertex.
     * If a vertex is a sharp corner, it rounds it.
     * If a vertex has control points, it is already rounded, so it does nothing.
     *
     *
     * To round a vertex:
     * Split the vertex into two.
     * Move vertex 1 directly towards the previous vertex.
     * Set vertex 1's in control point to itself so it is not rounded on that side.
     * Extend vertex 1's out control point towards the original vertex.
     *
     *
     * Repeat for vertex 2:
     * Move vertex 2 directly towards the next vertex.
     * Set vertex 2's out point to itself so it is not rounded on that side.
     * Extend vertex 2's in control point towards the original vertex.
     *
     *
     * The distance that the vertices and control points are moved are relative to the
     * shape's vertex distances and the roundedness set in the animation.
     */
    override fun modifyShape(startingShapeData: ShapeData?): ShapeData? {
        val startingCurves = startingShapeData!!.getCurves()
        if (startingCurves.size <= 2) {
            return startingShapeData
        }
        val roundedness = roundedCorners!!.value
        if (roundedness == 0f) {
            return startingShapeData
        }

        val modifiedShapeData = getShapeData(startingShapeData)
        modifiedShapeData.setInitialPoint(startingShapeData.initialPoint.x, startingShapeData.initialPoint.y)
        val modifiedCurves = modifiedShapeData.getCurves()
        var modifiedCurvesIndex = 0
        val isClosed = startingShapeData.isClosed

        // i represents which vertex we are currently on. Refer to the docs of CubicCurveData prior to working with
        // this code.
        // When i == 0
        //    vertex=ShapeData.initialPoint
        //    inCp=if closed vertex else curves[size - 1].cp2
        //    outCp=curves[0].cp1
        // When i == 1
        //    vertex=curves[0].vertex
        //    inCp=curves[0].cp2
        //    outCp=curves[1].cp1.
        // When i == size - 1
        //    vertex=curves[size - 1].vertex
        //    inCp=curves[size - 1].cp2
        //    outCp=if closed vertex else curves[0].cp1
        for (i in startingCurves.indices) {
            val startingCurve = startingCurves[i]
            val previousCurve = startingCurves[floorMod(i - 1, startingCurves.size)]
            val previousPreviousCurve = startingCurves[floorMod(i - 2, startingCurves.size)]
            val vertex = if ((i == 0 && !isClosed)) startingShapeData.initialPoint else previousCurve.vertex
            val inPoint = if ((i == 0 && !isClosed)) vertex else previousCurve.controlPoint2
            val outPoint = startingCurve.controlPoint1
            val previousVertex = previousPreviousCurve.vertex
            val nextVertex = startingCurve.vertex

            // We can't round the corner of the end of a non-closed curve.
            val isEndOfCurve = !startingShapeData.isClosed && (i == 0 || i == startingCurves.size - 1)
            if (inPoint == vertex && outPoint == vertex && !isEndOfCurve) {
                // This vertex is a point. Round its corners
                val dxToPreviousVertex = vertex.x - previousVertex.x
                val dyToPreviousVertex = vertex.y - previousVertex.y
                val dxToNextVertex = nextVertex.x - vertex.x
                val dyToNextVertex = nextVertex.y - vertex.y

                val dToPreviousVertex = hypot(dxToPreviousVertex.toDouble(), dyToPreviousVertex.toDouble()).toFloat()
                val dToNextVertex = hypot(dxToNextVertex.toDouble(), dyToNextVertex.toDouble()).toFloat()

                val previousVertexPercent = min((roundedness / dToPreviousVertex).toDouble(), 0.5).toFloat()
                val nextVertexPercent = min((roundedness / dToNextVertex).toDouble(), 0.5).toFloat()

                // Split the vertex into two and move each vertex towards the previous/next vertex.
                val newVertex1X = vertex.x + (previousVertex.x - vertex.x) * previousVertexPercent
                val newVertex1Y = vertex.y + (previousVertex.y - vertex.y) * previousVertexPercent
                val newVertex2X = vertex.x + (nextVertex.x - vertex.x) * nextVertexPercent
                val newVertex2Y = vertex.y + (nextVertex.y - vertex.y) * nextVertexPercent

                // Extend the new vertex control point towards the original vertex.
                val newVertex1OutPointX = newVertex1X - (newVertex1X - vertex.x) * ROUNDED_CORNER_MAGIC_NUMBER
                val newVertex1OutPointY = newVertex1Y - (newVertex1Y - vertex.y) * ROUNDED_CORNER_MAGIC_NUMBER
                val newVertex2InPointX = newVertex2X - (newVertex2X - vertex.x) * ROUNDED_CORNER_MAGIC_NUMBER
                val newVertex2InPointY = newVertex2Y - (newVertex2Y - vertex.y) * ROUNDED_CORNER_MAGIC_NUMBER

                // Remap vertex/in/out point to CubicCurveData.
                // Refer to the docs for CubicCurveData for more info on the difference.
                var previousCurveData = modifiedCurves[floorMod(modifiedCurvesIndex - 1, modifiedCurves.size)]
                var currentCurveData = modifiedCurves[modifiedCurvesIndex]
                previousCurveData.setControlPoint2(newVertex1X, newVertex1Y)
                previousCurveData.setVertex(newVertex1X, newVertex1Y)
                if (i == 0) {
                    modifiedShapeData.setInitialPoint(newVertex1X, newVertex1Y)
                }
                currentCurveData.setControlPoint1(newVertex1OutPointX, newVertex1OutPointY)
                modifiedCurvesIndex++

                previousCurveData = currentCurveData
                currentCurveData = modifiedCurves[modifiedCurvesIndex]
                previousCurveData.setControlPoint2(newVertex2InPointX, newVertex2InPointY)
                previousCurveData.setVertex(newVertex2X, newVertex2Y)
                currentCurveData.setControlPoint1(newVertex2X, newVertex2Y)
                modifiedCurvesIndex++
            } else {
                // This vertex is not a point. Don't modify it. Refer to the documentation above and for CubicCurveData for mapping a vertex
                // oriented point to CubicCurveData (path segments).
                val previousCurveData = modifiedCurves[floorMod(modifiedCurvesIndex - 1, modifiedCurves.size)]
                val currentCurveData = modifiedCurves[modifiedCurvesIndex]
                previousCurveData.setControlPoint2(previousCurve.controlPoint2.x, previousCurve.controlPoint2.y)
                previousCurveData.setVertex(previousCurve.vertex.x, previousCurve.vertex.y)
                currentCurveData.setControlPoint1(startingCurve.controlPoint1.x, startingCurve.controlPoint1.y)
                modifiedCurvesIndex++
            }
        }
        return modifiedShapeData
    }

    /**
     * Returns a shape data with the correct number of vertices for the rounded corners shape.
     * This just returns the object. It does not update any values within the shape.
     */
    private fun getShapeData(startingShapeData: ShapeData?): ShapeData {
        val startingCurves = startingShapeData!!.getCurves()
        val isClosed = startingShapeData.isClosed
        var vertices = 0
        for (i in startingCurves.indices.reversed()) {
            val startingCurve = startingCurves[i]
            val previousCurve = startingCurves[floorMod(i - 1, startingCurves.size)]
            val vertex = if ((i == 0 && !isClosed)) startingShapeData.initialPoint else previousCurve.vertex
            val inPoint = if ((i == 0 && !isClosed)) vertex else previousCurve.controlPoint2
            val outPoint = startingCurve.controlPoint1

            val isEndOfCurve = !startingShapeData.isClosed && (i == 0 || i == startingCurves.size - 1)
            vertices += if (inPoint == vertex && outPoint == vertex && !isEndOfCurve) {
                2
            } else {
                1
            }
        }
        if (shapeData == null || shapeData!!.getCurves().size != vertices) {
            val newCurves: MutableList<CubicCurveData> = ArrayList(vertices)
            for (i in 0 until vertices) {
                newCurves.add(CubicCurveData())
            }
            shapeData = ShapeData(PointF(0f, 0f), false, newCurves)
        }
        shapeData!!.isClosed = isClosed
        return shapeData!!
    }

    companion object {
        /**
         * Copied from:
         * https://github.com/airbnb/lottie-web/blob/bb71072a26e03f1ca993da60915860f39aae890b/player/js/utils/common.js#L47
         */
        private const val ROUNDED_CORNER_MAGIC_NUMBER = 0.5519f

        /**
         * Copied from the API 24+ AOSP source.
         */
        private fun floorMod(x: Int, y: Int): Int {
            return x - floorDiv(x, y) * y
        }

        /**
         * Copied from the API 24+ AOSP source.
         */
        private fun floorDiv(x: Int, y: Int): Int {
            var r = x / y
            // if the signs are different and modulo not zero, round down
            if ((x xor y) < 0 && (r * y != x)) {
                r--
            }
            return r
        }
    }
}
