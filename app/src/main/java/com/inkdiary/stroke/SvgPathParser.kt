package com.inkdiary.stroke

import android.util.Log

/**
 * A minimal SVG path parser supporting M, L, Q, C, Z commands.
 * Parses Make Me a Hanzi SVG path strings into a list of polylines.
 *
 * MMAH coordinate system is 1024x1024 with Y flipped (top=900, bottom=-124).
 * We handle the flip in the provider, not here.
 */
object SvgPathParser {

    private const val TAG = "SvgPathParser"

    data class Point(val x: Float, val y: Float)

    /**
     * Parse an SVG path string into a list of sub-paths,
     * each being a list of points (already flattened from curves).
     */
    fun parse(pathData: String, flattenSteps: Int = 16): List<List<Point>> {
        val subPaths = mutableListOf<MutableList<Point>>()
        var current = mutableListOf<Point>()

        val tokens = tokenize(pathData)
        var i = 0
        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        var lastCmd = 'M'

        while (i < tokens.size) {
            when (tokens[i]) {
                'M', 'm' -> {
                    val relative = tokens[i] == 'm'
                    if (current.isNotEmpty()) subPaths.add(current)
                    current = mutableListOf()
                    i++
                    curX = num(tokens, i, relative, curX).also { i += 2 }
                    curY = num(tokens, i, false, curY).also { i += 2 }
                    if (relative) { curY += startY; curX += startX }
                    // Actually for 'm', both x and y are relative
                    // Re-read properly:
                }
                'L', 'l' -> {
                    i++
                    curX = num(tokens, i, tokens[i - 1] == 'l', curX).also { i += 2 }
                    curY = num(tokens, i, false, curY).also { i += 2 }
                    current.add(Point(curX, curY))
                }
                'Q', 'q' -> {
                    val relative = tokens[i] == 'q'
                    i++
                    val cx = num(tokens, i, relative, curX).also { i += 2 }
                    val cy = num(tokens, i, false, curY).also { i += 2 }
                    val ex = num(tokens, i, relative, curX).also { i += 2 }
                    val ey = num(tokens, i, false, curY).also { i += 2 }
                    flattenQuad(curX, curY, cx, cy, ex, ey, flattenSteps, current)
                    curX = ex; curY = ey
                }
                'C', 'c' -> {
                    val relative = tokens[i] == 'c'
                    i++
                    val x1 = num(tokens, i, relative, curX).also { i += 2 }
                    val y1 = num(tokens, i, false, curY).also { i += 2 }
                    val x2 = num(tokens, i, relative, curX).also { i += 2 }
                    val y2 = num(tokens, i, false, curY).also { i += 2 }
                    val x3 = num(tokens, i, relative, curX).also { i += 2 }
                    val y3 = num(tokens, i, false, curY).also { i += 2 }
                    flattenCubic(curX, curY, x1, y1, x2, y2, x3, y3, flattenSteps, current)
                    curX = x3; curY = y3
                }
                'Z', 'z' -> {
                    if (current.isNotEmpty()) {
                        subPaths.add(current)
                        current = mutableListOf()
                    }
                    i++
                    curX = startX; curY = startY
                }
                else -> {
                    // Implicit continuation of last command (not fully supported)
                    i++
                }
            }
            if (i < tokens.size && tokens[i] == 'M') startX = curX; startY = curY
            lastCmd = tokens[i - 1]
        }
        if (current.isNotEmpty()) subPaths.add(current)
        return subPaths
    }

    /**
     * Tokenize SVG path into a list of command letters and numbers.
     * Numbers are stored as Float, commands as Char.
     */
    private fun tokenize(pathData: String): List<Any> {
        val result = mutableListOf<Any>()
        val numStr = StringBuilder()
        var i = 0
        val s = pathData.trim()

        fun flushNumber() {
            if (numStr.isNotEmpty()) {
                result.add(numStr.toString().toFloat())
                numStr.clear()
            }
        }

        while (i < s.length) {
            val c = s[i]
            when {
                c.isLetter() -> {
                    flushNumber()
                    result.add(c)
                }
                c == '-' || c == '+' -> {
                    // Could be start of a number or a sign after 'e'
                    if (numStr.isNotEmpty() && !numStr.endsWith('e') && !numStr.endsWith('E')) {
                        flushNumber()
                    }
                    numStr.append(c)
                }
                c == '.' -> {
                    if (numStr.isNotEmpty() && numStr.contains('.')) {
                        flushNumber()
                    }
                    numStr.append(c)
                }
                c.isDigit() -> {
                    numStr.append(c)
                }
                c == 'e' || c == 'E' -> {
                    numStr.append(c)
                }
                c == ',' || c.isWhitespace() -> {
                    flushNumber()
                }
                else -> {
                    flushNumber()
                }
            }
            i++
        }
        flushNumber()
        return result
    }

    private fun num(tokens: List<Any>, i: Int, relative: Boolean, prev: Float): Float {
        if (i + 1 >= tokens.size) return prev
        val n = (tokens[i] as? Float) ?: return prev
        return if (relative) prev + n else n
    }

    private fun flattenQuad(
        x0: Float, y0: Float, cx: Float, cy: Float,
        ex: Float, ey: Float, steps: Int, out: MutableList<Point>
    ) {
        if (out.isEmpty() || out.last().x != x0 || out.last().y != y0) {
            out.add(Point(x0, y0))
        }
        for (s in 1..steps) {
            val t = s.toFloat() / steps
            val mt = 1 - t
            val x = mt * mt * x0 + 2 * mt * t * cx + t * t * ex
            val y = mt * mt * y0 + 2 * mt * t * cy + t * t * ey
            out.add(Point(x, y))
        }
    }

    private fun flattenCubic(
        x0: Float, y0: Float, x1: Float, y1: Float,
        x2: Float, y2: Float, x3: Float, y3: Float,
        steps: Int, out: MutableList<Point>
    ) {
        if (out.isEmpty() || out.last().x != x0 || out.last().y != y0) {
            out.add(Point(x0, y0))
        }
        for (s in 1..steps) {
            val t = s.toFloat() / steps
            val mt = 1 - t
            val x = mt*mt*mt * x0 + 3*mt*mt*t * x1 + 3*mt*t*t * x2 + t*t*t * x3
            val y = mt*mt*mt * y0 + 3*mt*mt*t * y1 + 3*mt*t*t * y2 + t*t*t * y3
            out.add(Point(x, y))
        }
    }
}
