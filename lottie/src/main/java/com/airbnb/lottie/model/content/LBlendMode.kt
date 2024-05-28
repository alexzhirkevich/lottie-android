package com.airbnb.lottie.model.content

import androidx.core.graphics.BlendModeCompat

/**
 * Lottie BlendMode,
 * not to be confused with Paint.BlendMode in android graphics core,
 * which we will rely on for rendering.
 */
enum class LBlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    COLOR_DODGE,
    COLOR_BURN,
    HARD_LIGHT,
    SOFT_LIGHT,
    DIFFERENCE,
    EXCLUSION,
    HUE,
    SATURATION,
    COLOR,
    LUMINOSITY,
    ADD,
    HARD_MIX;

    fun toNativeBlendMode(): BlendModeCompat? {
        return when (this) {
            NORMAL -> null
            SCREEN -> BlendModeCompat.SCREEN
            OVERLAY -> BlendModeCompat.OVERLAY
            DARKEN -> BlendModeCompat.DARKEN
            LIGHTEN -> BlendModeCompat.LIGHTEN
            ADD -> BlendModeCompat.PLUS

            MULTIPLY, COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR, LUMINOSITY, HARD_MIX -> null
            else -> null
        }
    }
}
