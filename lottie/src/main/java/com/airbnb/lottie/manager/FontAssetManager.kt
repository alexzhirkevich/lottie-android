package com.airbnb.lottie.manager

import android.content.res.AssetManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import com.airbnb.lottie.FontAssetDelegate
import com.airbnb.lottie.model.Font
import com.airbnb.lottie.model.MutablePair
import com.airbnb.lottie.utils.Logger.warning

class FontAssetManager(callback: Drawable.Callback, private var delegate: FontAssetDelegate?) {
    private val tempPair = MutablePair<String>()

    /**
     * Pair is (fontName, fontStyle)
     */
    private val fontMap: MutableMap<MutablePair<String>, Typeface?> = HashMap()

    /**
     * Map of font families to their fonts. Necessary to create a font with a different style
     */
    private val fontFamilies: MutableMap<String, Typeface?> = HashMap()
    private var assetManager: AssetManager?
    private var defaultFontFileExtension = ".ttf"

    init {
        if (callback !is View) {
            warning("LottieDrawable must be inside of a view for images to work.")
            assetManager = null
        } else {

            assetManager = (callback as View).context.assets
        }
    }

    fun setDelegate(assetDelegate: FontAssetDelegate?) {
        this.delegate = assetDelegate
    }

    /**
     * Sets the default file extension (include the `.`).
     *
     *
     * e.g. `.ttf` `.otf`
     *
     *
     * Defaults to `.ttf`
     */
    @Suppress("unused")
    fun setDefaultFontFileExtension(defaultFontFileExtension: String) {
        this.defaultFontFileExtension = defaultFontFileExtension
    }

    fun getTypeface(font: Font): Typeface? {
        tempPair.set(font.family, font.style)
        var typeface = fontMap[tempPair]
        if (typeface != null) {
            return typeface
        }
        val typefaceWithDefaultStyle = getFontFamily(font)
        typeface = typefaceForStyle(typefaceWithDefaultStyle, font.style)
        fontMap[tempPair] = typeface
        return typeface
    }

    private fun getFontFamily(font: Font): Typeface? {
        val fontFamily = font.family
        val defaultTypeface = fontFamilies[fontFamily]
        if (defaultTypeface != null) {
            return defaultTypeface
        }

        var typeface: Typeface? = null
        val fontStyle = font.style
        val fontName = font.name
        if (delegate != null) {
            typeface = delegate!!.fetchFont(fontFamily, fontStyle, fontName)
            if (typeface == null) {
                typeface = delegate!!.fetchFont(fontFamily)
            }
        }

        if (delegate != null && typeface == null) {
            var path = delegate!!.getFontPath(fontFamily, fontStyle, fontName)
            if (path == null) {
                path = delegate!!.getFontPath(fontFamily)
            }
            if (path != null) {
                typeface = Typeface.createFromAsset(assetManager, path)
            }
        }

        if (font.typeface != null) {
            return font.typeface
        }

        if (typeface == null) {
            val path = "fonts/$fontFamily$defaultFontFileExtension"
            typeface = Typeface.createFromAsset(assetManager, path)
        }

        fontFamilies[fontFamily] = typeface
        return typeface
    }

    private fun typefaceForStyle(typeface: Typeface?, style: String): Typeface? {
        var styleInt = Typeface.NORMAL
        val containsItalic = style.contains("Italic")
        val containsBold = style.contains("Bold")
        if (containsItalic && containsBold) {
            styleInt = Typeface.BOLD_ITALIC
        } else if (containsItalic) {
            styleInt = Typeface.ITALIC
        } else if (containsBold) {
            styleInt = Typeface.BOLD
        }

        if (typeface!!.style == styleInt) {
            return typeface
        }

        return Typeface.create(typeface, styleInt)
    }
}
