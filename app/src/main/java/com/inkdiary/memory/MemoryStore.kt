package com.inkdiary.memory

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Stores memories as JSON files on disk. Each entry includes the user's
 * strokes, a transcription, and the AI's reply. ALL notes are kept
 * permanently (no pruning) so the user never loses a conversation.
 *
 * A separate memory.md file holds the AI-maintained long-term memory
 * profile. Instead of injecting raw conversation history into the system
 * prompt, we inject this compact memory file. The AI updates it every
 * [MEMORY_UPDATE_INTERVAL] exchanges via summarizeMemory.
 *
 * Storage layout (in app filesDir):
 *   memories/
 *     index.json    - list of {id, transcript, reply}  (metadata only)
 *     memory.md     - AI-maintained long-term memory profile
 *     counter.json  - { exchangeCount } for update scheduling
 *     <id>.strokes  - serialized pen strokes for replay/backup
 *
 * Export/import: exportToZip / importFromZip bundle the whole memories/
 * directory (all notes + strokes + memory.md) into a single .zip archive.
 */
class MemoryStore(context: Context) {
    private val gson = Gson()
    private val memDir = File(context.filesDir, "memories")
    private val indexFile = File(memDir, "index.json")
    private val memoryMdFile = File(memDir, "memory.md")
    private val counterFile = File(memDir, "counter.json")

    private val entries = mutableListOf<MemoryEntry>()
    private var exchangeCount = 0

    init {
        memDir.mkdirs()
        loadIndex()
        loadCounter()
        if (!memoryMdFile.exists()) {
            memoryMdFile.writeText(DEFAULT_MEMORY_MD)
        }
    }

    private fun loadIndex() {
        if (!indexFile.exists()) return
        try {
            val type = object : TypeToken<List<MemoryEntry>>() {}.type
            val list: List<MemoryEntry> = gson.fromJson(indexFile.readText(), type) ?: emptyList()
            entries.clear()
            entries.addAll(list)
        } catch (_: Exception) {
            entries.clear()
        }
    }

    private fun saveIndex() {
        indexFile.writeText(gson.toJson(entries))
    }

    private fun loadCounter() {
        if (!counterFile.exists()) return
        try {
            val obj = gson.fromJson(counterFile.readText(), Counter::class.java)
            exchangeCount = obj?.exchangeCount ?: 0
        } catch (_: Exception) {
            exchangeCount = 0
        }
    }

    private fun saveCounter() {
        counterFile.writeText(gson.toJson(Counter(exchangeCount)))
    }

    private data class Counter(val exchangeCount: Int = 0)

    fun getMemoryMd(): String =
        if (memoryMdFile.exists()) memoryMdFile.readText() else DEFAULT_MEMORY_MD

    fun updateMemoryMd(content: String) {
        memoryMdFile.writeText(content.trim())
    }

    /**
     * Add a new memory. Strokes are saved to a separate file.
     * ALL entries are kept permanently — no pruning.
     */
    fun add(entry: MemoryEntry) {
        entries.add(entry)
        if (entry.strokes.isNotEmpty()) {
            val strokesFile = File(memDir, "${entry.id}.strokes")
            strokesFile.writeText(gson.toJson(entry.strokes.map { stroke ->
                stroke.points.map { Triple(it.x, it.y, it.pressure) }
            }))
        }
        exchangeCount++
        saveIndex()
        saveCounter()
    }

    fun getRecent(count: Int): List<MemoryEntry> = entries.takeLast(count)

    fun getAll(): List<MemoryEntry> = entries.toList()

    fun shouldUpdateMemory(): Boolean =
        exchangeCount > 0 && exchangeCount % MEMORY_UPDATE_INTERVAL == 0

    fun search(keyword: String): List<MemoryEntry> {
        val lower = keyword.lowercase()
        return entries.filter {
            it.transcript.lowercase().contains(lower) ||
            it.reply.lowercase().contains(lower)
        }
    }

    fun loadStrokes(id: Long): List<com.inkdiary.ink.PenStroke> {
        val file = File(memDir, "$id.strokes")
        if (!file.exists()) return emptyList()
        val type = object : TypeToken<List<List<Triple<Float, Float, Float>>>>() {}.type
        val raw: List<List<Triple<Float, Float, Float>>> = gson.fromJson(file.readText(), type)
        return raw.map { pointList ->
            com.inkdiary.ink.PenStroke(pointList.map { Triple(it.first, it.second, it.third) }
                .mapIndexed { i, (x, y, p) ->
                    com.inkdiary.ink.PenStroke.Point(x, y, p, System.currentTimeMillis() + i)
                }.toMutableList())
        }
    }

    fun clearAll() {
        entries.clear()
        exchangeCount = 0
        memDir.listFiles()?.forEach { it.delete() }
        memoryMdFile.writeText(DEFAULT_MEMORY_MD)
        saveIndex()
        saveCounter()
    }

    /**
     * Bundle the entire memories/ directory (all notes, strokes, memory.md,
     * index.json, counter.json) into a .zip written to [out].
     */
    fun exportToZip(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun addFile(file: File, entryName: String) {
                if (!file.exists()) return
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            addFile(indexFile, "memories/index.json")
            addFile(memoryMdFile, "memories/memory.md")
            addFile(counterFile, "memories/counter.json")
            memDir.listFiles { f -> f.name.endsWith(".strokes") }?.forEach { f ->
                addFile(f, "memories/${f.name}")
            }
        }
    }

    /**
     * Restore memories/ from a .zip read from [input]. Replaces all current
     * data. Returns true on success.
     */
    fun importFromZip(input: InputStream): Boolean {
        return try {
            memDir.listFiles()?.forEach { it.delete() }
            entries.clear()
            exchangeCount = 0
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val rawName = entry.name.removePrefix("memories/")
                    if (rawName.isNotBlank() && !rawName.endsWith("/")) {
                        val outFile = File(memDir, rawName)
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zip.copyTo(it) }
                    }
                    entry = zip.closeEntry()
                    zip.nextEntry
                }
            }
            loadIndex()
            loadCounter()
            if (!memoryMdFile.exists()) {
                memoryMdFile.writeText(DEFAULT_MEMORY_MD)
            }
            saveIndex()
            saveCounter()
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val MEMORY_UPDATE_INTERVAL = 5
        const val DEFAULT_MEMORY_MD =
            "# 关于这个人\n\n（还没有对话，记忆将在交流中逐渐生长。）\n"
    }
}
