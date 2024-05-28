package com.airbnb.lottie.model

import androidx.annotation.RestrictTo
import androidx.core.util.Pair

/**
 * Non final version of [Pair].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class MutablePair<T> {
    var first: T? = null
    var second: T? = null

    fun set(first: T, second: T) {
        this.first = first
        this.second = second
    }

    /**
     * Checks the two objects for equality by delegating to their respective
     * [Object.equals] methods.
     *
     * @param o the [Pair] to which this one is to be checked for equality
     * @return true if the underlying objects of the Pair are both considered
     * equal
     */
    override fun equals(o: Any?): Boolean {
        if (o !is Pair<*, *>) {
            return false
        }
        val p = o
        return objectsEqual(p.first, first) && objectsEqual(p.second, second)
    }

    /**
     * Compute a hash code using the hash codes of the underlying objects
     *
     * @return a hashcode of the Pair
     */
    override fun hashCode(): Int {
        return (if (first == null) 0 else first.hashCode()) xor (if (second == null) 0 else second.hashCode())
    }

    override fun toString(): String {
        return "Pair{$first $second}"
    }

    companion object {
        private fun objectsEqual(a: Any?, b: Any?): Boolean {
            return a === b || (a != null && a == b)
        }
    }
}
