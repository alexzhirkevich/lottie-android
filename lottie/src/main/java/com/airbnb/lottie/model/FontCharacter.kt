package com.airbnb.lottie.model

import androidx.annotation.RestrictTo
import com.airbnb.lottie.model.content.ShapeGroup

@RestrictTo(RestrictTo.Scope.LIBRARY)
class FontCharacter(
  @JvmField val shapes: List<ShapeGroup>,
  private val character: Char,
  private val size: Double,
  @JvmField val width: Double,
  private val style: String,
  private val fontFamily: String
) {
    override fun hashCode(): Int {
        return hashFor(character, fontFamily, style)
    }

    companion object {
        @JvmStatic
        fun hashFor(character: Char, fontFamily: String, style: String): Int {
            var result = character.code
            result = 31 * result + fontFamily.hashCode()
            result = 31 * result + style.hashCode()
            return result
        }
    }
}
