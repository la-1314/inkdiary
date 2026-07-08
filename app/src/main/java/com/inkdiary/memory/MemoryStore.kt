package com.inkdiary.memory

import android.content.Context
import com.inkdiary.ink.PenStroke
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Stores memories as JSON files on disk. Each entry includes the user's
 * strokes, a transcription, and the AI's reply. Recent memories are
 * injected into oracle requests for memory-driven growth.
 *
 * Storage layout (in app filesDir):
 *   memories/
 *     index.json    — list of {id, transcript, reply}
 *     <id>.strokes  — serialized pen strokes for replay
 */
class MemoryStore(context: Context) {

    private val gson = Gson()
    private val memDir = File(context.filesDir, "memories")
    private val indexFile = File(memDir, "index.json")
    private val maxMemories = 400

    private val entries = mutableListOf<MemoryEntry>()

    init {
        memDir.mkdirs()
        loadIndex()
    }

    private fun loadIndex() {
        if (!indexFile.exists()) return
        try {
            val type = object : TypeToken<List<MemoryEntry>>() {}.type
            val list: List<MemoryEntry> = gson.fromJson(indexFile.readText(), type) ?: emptyList()
            entries.clear()
            entries.addAll(list)
        } catch (_: Exception) {
            // Corrupt index — start fresh
            entries.clear()
        }
    }

    private fun saveIndex() {
        indexFile.writeText(gson.toJson(entries))
    }

    /**
     * Add a new memory. Also saves strokes to a separate file.
     */
    fun add(entry: MemoryEntry) {
        entries.add(entry)

        // Save strokes
        if (entry.strokes.isNotEmpty()) {
            val strokesFile = File(memDir, "${entry.id}.strokes")
            strokesFile.writeText(gson.toJson(entry.strokes.map { stroke ->
                stroke.points.map { Triple(it.x, it.y, it.pressure) }
            }))
        }

        // Prune old entries
        while (entries.size > maxMemories) {
            val removed = entries.removeAt(0)
            File(memDir, "${removed.id}.strokes").delete()
        }

        saveIndex()
    }

    /**
     * Get the N most recent memories for context injection.
     */
    fun getRecent(count: Int): List<MemoryEntry> {
        return entries.takeLast(count)
    }

    /**
     * Search memories by keyword in transcript or reply.
     */
    fun search(keyword: String): List<MemoryEntry> {
        val lower = keyword.lowercase()
        return entries.filter {
            it.transcript.lowercase().contains(lower) ||
            it.reply.lowercase().contains(lower)
        }
    }

    /**
     * Load strokes for a memory entry (for replay/conjuring).
     */
    fun loadStrokes(id: Long): List<PenStroke> {
        val file = File(memDir, "$id.strokes")
        if (!file.exists()) return emptyList()
        val type = object : TypeToken<List<List<Triple<Float, Float, Float>>>>() {}.type
        val raw: List<List<Triple<Float, Float, Float>>> = gson.fromJson(file.readText(), type)
        return raw.map { pointList ->
            PenStroke(pointList.map { Triple(it.first, it.second, it.third) }
                .mapIndexed { i, (x, y, p) ->
                    PenStroke.Point(x, y, p, System.currentTimeMillis() + i)
                }.toMutableList())
        }
    }

    fun clearAll() {
        entries.clear()
        memDir.listFiles()?.forEach { it.delete() }
        saveIndex()
    }
}
