package com.airbnb.lottie

import android.util.Log
import androidx.collection.ArraySet
import androidx.core.util.Pair
import com.airbnb.lottie.utils.MeanCalculator
import java.util.Collections

class PerformanceTracker {
    interface FrameListener {
        fun onFrameRendered(renderTimeMs: Float)
    }

    private var enabled = false
    private val frameListeners: MutableSet<FrameListener> = ArraySet()
    private val layerRenderTimes: MutableMap<String, MeanCalculator> = HashMap()
    private val floatComparator: java.util.Comparator<Pair<String, Float>> = Comparator { o1, o2 ->
        val r1 = o1.second
        val r2 = o2.second
        if (r2 > r1) {
            return@Comparator 1
        } else if (r1 > r2) {
            return@Comparator -1
        }
        0
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun recordRenderTime(layerName: String, millis: Float) {
        if (!enabled) {
            return
        }
        var meanCalculator = layerRenderTimes[layerName]
        if (meanCalculator == null) {
            meanCalculator = MeanCalculator()
            layerRenderTimes[layerName] = meanCalculator
        }
        meanCalculator.add(millis)

        if (layerName == "__container") {
            for (listener in frameListeners) {
                listener.onFrameRendered(millis)
            }
        }
    }

    fun addFrameListener(frameListener: FrameListener) {
        frameListeners.add(frameListener)
    }

    @Suppress("unused")
    fun removeFrameListener(frameListener: FrameListener) {
        frameListeners.remove(frameListener)
    }

    fun clearRenderTimes() {
        layerRenderTimes.clear()
    }

    fun logRenderTimes() {
        if (!enabled) {
            return
        }
        val sortedRenderTimes = sortedRenderTimes
        Log.d(L.TAG, "Render times:")
        for (i in sortedRenderTimes.indices) {
            val layer = sortedRenderTimes[i]
            Log.d(L.TAG, String.format("\t\t%30s:%.2f", layer.first, layer.second))
        }
    }

    val sortedRenderTimes: List<Pair<String, Float>>
        get() {
            if (!enabled) {
                return emptyList()
            }
            val sortedRenderTimes: MutableList<Pair<String, Float>> = ArrayList(layerRenderTimes.size)
            for ((key, value) in layerRenderTimes) {
                sortedRenderTimes.add(Pair(key, value.mean))
            }
            Collections.sort(sortedRenderTimes, floatComparator)
            return sortedRenderTimes
        }
}
