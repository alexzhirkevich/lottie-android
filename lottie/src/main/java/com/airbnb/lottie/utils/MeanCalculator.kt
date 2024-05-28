package com.airbnb.lottie.utils

/**
 * Class to calculate the average in a stream of numbers on a continuous basis.
 */
class MeanCalculator {
    private var sum = 0f
    private var n = 0

    fun add(number: Float) {
        sum += number
        n++
        if (n == Int.MAX_VALUE) {
            sum /= 2f
            n /= 2
        }
    }

    val mean: Float
        get() {
            if (n == 0) {
                return 0f
            }
            return sum / n.toFloat()
        }
}
