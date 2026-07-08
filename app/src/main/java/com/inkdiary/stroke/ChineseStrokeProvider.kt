package com.inkdiary.stroke

import android.content.res.AssetManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStream

/**
 * Provides stroke paths for Chinese characters using Make Me a Hanzi data.
 *
 * Data format (from graphics.txt, each line is JSON):
 * {"character":"我","strokes":["M ... Z", ...], "medians":[[[x,y],...], ...]}
 *
 * - strokes: SVG path strings ordered by correct stroke order
 * - medians: center-line points for each stroke (drives animation)
 *
 * Coordinate system: 1024x1024, Y flipped (top=900, bottom=-124)
 *
 * Data source: https://github.com/skishore/makemeahanzi
 * License: Arphic Public License
 */
class ChineseStrokeProvider(
    private val assets: AssetManager,
    private val filesDir: File
) : StrokeProvider {

    companion object {
        private const val TAG = "ChineseStrokeProvider"
        private const val SUBSET_FILE = "hanzi_subset.json"
        private const val FULL_FILE = "hanzi_full.json"
        private const val MMAH_SIZE = 1024f
        private const val MMAH_Y_TOP = 900f
        private const val MMAH_Y_BOTTOM = -124f
        private val MMAH_HEIGHT = MMAH_Y_TOP - MMAH_Y_BOTTOM // 1024
    }

    private val gson = Gson()

    // character -> list of (strokes, medians)
    private data class CharData(
        val strokes: List<String>,
        val medians: List<List<List<Float>>>
    )

    private val cache = HashMap<Char, CharData>()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true

        // Try full data first (downloaded to filesDir)
        val fullFile = File(filesDir, FULL_FILE)
        val stream: InputStream = if (fullFile.exists()) {
            fullFile.inputStream()
        } else {
            try {
                assets.open(SUBSET_FILE)
            } catch (_: Exception) {
                Log.w(TAG, "No hanzi data file found")
                return
            }
        }

        try {
            val text = stream.bufferedReader().readText()
            stream.close()
            val type = object : TypeToken<Map<String, CharDataRaw>>() {}.type
            val raw: Map<String, CharDataRaw> = gson.fromJson(text, type) ?: emptyMap()
            for ((char, data) in raw) {
                if (char.isNotEmpty()) {
                    cache[char[0]] = CharData(data.strokes, data.medians)
                }
            }
            Log.i(TAG, "Loaded ${cache.size} Chinese characters")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load hanzi data", e)
        }
    }

    // Gson deserialization helper
    data class CharDataRaw(
        val strokes: List<String> = emptyList(),
        val medians: List<List<List<Float>>> = emptyList()
    )

    override fun supports(char: Char): Boolean {
        ensureLoaded()
        // CJK Unified Ideographs: U+4E00 – U+9FFF
        // Also CJK Extension A and compatibility
        val code = char.code
        return (code in 0x4E00..0x9FFF) || (code in 0x3400..0x4DBF)
    }

    override fun getStrokes(char: Char): List<StrokePath> {
        ensureLoaded()
        val data = cache[char] ?: return emptyList()

        val result = mutableListOf<StrokePath>()

        // Use medians as the animation path (simpler and designed for this)
        for (median in data.medians) {
            if (median.isEmpty()) continue
            val points = median.map { pt ->
                val x = pt[0]
                val y = pt[1]
                // Flip Y and normalize to 0..1
                val nx = x / MMAH_SIZE
                val ny = (MMAH_Y_TOP - y) / MMAH_HEIGHT
                nx to ny
            }
            result.add(StrokePath(points, width = 4f))
        }

        return result
    }
}
