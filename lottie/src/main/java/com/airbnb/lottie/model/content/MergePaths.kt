package com.airbnb.lottie.model.content

import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.content.Content
import com.airbnb.lottie.animation.content.MergePathsContent
import com.airbnb.lottie.model.layer.BaseLayer
import com.airbnb.lottie.utils.Logger.warning

class MergePaths(
    @JvmField val name: String,
    @JvmField val mode: MergePathsMode,
    val isHidden: Boolean
) : ContentModel {
    enum class MergePathsMode {
        MERGE,
        ADD,
        SUBTRACT,
        INTERSECT,
        EXCLUDE_INTERSECTIONS;

        companion object {
            @JvmStatic
            fun forId(id: Int): MergePathsMode {
                return when (id) {
                    1 -> MERGE
                    2 -> ADD
                    3 -> SUBTRACT
                    4 -> INTERSECT
                    5 -> EXCLUDE_INTERSECTIONS
                    else -> MERGE
                }
            }
        }
    }

    override fun toContent(drawable: LottieDrawable, composition: LottieComposition, layer: BaseLayer): Content? {
        if (!drawable.enableMergePathsForKitKatAndAbove()) {
            warning("Animation contains merge paths but they are disabled.")
            return null
        }
        return MergePathsContent(this)
    }

    override fun toString(): String {
        return "MergePaths{mode=$mode}"
    }
}
