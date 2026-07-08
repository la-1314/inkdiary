package com.inkdiary.stroke

/**
 * Zhang-Suen skeletonization algorithm.
 * Reduces a binary bitmap to 1-pixel-wide skeleton lines.
 *
 * Used for Latin text: rasterize a handwriting font → thin → trace → animate.
 * Ported from riddle's Rust implementation (script.rs).
 */
object ZhangSuen {

    /**
     * Thin a binary mask (true = ink) to its skeleton.
     * Modifies the mask in place.
     */
    fun thin(width: Int, height: Int, mask: BooleanArray) {
        fun idx(x: Int, y: Int) = y * width + x

        var changed = true
        while (changed) {
            changed = false
            for (phase in 0..1) {
                val toClear = mutableListOf<Int>()
                for (y in 1 until height - 1) {
                    for (x in 1 until width - 1) {
                        if (!mask[idx(x, y)]) continue

                        val p = booleanArrayOf(
                            mask[idx(x, y - 1)],     // p2 N
                            mask[idx(x + 1, y - 1)], // p3 NE
                            mask[idx(x + 1, y)],     // p4 E
                            mask[idx(x + 1, y + 1)], // p5 SE
                            mask[idx(x, y + 1)],     // p6 S
                            mask[idx(x - 1, y + 1)], // p7 SW
                            mask[idx(x - 1, y)],     // p8 W
                            mask[idx(x - 1, y - 1)]  // p9 NW
                        )

                        val b = p.count { it }
                        if (b !in 2..6) continue

                        var a = 0
                        for (i in 0 until 8) {
                            if (!p[i] && p[(i + 1) % 8]) a++
                        }
                        if (a != 1) continue

                        val (c1, c2) = if (phase == 0) {
                            Pair(!(p[0] && p[2] && p[4]), !(p[2] && p[4] && p[6]))
                        } else {
                            Pair(!(p[0] && p[2] && p[6]), !(p[0] && p[4] && p[6]))
                        }

                        if (c1 && c2) toClear.add(idx(x, y))
                    }
                }
                if (toClear.isNotEmpty()) {
                    changed = true
                    for (i in toClear) mask[i] = false
                }
            }
        }
    }

    /**
     * Trace the skeleton into ordered polyline strokes (left-to-right).
     * Ported from riddle's trace() function.
     */
    fun trace(width: Int, height: Int, mask: BooleanArray): List<List<Pair<Float, Float>>> {
        fun at(x: Int, y: Int): Boolean =
            x >= 0 && y >= 0 && x < width && y < height && mask[y * width + x]

        fun neighbors(x: Int, y: Int): List<Pair<Int, Int>> {
            val out = mutableListOf<Pair<Int, Int>>()
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    if (at(x + dx, y + dy)) out.add(x + dx to y + dy)
                }
            }
            return out
        }

        val visited = BooleanArray(width * height)

        // Endpoints first (degree 1), then any remaining pixels (loops)
        val starts = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (at(x, y) && neighbors(x, y).size == 1) {
                    starts.add(x to y)
                }
            }
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (at(x, y)) starts.add(x to y)
            }
        }

        val strokes = mutableListOf<MutableList<Pair<Float, Float>>>()
        for ((sx, sy) in starts) {
            if (visited[sy * width + sx]) continue
            val path = mutableListOf<Pair<Float, Float>>()
            path.add(sx.toFloat() to sy.toFloat())
            visited[sy * width + sx] = true
            var cx = sx
            var cy = sy
            while (true) {
                val next = neighbors(cx, cy).find { (nx, ny) ->
                    !visited[ny * width + nx]
                }
                if (next == null) break
                val (nx, ny) = next
                visited[ny * width + nx] = true
                path.add(nx.toFloat() to ny.toFloat())
                cx = nx; cy = ny
            }
            if (path.size >= 3) strokes.add(path)
        }

        // Sort left-to-right
        strokes.sortBy { stroke -> stroke.minOf { (x, _) -> x } }
        return strokes
    }
}
