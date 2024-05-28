package com.airbnb.lottie.animation.content

import android.graphics.Path

interface PathContent : Content {
    fun getPath(): Path
}
