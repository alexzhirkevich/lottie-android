package com.airbnb.lottie.animation.content

import android.annotation.TargetApi
import android.graphics.Path
import android.os.Build
import com.airbnb.lottie.model.content.MergePaths
import com.airbnb.lottie.model.content.MergePaths.MergePathsMode

@TargetApi(Build.VERSION_CODES.KITKAT)
class MergePathsContent(mergePaths: MergePaths) : PathContent, GreedyContent {
    private val firstPath = Path()
    private val remainderPath = Path()
    private val path = Path()

    override val name: String
    private val pathContents: MutableList<PathContent> = ArrayList()
    private val mergePaths: MergePaths

    init {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { "Merge paths are not supported pre-KitKat." }
        name = mergePaths.name
        this.mergePaths = mergePaths
    }

    override fun absorbContent(contents: MutableListIterator<Content>) {
        // Fast forward the iterator until after this content.
        while (contents.hasPrevious() && contents.previous() !== this) {
        }
        while (contents.hasPrevious()) {
            val content = contents.previous()
            if (content is PathContent) {
                pathContents.add(content)
                contents.remove()
            }
        }
    }

    override fun setContents(contentsBefore: List<Content>, contentsAfter: List<Content>) {
        for (i in pathContents.indices) {
            pathContents[i].setContents(contentsBefore, contentsAfter)
        }
    }

    override fun getPath(): Path {
        path.reset()

        if (mergePaths.isHidden) {
            return path
        }

        when (mergePaths.mode) {
            MergePathsMode.MERGE -> addPaths()
            MergePathsMode.ADD -> opFirstPathWithRest(Path.Op.UNION)
            MergePathsMode.SUBTRACT -> opFirstPathWithRest(Path.Op.REVERSE_DIFFERENCE)
            MergePathsMode.INTERSECT -> opFirstPathWithRest(Path.Op.INTERSECT)
            MergePathsMode.EXCLUDE_INTERSECTIONS -> opFirstPathWithRest(Path.Op.XOR)
        }
        return path
    }

    private fun addPaths() {
        for (i in pathContents.indices) {
            path.addPath(pathContents[i].getPath())
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun opFirstPathWithRest(op: Path.Op) {
        remainderPath.reset()
        firstPath.reset()

        for (i in pathContents.size - 1 downTo 1) {
            val content = pathContents[i]

            if (content is ContentGroup) {
                val pathList = content.pathList
                for (j in pathList.indices.reversed()) {
                    val path: Path = pathList[j].getPath()
                    path.transform(content.transformationMatrix)
                    remainderPath.addPath(path)
                }
            } else {
                remainderPath.addPath(content.getPath())
            }
        }

        val lastContent = pathContents[0]
        if (lastContent is ContentGroup) {
            val pathList = lastContent.pathList
            for (j in pathList.indices) {
                val path: Path = pathList[j].getPath()
                path.transform(lastContent.transformationMatrix)
                firstPath.addPath(path)
            }
        } else {
            firstPath.set(lastContent.getPath())
        }

        path.op(firstPath, remainderPath, op)
    }
}
