package com.airbnb.lottie.utils

import androidx.core.os.TraceCompat

class LottieTrace {
    private val sections = arrayOfNulls<String>(MAX_DEPTH)
    private val startTimeNs = LongArray(MAX_DEPTH)
    private var traceDepth = 0
    private var depthPastMaxDepth = 0

    fun beginSection(section: String?) {
        if (traceDepth == MAX_DEPTH) {
            depthPastMaxDepth++
            return
        }
        sections[traceDepth] = section
        startTimeNs[traceDepth] = System.nanoTime()
        TraceCompat.beginSection(section!!)
        traceDepth++
    }

    fun endSection(section: String): Float {
        if (depthPastMaxDepth > 0) {
            depthPastMaxDepth--
            return 0f
        }
        traceDepth--
        check(traceDepth != -1) { "Can't end trace section. There are none." }
        check(section == sections[traceDepth]) {
            "Unbalanced trace call " + section +
                    ". Expected " + sections[traceDepth] + "."
        }
        TraceCompat.endSection()
        return (System.nanoTime() - startTimeNs[traceDepth]) / 1000000f
    }

    companion object {
        private const val MAX_DEPTH = 5
    }
}
