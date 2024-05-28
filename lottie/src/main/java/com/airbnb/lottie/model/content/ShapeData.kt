package com.airbnb.lottie.model.content

import android.graphics.PointF
import androidx.annotation.FloatRange
import com.airbnb.lottie.model.CubicCurveData
import com.airbnb.lottie.utils.Logger.warning
import com.airbnb.lottie.utils.MiscUtils.lerp
import kotlin.math.min

class ShapeData(
    val initialPoint: PointF = PointF(),
    var isClosed: Boolean = false,
    private val startCurves: MutableList<CubicCurveData> = mutableListOf()
) {

    fun setInitialPoint(x: Float, y: Float) {
        initialPoint.set(x, y)
    }

    fun getCurves(): List<CubicCurveData> {
        return startCurves
    }

    fun interpolateBetween(
        shapeData1: ShapeData,
        shapeData2: ShapeData,
        @FloatRange(from = 0.0, to = 1.0) percentage: Float
    ) {
        this.isClosed = shapeData1.isClosed || shapeData2.isClosed


        if (shapeData1.getCurves().size != shapeData2.getCurves().size) {
            warning(
                "Curves must have the same number of control points. Shape 1: " +
                        shapeData1.getCurves().size + "\tShape 2: " + shapeData2.getCurves().size
            )
        }

        val points = min(shapeData1.getCurves().size.toDouble(), shapeData2.getCurves().size.toDouble()).toInt()
        if (startCurves.size < points) {
            for (i in startCurves.size until points) {
                startCurves.add(CubicCurveData())
            }
        } else if (startCurves.size > points) {
            for (i in startCurves.size - 1 downTo points) {
                startCurves.removeAt(startCurves.size - 1)
            }
        }

        val initialPoint1 = shapeData1.initialPoint
        val initialPoint2 = shapeData2.initialPoint

        setInitialPoint(
            lerp(initialPoint1!!.x, initialPoint2!!.x, percentage),
            lerp(initialPoint1.y, initialPoint2.y, percentage)
        )

        for (i in startCurves.indices.reversed()) {
            val curve1 = shapeData1.getCurves()[i]
            val curve2 = shapeData2.getCurves()[i]

            val cp11 = curve1.controlPoint1
            val cp21 = curve1.controlPoint2
            val vertex1 = curve1.vertex

            val cp12 = curve2.controlPoint1
            val cp22 = curve2.controlPoint2
            val vertex2 = curve2.vertex

            startCurves[i].setControlPoint1(
                lerp(cp11.x, cp12.x, percentage), lerp(
                    cp11.y, cp12.y,
                    percentage
                )
            )
            startCurves[i].setControlPoint2(
                lerp(cp21.x, cp22.x, percentage), lerp(
                    cp21.y, cp22.y,
                    percentage
                )
            )
            startCurves[i].setVertex(
                lerp(vertex1.x, vertex2.x, percentage), lerp(
                    vertex1.y, vertex2.y,
                    percentage
                )
            )
        }
    }

    override fun toString(): String {
        return "ShapeData{" + "numCurves=" + startCurves.size +
                "closed=" + isClosed +
                '}'
    }
}
