package com.airbnb.lottie.model.content

import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.model.layer.BaseLayer


interface ContentModel {
    fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content?
}
