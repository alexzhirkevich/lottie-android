package com.airbnb.lottie.value

class ScaleXY @JvmOverloads constructor(@JvmField var scaleX: Float = 1f, @JvmField var scaleY: Float = 1f) {
    fun set(scaleX: Float, scaleY: Float) {
        this.scaleX = scaleX
        this.scaleY = scaleY
    }

    fun equals(scaleX: Float, scaleY: Float): Boolean {
        return this.scaleX == scaleX && this.scaleY == scaleY
    }

    override fun toString(): String {
        return scaleX.toString() + "x" + scaleY
    }
}
