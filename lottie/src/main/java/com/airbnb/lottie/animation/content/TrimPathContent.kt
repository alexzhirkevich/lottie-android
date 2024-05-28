package com.airbnb.lottie.animation.content

import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.model.content.ShapeTrimPath
import com.airbnb.lottie.model.layer.BaseLayer

class TrimPathContent(layer: BaseLayer, trimPath: ShapeTrimPath) : Content, BaseKeyframeAnimation.AnimationListener {
    override val name: String = trimPath.name
    val isHidden: Boolean = trimPath.isHidden
    private val listeners: MutableList<BaseKeyframeAnimation.AnimationListener> = ArrayList()
    @JvmField
    val type: ShapeTrimPath.Type = trimPath.type
    val start: BaseKeyframeAnimation<Float, Float>
    val end: BaseKeyframeAnimation<Float, Float>
    val offset: BaseKeyframeAnimation<Float, Float>

    init {
        start = trimPath.start.createAnimation()
        end = trimPath.end.createAnimation()
        offset = trimPath.offset.createAnimation()

        layer.addAnimation(start)
        layer.addAnimation(end)
        layer.addAnimation(offset)

        start.addUpdateListener(this)
        end.addUpdateListener(this)
        offset.addUpdateListener(this)
    }

    override fun onValueChanged() {
        for (i in listeners.indices) {
            listeners[i].onValueChanged()
        }
    }

    override fun setContents(contentsBefore: List<Content>, contentsAfter: List<Content>) {
        // Do nothing.
    }

    fun addListener(listener: BaseKeyframeAnimation.AnimationListener) {
        listeners.add(listener)
    }
}
