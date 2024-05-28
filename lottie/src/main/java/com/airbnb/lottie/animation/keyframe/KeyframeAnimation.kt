package com.airbnb.lottie.animation.keyframe

import com.airbnb.lottie.value.Keyframe

abstract class KeyframeAnimation<T : Any>(keyframes: List<Keyframe<T>>) : BaseKeyframeAnimation<T, T>(keyframes)
