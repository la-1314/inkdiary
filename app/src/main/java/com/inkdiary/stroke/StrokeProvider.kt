package com.inkdiary.stroke

/**
 * A single stroke path for animation: a sequence of (x, y) points.
 */
data class StrokePath(
    val points: List<Pair<Float, Float>>,
    val width: Float = 4f
)

/**
 * Interface for providing stroke paths for a character.
 * Chinese and Latin text use different providers.
 */
interface StrokeProvider {
    /**
     * Get the ordered stroke paths for a single character.
     * Returns empty list if the character is not supported.
     */
    fun getStrokes(char: Char): List<StrokePath>

    /** Whether this provider handles the given character. */
    fun supports(char: Char): Boolean
}

/**
 * A laid-out glyph ready for animation: its stroke paths plus
 * the position (x, y) where it should be drawn.
 */
data class GlyphLayout(
    val strokes: List<StrokePath>,
    val x: Float,
    val y: Float,
    val size: Float
)
