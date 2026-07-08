package com.inkdiary.stroke

import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.inkdiary.ink.InkCanvasView
import java.io.File

/**
 * The unified stroke animation engine.
 *
 * Takes a reply string, breaks it into characters, gets stroke paths from
 * the appropriate provider (Chinese or Latin), lays them out on the canvas,
 * and animates them stroke-by-stroke.
 *
 * This is the core feature: text appears as if written by hand,
 * following correct stroke order for both Chinese and Latin characters.
 */
class StrokeAnimator(
    private val canvasView: InkCanvasView,
    private val assets: AssetManager,
    private val fontPath: String
) {
    companion object {
        private const val TAG = "StrokeAnimator"
        private const val GLYPH_SIZE = 80f   // px per glyph
        private const val LINE_HEIGHT = 100f  // px per line
        private const val MARGIN = 40f
        private const val MAX_LINE_WIDTH = 1000f
        private const val STROKE_DURATION_MS = 300L  // per stroke
        private const val STROKE_GAP_MS = 50L        // between strokes
        private const val CHAR_GAP_MS = 100L         // between characters
        private const val FRAME_MS = 16L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var cancelled = false

    private val chineseProvider: ChineseStrokeProvider by lazy {
        ChineseStrokeProvider(assets, File(canvasView.context.filesDir.absolutePath))
    }
    private val latinProvider: LatinStrokeProvider by lazy {
        LatinStrokeProvider(assets, fontPath)
    }

    /**
     * Animate a reply string stroke by stroke.
     * Calls onComplete when the full animation finishes.
     */
    fun animateReply(text: String, onComplete: () -> Unit) {
        cancelled = false

        // Get layout for all characters
        val glyphs = layoutText(text)
        if (glyphs.isEmpty()) {
            onComplete()
            return
        }

        // Collect all stroke segments with absolute positions
        val allStrokes = mutableListOf<List<Pair<Float, Float>>>()
        for (glyph in glyphs) {
            for (stroke in glyph.strokes) {
                val positioned = stroke.points.map { (nx, ny) ->
                    (glyph.x + nx * glyph.size) to (glyph.y + ny * glyph.size)
                }
                if (positioned.size >= 2) {
                    allStrokes.add(positioned)
                }
            }
        }

        if (allStrokes.isEmpty()) {
            onComplete()
            return
        }

        // Set up canvas with all stroke paths
        canvasView.setAnimStrokes(allStrokes)

        // Animate each stroke
        animateStrokes(allStrokes, 0, onComplete)
    }

    private fun animateStrokes(
        strokes: List<List<Pair<Float, Float>>>,
        index: Int,
        onComplete: () -> Unit
    ) {
        if (cancelled || index >= strokes.size) {
            if (!cancelled) onComplete()
            return
        }

        val stroke = strokes[index]
        val totalPoints = stroke.size
        var currentPoint = 0
        val pointsPerFrame = (totalPoints.toFloat() / (STROKE_DURATION_MS.toFloat() / FRAME_MS))
            .coerceAtLeast(1f)

        val frameRunnable = object : Runnable {
            override fun run() {
                if (cancelled) return
                currentPoint += pointsPerFrame.toInt().coerceAtLeast(1)
                val progress = (currentPoint.toFloat() / totalPoints).coerceIn(0f, 1f)
                canvasView.updateAnimStrokeProgress(index, progress)

                if (currentPoint >= totalPoints) {
                    // Move to next stroke after a gap
                    handler.postDelayed({
                        animateStrokes(strokes, index + 1, onComplete)
                    }, STROKE_GAP_MS)
                } else {
                    handler.postDelayed(this, FRAME_MS)
                }
            }
        }
        handler.post(frameRunnable)
    }

    /**
     * Lay out text into glyphs with positions.
     * Simple word-wrap: breaks lines at MAX_LINE_WIDTH.
     */
    private fun layoutText(text: String): List<GlyphLayout> {
        val result = mutableListOf<GlyphLayout>()
        var x = MARGIN
        var y = LINE_HEIGHT

        for (char in text) {
            if (char == '\n') {
                x = MARGIN
                y += LINE_HEIGHT
                continue
            }

            if (char.isWhitespace()) {
                x += GLYPH_SIZE * 0.3f
                if (x > MAX_LINE_WIDTH) {
                    x = MARGIN
                    y += LINE_HEIGHT
                }
                continue
            }

            val provider = getProvider(char)
            val strokes = provider.getStrokes(char)

            if (strokes.isNotEmpty()) {
                result.add(GlyphLayout(strokes, x, y, GLYPH_SIZE))
            }

            // Advance x
            x += if (isCJK(char)) GLYPH_SIZE else GLYPH_SIZE * 0.6f

            // Word wrap
            if (x > MAX_LINE_WIDTH) {
                x = MARGIN
                y += LINE_HEIGHT
            }
        }

        return result
    }

    private fun getProvider(char: Char): StrokeProvider {
        return if (chineseProvider.supports(char)) {
            chineseProvider
        } else {
            latinProvider
        }
    }

    private fun isCJK(char: Char): Boolean {
        val code = char.code
        return code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF
    }

    fun cancel() {
        cancelled = true
        handler.removeCallbacksAndMessages(null)
        canvasView.clearAnimStrokes()
    }
}
