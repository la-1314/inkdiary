package com.inkdiary.stroke

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log

/**
 * Provides stroke paths for Latin/English text using Zhang-Suen thinning.
 *
 * Pipeline (ported from riddle's approach):
 * 1. Rasterize text with a handwriting font (Dancing Script) to a binary bitmap
 * 2. Apply Zhang-Suen skeletonization to get 1px-wide strokes
 * 3. Trace skeleton into ordered polylines (left-to-right)
 * 4. Return as StrokePath objects for animation
 */
class LatinStrokeProvider(
    private val assets: AssetManager,
    private val fontPath: String
) : StrokeProvider {

    companion object {
        private const val TAG = "LatinStrokeProvider"
        private const val RASTER_SIZE = 256
        private const val FONT_SIZE = 180f
    }

    private var font: Typeface? = null

    private fun ensureFont() {
        if (font != null) return
        font = try {
            Typeface.createFromAsset(assets, fontPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load font $fontPath, falling back to default", e)
            Typeface.DEFAULT
        }
    }

    override fun supports(char: Char): Boolean {
        // Latin, digits, punctuation, spaces
        val code = char.code
        return code < 0x4E00 && char.isDefined() && !char.isISOControl()
    }

    override fun getStrokes(char: Char): List<StrokePath> {
        if (char.isWhitespace()) return emptyList()
        ensureFont()

        val size = RASTER_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = FONT_SIZE
            typeface = font
            isAntiAlias = false // We want crisp edges for thinning
            textAlign = Paint.Align.LEFT
        }

        // Center the character
        val fm = paint.fontMetrics
        val textWidth = paint.measureText(char.toString())
        val x = (size - textWidth) / 2f
        val y = (size - (fm.descent + fm.ascent)) / 2f
        canvas.drawText(char.toString(), x, y, paint)

        // Extract binary mask
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        bitmap.recycle()

        val mask = BooleanArray(size * size) { i ->
            // Dark pixel = ink
            (pixels[i] and 0xFF) < 128
        }

        // Thin
        ZhangSuen.thin(size, size, mask)

        // Trace
        val rawStrokes = ZhangSuen.trace(size, size, mask)

        // Normalize to 0..1
        return rawStrokes.map { stroke ->
            StrokePath(
                points = stroke.map { (px, py) ->
                    (px / size) to (py / size)
                },
                width = 4f
            )
        }
    }
}
