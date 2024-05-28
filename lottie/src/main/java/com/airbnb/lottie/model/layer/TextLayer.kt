package com.airbnb.lottie.model.layer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.collection.LongSparseArray
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.animation.content.ContentGroup
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.TextKeyframeAnimation
import com.airbnb.lottie.animation.keyframe.ValueCallbackKeyframeAnimation
import com.airbnb.lottie.model.DocumentData
import com.airbnb.lottie.model.DocumentData.Justification
import com.airbnb.lottie.model.Font
import com.airbnb.lottie.model.FontCharacter
import com.airbnb.lottie.model.FontCharacter.Companion.hashFor
import com.airbnb.lottie.utils.Utils.dpScale
import com.airbnb.lottie.utils.Utils.getScale
import com.airbnb.lottie.value.LottieValueCallback
import java.util.Arrays

class TextLayer internal constructor(
    lottieDrawable: LottieDrawable,
    layerModel: Layer
) : BaseLayer(lottieDrawable, layerModel) {
    // Capacity is 2 because emojis are 2 characters. Some are longer in which case, the capacity will
    // be expanded but that should be pretty rare.
    private val stringBuilder = StringBuilder(2)
    private val rectF = RectF()
    private val matrix = Matrix()
    private val fillPaint: Paint = object : Paint(ANTI_ALIAS_FLAG) {
        init {
            style = Style.FILL
        }
    }
    private val strokePaint: Paint = object : Paint(ANTI_ALIAS_FLAG) {
        init {
            style = Style.STROKE
        }
    }
    private val contentsForCharacter: MutableMap<FontCharacter, List<ContentGroup>> = HashMap()
    private val codePointCache = LongSparseArray<String>()

    /**
     * If this is paragraph text, one line may wrap depending on the size of the document data box.
     */
    private val textSubLines: MutableList<TextSubLine> = ArrayList()
    private val textAnimation: TextKeyframeAnimation = layerModel.text!!.createAnimation()
    private val composition = layerModel.composition
    private var colorAnimation: BaseKeyframeAnimation<Int, Int>? = null
    private var colorCallbackAnimation: BaseKeyframeAnimation<Int, Int>? = null
    private var strokeColorAnimation: BaseKeyframeAnimation<Int, Int>? = null
    private var strokeColorCallbackAnimation: BaseKeyframeAnimation<Int, Int>? = null
    private var strokeWidthAnimation: BaseKeyframeAnimation<Float, Float>? = null
    private var strokeWidthCallbackAnimation: BaseKeyframeAnimation<Float, Float>? = null
    private var trackingAnimation: BaseKeyframeAnimation<Float, Float>? = null
    private var trackingCallbackAnimation: BaseKeyframeAnimation<Float, Float>? = null
    private var textSizeCallbackAnimation: BaseKeyframeAnimation<Float, Float>? = null
    private var typefaceCallbackAnimation: BaseKeyframeAnimation<Typeface, Typeface>? = null

    init {
        textAnimation.addUpdateListener(this)
        addAnimation(textAnimation)

        val textProperties = layerModel.textProperties
        if (textProperties?.color != null) {
            colorAnimation = textProperties.color.createAnimation()?.also {
                it.addUpdateListener(this)
                addAnimation(it)
            }
        }

        if (textProperties?.stroke != null) {
            strokeColorAnimation = textProperties.stroke.createAnimation().also {
                it.addUpdateListener(this)
                addAnimation(it)
            }
        }

        if (textProperties?.strokeWidth != null) {
            strokeWidthAnimation = textProperties.strokeWidth.createAnimation().also {
                it.addUpdateListener(this)
                addAnimation(it)
            }
        }

        if (textProperties?.tracking != null) {
            trackingAnimation = textProperties.tracking.createAnimation().also {
                it.addUpdateListener(this)
                addAnimation(it)
            }
        }
    }

    override fun getBounds(outBounds: RectF, parentMatrix: Matrix, applyParents: Boolean) {
        super.getBounds(outBounds, parentMatrix, applyParents)
        // TODO: use the correct text bounds.
        outBounds[0f, 0f, composition.bounds!!.width().toFloat()] = composition.bounds!!.height().toFloat()
    }

    override fun drawLayer(canvas: Canvas, parentMatrix: Matrix, parentAlpha: Int) {
        val documentData = textAnimation.value
        val font = composition.fonts!![documentData.fontName!!] ?: return
        canvas.save()
        canvas.concat(parentMatrix)

        configurePaint(documentData, parentAlpha)

        if (lottieDrawable.useTextGlyphs()) {
            drawTextWithGlyphs(documentData, parentMatrix, font, canvas)
        } else {
            drawTextWithFont(documentData, font, canvas)
        }

        canvas.restore()
    }

    private fun configurePaint(documentData: DocumentData, parentAlpha: Int) {
        if (colorCallbackAnimation != null) {
            fillPaint.color = colorCallbackAnimation!!.value!!
        } else if (colorAnimation != null) {
            fillPaint.color = colorAnimation!!.value!!
        } else {
            fillPaint.color = documentData.color
        }

        if (strokeColorCallbackAnimation != null) {
            strokePaint.color = strokeColorCallbackAnimation!!.value!!
        } else if (strokeColorAnimation != null) {
            strokePaint.color = strokeColorAnimation!!.value!!
        } else {
            strokePaint.color = documentData.strokeColor
        }
        val opacity = if (transform.getOpacity() == null) 100 else transform.getOpacity()!!.value
        val alpha = opacity * 255 / 100 * parentAlpha / 255
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha

        if (strokeWidthCallbackAnimation != null) {
            strokePaint.strokeWidth = strokeWidthCallbackAnimation!!.value!!
        } else if (strokeWidthAnimation != null) {
            strokePaint.strokeWidth = strokeWidthAnimation!!.value!!
        } else {
            strokePaint.strokeWidth = documentData.strokeWidth * dpScale()
        }
    }

    private fun drawTextWithGlyphs(
        documentData: DocumentData, parentMatrix: Matrix, font: Font, canvas: Canvas
    ) {
        val textSize = if (textSizeCallbackAnimation != null) {
            textSizeCallbackAnimation!!.value
        } else {
            documentData.size
        }
        val fontScale = textSize / 100f
        val parentScale = getScale(parentMatrix)

        val text = documentData.text

        // Split full text in multiple lines
        val textLines = getTextLines(text)
        val textLineCount = textLines.size
        // Add tracking
        var tracking = documentData.tracking / 10f
        if (trackingCallbackAnimation != null) {
            tracking += trackingCallbackAnimation!!.value
        } else if (trackingAnimation != null) {
            tracking += trackingAnimation!!.value
        }
        var lineIndex = -1
        for (i in 0 until textLineCount) {
            val textLine = textLines[i]
            val boxWidth = if (documentData.boxSize == null) 0f else documentData.boxSize!!.x
            val lines = splitGlyphTextIntoLines(textLine, boxWidth, font, fontScale, tracking, true)
            for (j in lines.indices) {
                val line = lines[j]
                lineIndex++

                canvas.save()

                if (offsetCanvas(canvas, documentData, lineIndex, line.width)) {
                    drawGlyphTextLine(line.text, documentData, font, canvas, parentScale, fontScale, tracking)
                }

                canvas.restore()
            }
        }
    }

    private fun drawGlyphTextLine(
        text: String, documentData: DocumentData,
        font: Font, canvas: Canvas, parentScale: Float, fontScale: Float, tracking: Float
    ) {
        for (i in 0 until text.length) {
            val c = text[i]
            val characterHash = hashFor(c, font.family, font.style)
            val character = composition.characters!![characterHash]
                ?: // Something is wrong. Potentially, they didn't export the text as a glyph.
                continue
            drawCharacterAsGlyph(character, fontScale, documentData, canvas)
            val tx = character.width.toFloat() * fontScale * dpScale() + tracking
            canvas.translate(tx, 0f)
        }
    }

    private fun drawTextWithFont(documentData: DocumentData, font: Font, canvas: Canvas) {
        val typeface = getTypeface(font) ?: return
        var text = documentData.text
        val textDelegate = lottieDrawable.textDelegate
        if (textDelegate != null) {
            text = textDelegate.getTextInternal(name, text!!)
        }
        fillPaint.setTypeface(typeface)
        val textSize = if (textSizeCallbackAnimation != null) {
            textSizeCallbackAnimation!!.value
        } else {
            documentData.size
        }
        fillPaint.textSize = textSize * dpScale()
        strokePaint.setTypeface(fillPaint.typeface)
        strokePaint.textSize = fillPaint.textSize

        // Calculate tracking
        var tracking = documentData.tracking / 10f
        if (trackingCallbackAnimation != null) {
            tracking += trackingCallbackAnimation!!.value
        } else if (trackingAnimation != null) {
            tracking += trackingAnimation!!.value
        }
        tracking = tracking * dpScale() * textSize / 100.0f

        // Split full text in multiple lines
        val textLines = getTextLines(text)
        val textLineCount = textLines.size
        var lineIndex = -1
        for (i in 0 until textLineCount) {
            val textLine = textLines[i]
            val boxWidth = if (documentData.boxSize == null) 0f else documentData.boxSize!!.x
            val lines = splitGlyphTextIntoLines(textLine, boxWidth, font, 0f, tracking, false)
            for (j in lines.indices) {
                val line = lines[j]
                lineIndex++

                canvas.save()

                if (offsetCanvas(canvas, documentData, lineIndex, line.width)) {
                    drawFontTextLine(line.text, documentData, canvas, tracking)
                }

                canvas.restore()
            }
        }
    }

    private fun offsetCanvas(canvas: Canvas, documentData: DocumentData, lineIndex: Int, lineWidth: Float): Boolean {
        val position = documentData.boxPosition
        val size = documentData.boxSize
        val dpScale = dpScale()
        val lineStartY = if (position == null) 0f else documentData.lineHeight * dpScale + position.y
        val lineOffset = (lineIndex * documentData.lineHeight * dpScale) + lineStartY
        if (lottieDrawable.clipTextToBoundingBox && size != null && position != null && lineOffset >= position.y + size.y + documentData.size) {
            return false
        }
        val lineStart = position?.x ?: 0f
        val boxWidth = size?.x ?: 0f
        when (documentData.justification) {
            Justification.LEFT_ALIGN -> canvas.translate(lineStart, lineOffset)
            Justification.RIGHT_ALIGN -> canvas.translate(lineStart + boxWidth - lineWidth, lineOffset)
            Justification.CENTER -> canvas.translate(lineStart + boxWidth / 2f - lineWidth / 2f, lineOffset)
            else -> {}
        }
        return true
    }

    private fun getTypeface(font: Font): Typeface? {
        if (typefaceCallbackAnimation != null) {
            val callbackTypeface = typefaceCallbackAnimation!!.value
            if (callbackTypeface != null) {
                return callbackTypeface
            }
        }
        val drawableTypeface = lottieDrawable.getTypeface(font)
        if (drawableTypeface != null) {
            return drawableTypeface
        }
        return font.typeface
    }

    private fun getTextLines(text: String?): List<String> {
        // Split full text by carriage return character
        val formattedText = text!!.replace("\r\n".toRegex(), "\r")
            .replace("\u0003".toRegex(), "\r")
            .replace("\n".toRegex(), "\r")
        val textLinesArray = formattedText.split("\r".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return Arrays.asList(*textLinesArray)
    }

    private fun drawFontTextLine(text: String, documentData: DocumentData, canvas: Canvas, tracking: Float) {
        var i = 0
        while (i < text.length) {
            val charString = codePointToString(text, i)
            i += charString!!.length
            drawCharacterFromFont(charString, documentData, canvas)
            val charWidth = fillPaint.measureText(charString)
            val tx = charWidth + tracking
            canvas.translate(tx, 0f)
        }
    }

    private fun splitGlyphTextIntoLines(
        textLine: String, boxWidth: Float, font: Font, fontScale: Float, tracking: Float,
        usingGlyphs: Boolean
    ): List<TextSubLine> {
        var lineCount = 0

        var currentLineWidth = 0f
        var currentLineStartIndex = 0

        var currentWordStartIndex = 0
        var currentWordWidth = 0f
        var nextCharacterStartsWord = false

        // The measured size of a space.
        var spaceWidth = 0f

        for (i in 0 until textLine.length) {
            val c = textLine[i]
            var currentCharWidth: Float
            if (usingGlyphs) {
                val characterHash = hashFor(c, font.family, font.style)
                val character = composition.characters!![characterHash] ?: continue
                currentCharWidth = character.width.toFloat() * fontScale * dpScale() + tracking
            } else {
                currentCharWidth = fillPaint.measureText(textLine.substring(i, i + 1)) + tracking
            }

            if (c == ' ') {
                spaceWidth = currentCharWidth
                nextCharacterStartsWord = true
            } else if (nextCharacterStartsWord) {
                nextCharacterStartsWord = false
                currentWordStartIndex = i
                currentWordWidth = currentCharWidth
            } else {
                currentWordWidth += currentCharWidth
            }
            currentLineWidth += currentCharWidth

            if (boxWidth > 0f && currentLineWidth >= boxWidth) {
                if (c == ' ') {
                    // Spaces at the end of a line don't do anything. Ignore it.
                    // The next non-space character will hit the conditions below.
                    continue
                }
                val subLine = ensureEnoughSubLines(++lineCount)
                if (currentWordStartIndex == currentLineStartIndex) {
                    // Only word on line is wider than box, start wrapping mid-word.
                    val substr = textLine.substring(currentLineStartIndex, i)
                    val trimmed = substr.trim { it <= ' ' }
                    val trimmedSpace = (trimmed.length - substr.length) * spaceWidth
                    subLine.set(trimmed, currentLineWidth - currentCharWidth - trimmedSpace)
                    currentLineStartIndex = i
                    currentLineWidth = currentCharWidth
                    currentWordStartIndex = currentLineStartIndex
                    currentWordWidth = currentCharWidth
                } else {
                    val substr = textLine.substring(currentLineStartIndex, currentWordStartIndex - 1)
                    val trimmed = substr.trim { it <= ' ' }
                    val trimmedSpace = (substr.length - trimmed.length) * spaceWidth
                    subLine.set(trimmed, currentLineWidth - currentWordWidth - trimmedSpace - spaceWidth)
                    currentLineStartIndex = currentWordStartIndex
                    currentLineWidth = currentWordWidth
                }
            }
        }
        if (currentLineWidth > 0f) {
            val line = ensureEnoughSubLines(++lineCount)
            line.set(textLine.substring(currentLineStartIndex), currentLineWidth)
        }
        return textSubLines.subList(0, lineCount)
    }

    /**
     * Elements are reused and not deleted to save allocations.
     */
    private fun ensureEnoughSubLines(numLines: Int): TextSubLine {
        for (i in textSubLines.size until numLines) {
            textSubLines.add(TextSubLine())
        }
        return textSubLines[numLines - 1]
    }

    private fun drawCharacterAsGlyph(
        character: FontCharacter,
        fontScale: Float,
        documentData: DocumentData,
        canvas: Canvas
    ) {
        val contentGroups = getContentsForCharacter(character)
        for (j in contentGroups.indices) {
            val path = contentGroups[j].getPath()
            path.computeBounds(rectF, false)
            matrix.reset()
            matrix.preTranslate(0f, -documentData.baselineShift * dpScale())
            matrix.preScale(fontScale, fontScale)
            path.transform(matrix)
            if (documentData.strokeOverFill) {
                drawGlyph(path, fillPaint, canvas)
                drawGlyph(path, strokePaint, canvas)
            } else {
                drawGlyph(path, strokePaint, canvas)
                drawGlyph(path, fillPaint, canvas)
            }
        }
    }

    private fun drawGlyph(path: Path, paint: Paint, canvas: Canvas) {
        if (paint.color == Color.TRANSPARENT) {
            return
        }
        if (paint.style == Paint.Style.STROKE && paint.strokeWidth == 0f) {
            return
        }
        canvas.drawPath(path, paint)
    }

    private fun drawCharacterFromFont(character: String?, documentData: DocumentData, canvas: Canvas) {
        if (documentData.strokeOverFill) {
            drawCharacter(character, fillPaint, canvas)
            drawCharacter(character, strokePaint, canvas)
        } else {
            drawCharacter(character, strokePaint, canvas)
            drawCharacter(character, fillPaint, canvas)
        }
    }

    private fun drawCharacter(character: String?, paint: Paint, canvas: Canvas) {
        if (paint.color == Color.TRANSPARENT) {
            return
        }
        if (paint.style == Paint.Style.STROKE && paint.strokeWidth == 0f) {
            return
        }
        canvas.drawText(character!!, 0, character.length, 0f, 0f, paint)
    }

    private fun getContentsForCharacter(character: FontCharacter): List<ContentGroup> {
        if (contentsForCharacter.containsKey(character)) {
            return contentsForCharacter[character]!!
        }
        val shapes = character.shapes
        val size = shapes.size
        val contents: MutableList<ContentGroup> = ArrayList(size)
        for (i in 0 until size) {
            val sg = shapes[i]
            contents.add(ContentGroup(lottieDrawable, this, sg, composition))
        }
        contentsForCharacter[character] = contents
        return contents
    }

    private fun codePointToString(text: String, startIndex: Int): String? {
        val firstCodePoint = text.codePointAt(startIndex)
        val firstCodePointLength = Character.charCount(firstCodePoint)
        var key = firstCodePoint
        var index = startIndex + firstCodePointLength
        while (index < text.length) {
            val nextCodePoint = text.codePointAt(index)
            if (!isModifier(nextCodePoint)) {
                break
            }
            val nextCodePointLength = Character.charCount(nextCodePoint)
            index += nextCodePointLength
            key = key * 31 + nextCodePoint
        }

        if (codePointCache.containsKey(key.toLong())) {
            return codePointCache[key.toLong()]
        }

        stringBuilder.setLength(0)
        var i = startIndex
        while (i < index) {
            val codePoint = text.codePointAt(i)
            stringBuilder.appendCodePoint(codePoint)
            i += Character.charCount(codePoint)
        }
        val str = stringBuilder.toString()
        codePointCache.put(key.toLong(), str)
        return str
    }

    private fun isModifier(codePoint: Int): Boolean {
        return Character.getType(codePoint) == Character.FORMAT.toInt() || Character.getType(codePoint) == Character.MODIFIER_SYMBOL.toInt() || Character.getType(
            codePoint
        ) == Character.NON_SPACING_MARK.toInt() || Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt() || Character.getType(
            codePoint
        ) == Character.DIRECTIONALITY_NONSPACING_MARK.toInt() || Character.getType(codePoint) == Character.SURROGATE.toInt()
    }

    override fun <T> addValueCallback(property: T, callback: LottieValueCallback<T>?) {
        super.addValueCallback(property, callback)
        if (property == LottieProperty.COLOR) {
            if (colorCallbackAnimation != null) {
                removeAnimation(colorCallbackAnimation!!)
            }

            if (callback == null) {
                colorCallbackAnimation = null
            } else {
                colorCallbackAnimation = ValueCallbackKeyframeAnimation<Int, Int>(callback as LottieValueCallback<Int>?).also {
                    it.addUpdateListener(this)
                    addAnimation(it)
                }
            }
        } else if (property == LottieProperty.STROKE_COLOR) {
            if (strokeColorCallbackAnimation != null) {
                removeAnimation(strokeColorCallbackAnimation!!)
            }

            if (callback == null) {
                strokeColorCallbackAnimation = null
            } else {
                strokeColorCallbackAnimation = ValueCallbackKeyframeAnimation<Int, Int>(callback as LottieValueCallback<Int>?).also {
                    it.addUpdateListener(this)
                    addAnimation(it)
                }
            }
        } else if (property == LottieProperty.STROKE_WIDTH) {
            if (strokeWidthCallbackAnimation != null) {
                removeAnimation(strokeWidthCallbackAnimation!!)
            }

            if (callback == null) {
                strokeWidthCallbackAnimation = null
            } else {
                strokeWidthCallbackAnimation = ValueCallbackKeyframeAnimation<Float, Float>(callback as LottieValueCallback<Float>?).also {
                    it.addUpdateListener(this)
                    addAnimation(it)
                }
            }
        } else if (property == LottieProperty.TEXT_TRACKING) {
            if (trackingCallbackAnimation != null) {
                removeAnimation(trackingCallbackAnimation!!)
            }

            if (callback == null) {
                trackingCallbackAnimation = null
            } else {
                trackingCallbackAnimation = ValueCallbackKeyframeAnimation<Float, Float>(callback as LottieValueCallback<Float>?).also {
                    it.addUpdateListener(this)
                    addAnimation(it)
                }
            }
        } else if (property == LottieProperty.TEXT_SIZE) {
            if (textSizeCallbackAnimation != null) {
                removeAnimation(textSizeCallbackAnimation!!)
            }

            if (callback == null) {
                textSizeCallbackAnimation = null
            } else {
                textSizeCallbackAnimation = ValueCallbackKeyframeAnimation<Float, Float>(callback as LottieValueCallback<Float>?).also {
                    it.addUpdateListener(this)
                    addAnimation(it)
                }
            }
        } else if (property === LottieProperty.TYPEFACE) {
            if (typefaceCallbackAnimation != null) {
                removeAnimation(typefaceCallbackAnimation!!)
            }

            if (callback == null) {
                typefaceCallbackAnimation = null
            } else {
                typefaceCallbackAnimation = ValueCallbackKeyframeAnimation<Typeface, Typeface>(callback as LottieValueCallback<Typeface>?).also {
                    it.addUpdateListener(this)
                    addAnimation(it)
                }
            }
        } else if (property === LottieProperty.TEXT) {
            textAnimation.setStringValueCallback((callback as LottieValueCallback<String>?)!!)
        }
    }

    private class TextSubLine {
        var text: String = ""
        var width: Float = 0f

        fun set(text: String, width: Float) {
            this.text = text
            this.width = width
        }
    }
}
