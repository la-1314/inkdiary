package com.inkdiary.stroke

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Provides stroke paths for Chinese characters using a three-tier strategy:
 *
 *  1. **Local bundle** (hanzi_common.json in assets) — ~4500 common chars,
 *     covering 99%+ of daily usage. Always available offline.
 *
 *  2. **CDN on-demand** (hanzi-writer-data on jsDelivr) — for rare chars not
 *     in the bundle. Fetched once, cached in filesDir/hanzi_cache/ for
 *     offline reuse on subsequent encounters.
 *
 *  3. **Zhang-Suen fallback** — if both local and CDN fail (network error,
 *     char not in any dataset), thin a system font glyph. Stroke order is
 *     heuristic (not authoritative) but the character still animates.
 *
 * Data source: https://github.com/skishore/makemeahanzi (Arphic Public License)
 * CDN: https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0/<char>.json
 */
class ChineseStrokeProvider(
    private val context: Context,
    private val assets: AssetManager,
    private val filesDir: File
) : StrokeProvider {

    companion object {
        private const val TAG = "ChineseStrokeProvider"
        private const val BUNDLED_FILE = "hanzi_common.json"
        private const val LEGACY_FILE = "hanzi_subset.json"
        private const val CACHE_DIR = "hanzi_cache"
        private const val MMAH_SIZE = 1024f
        private const val MMAH_Y_TOP = 900f
        private const val MMAH_Y_BOTTOM = -124f
        private val MMAH_HEIGHT = MMAH_Y_TOP - MMAH_Y_BOTTOM // 1024
        private const val CDN_BASE = "https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0/"
        private const val FETCH_TIMEOUT_S = 8L
    }

    private val gson = Gson()
    private val cacheDir = File(filesDir, CACHE_DIR)
    private val localCache = HashMap<Char, List<StrokePath>>()
    private var localLoaded = false

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(FETCH_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(FETCH_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    // Gson helpers
    private data class CharDataRaw(
        val medians: List<List<List<Float>>> = emptyList()
    )

    private fun ensureLocalLoaded() {
        if (localLoaded) return
        localLoaded = true
        cacheDir.mkdirs()

        val stream: InputStream? = try {
            assets.open(BUNDLED_FILE)
        } catch (_: Exception) {
            // Fall back to legacy subset file if the CI-built one isn't present
            try { assets.open(LEGACY_FILE) } catch (_: Exception) { null }
        }

        if (stream == null) {
            Log.w(TAG, "No bundled hanzi data found")
            return
        }

        try {
            val text = stream.bufferedReader().readText()
            stream.close()
            val type = object : TypeToken<Map<String, CharDataRaw>>() {}.type
            val raw: Map<String, CharDataRaw> = gson.fromJson(text, type) ?: emptyMap()
            for ((char, data) in raw) {
                if (char.isNotEmpty()) {
                    localCache[char[0]] = mediansToStrokes(data.medians)
                }
            }
            Log.i(TAG, "Loaded ${localCache.size} bundled Chinese characters")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bundled hanzi data", e)
        }
    }

    /**
     * Convert MMAH medians (1024x1024, Y-flipped) to normalized StrokePaths.
     */
    private fun mediansToStrokes(medians: List<List<List<Float>>>): List<StrokePath> {
        return medians.mapNotNull { median ->
            if (median.isEmpty()) return@mapNotNull null
            val points = median.map { pt ->
                val x = pt.getOrElse(0) { 0f }
                val y = pt.getOrElse(1) { 0f }
                val nx = x / MMAH_SIZE
                val ny = (MMAH_Y_TOP - y) / MMAH_HEIGHT
                nx to ny
            }
            StrokePath(points, width = 4f)
        }
    }

    override fun supports(char: Char): Boolean {
        val code = char.code
        return code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF
    }

    /**
     * Synchronous: returns strokes for a char from local bundle or cache.
     * Does NOT do network I/O. Call [prefetch] first to populate the cache
     * for rare characters.
     */
    override fun getStrokes(char: Char): List<StrokePath> {
        ensureLocalLoaded()

        // Tier 1: local bundle
        localCache[char]?.let { return it }

        // Tier 2: on-disk cache (from a prior CDN fetch)
        val cachedFile = File(cacheDir, "${char.code}.json")
        if (cachedFile.exists()) {
            try {
                val data: CharDataRaw = gson.fromJson(cachedFile.readText(), CharDataRaw::class.java)
                val strokes = mediansToStrokes(data.medians)
                localCache[char] = strokes
                return strokes
            } catch (_: Exception) {
                cachedFile.delete()
            }
        }

        // Tier 3: Zhang-Suen fallback (no authoritative stroke order)
        Log.w(TAG, "Char '$char' (U+${char.code.toString(16)}) not in local data, using Zhang-Suen fallback")
        return zhangSuenFallback(char)
    }

    /**
     * Prefetch rare characters in [text] from the CDN, caching them on disk.
     * Call this on a background thread before animation starts.
     * Characters already in the local bundle or cache are skipped.
     */
    suspend fun prefetch(text: String) = withContext(Dispatchers.IO) {
        ensureLocalLoaded()
        val missing = text.filter { supports(it) }
            .filter { char ->
                !localCache.containsKey(char) &&
                !File(cacheDir, "${char.code}.json").exists()
            }
            .toSet()

        if (missing.isEmpty()) return@withContext
        Log.i(TAG, "Prefetching ${missing.size} rare chars from CDN: ${missing.joinToString("")}")

        for (char in missing) {
            try {
                fetchFromCdn(char)
            } catch (e: Exception) {
                Log.w(TAG, "CDN fetch failed for '$char': ${e.message}")
                // Will fall back to Zhang-Suen at draw time
            }
        }
    }

    private fun fetchFromCdn(char: Char): List<StrokePath> {
        val encoded = URLEncoder.encode(char.toString(), "UTF-8")
        val url = "$CDN_BASE$encoded.json"

        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("Empty body")

            // hanzi-writer-data format: {"strokes":[...], "medians":[...]}
            val obj: JsonObject = gson.fromJson(body, JsonObject::class.java)
            val mediansRaw = obj.getAsJsonArray("medians") ?: throw Exception("No medians")
            val type = object : TypeToken<List<List<List<Float>>>>() {}.type
            val medians: List<List<List<Float>>> = gson.fromJson(mediansRaw, type)

            val strokes = mediansToStrokes(medians)

            // Cache to disk (store just medians in our compact format)
            val cacheFile = File(cacheDir, "${char.code}.json")
            cacheFile.writeText(gson.toJson(CharDataRaw(medians)))

            localCache[char] = strokes
            Log.d(TAG, "Fetched & cached '$char' from CDN (${strokes.size} strokes)")
            return strokes
        }
    }

    /**
     * Tier 3 fallback: rasterize a system CJK font glyph and thin it.
     * Stroke order is heuristic (left-to-right, top-to-bottom), not
     * authoritative — but the character still animates acceptably.
     */
    private fun zhangSuenFallback(char: Char): List<StrokePath> {
        return try {
            LatinStrokeProvider(assets, "fonts/DancingScript-Regular.ttf")
                .getStrokes(char).ifEmpty {
                    // If Latin provider also fails (no font), return empty
                    emptyList()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Zhang-Suen fallback failed for '$char'", e)
            emptyList()
        }
    }
}
