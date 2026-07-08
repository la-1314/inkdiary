package com.inkdiary.oracle

import android.util.Base64
import android.util.Log
import com.inkdiary.memory.MemoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Calls an OpenAI-compatible /chat/completions endpoint with a vision payload.
 * Also provides summarizeMemory to refresh memory.md periodically.
 */
class OracleClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) {
    companion object {
        private const val TAG = "OracleClient"
        private const val MAX_TOKENS = 2000
        private const val MEMORY_MAX_TOKENS = 1200
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun ask(
        pngPath: String,
        persona: String,
        memories: List<MemoryEntry> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val pngFile = File(pngPath)
        val pngBytes = pngFile.readBytes()
        val base64Image = Base64.encodeToString(pngBytes, Base64.NO_WRAP)

        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", persona)
        })
        val content = JSONArray()
        content.put(JSONObject().apply {
            put("type", "text")
            put("text", "Please read what is written on this page and reply.")
        })
        content.put(JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply {
                put("url", "data:image/png;base64,$base64Image")
            })
        })
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", content)
        })

        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", MAX_TOKENS)
            put("stream", true)
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            response.close()
            throw Exception("Oracle HTTP ${response.code}: $errorBody")
        }

        val fullReply = StringBuilder()
        val body = response.body ?: run {
            response.close()
            throw Exception("Oracle returned empty body")
        }
        val source = body.source()
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                    val content2 = delta.optString("content", "")
                    if (content2.isNotEmpty()) {
                        fullReply.append(content2)
                    }
                } catch (_: Exception) {
                }
            }
        } finally {
            response.close()
        }

        val reply = fullReply.toString().trim()
        Log.d(TAG, "Oracle reply length: ${reply.length}")
        reply
    }

    /**
     * Ask the model to refresh memory.md from the current memory file plus
     * the most recent exchanges. Returns the updated markdown content.
     */
    suspend fun summarizeMemory(
        currentMemoryMd: String,
        recentExchanges: List<MemoryEntry>
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = """You maintain a private memory file (memory.md) about the person you're conversing with on paper. Your job is to update this file so you remember them better over time.

Rules for the memory file:
- Write in the SAME language the person uses.
- Keep it concise (under ~400 words). This is a living profile, not a transcript.
- Capture: who they are, what they care about, recurring topics, preferences, emotional state, and anything worth remembering.
- DO preserve useful facts from the existing file. ADD new insights from the recent exchanges. PRUNE outdated or redundant content.
- Output ONLY the updated memory.md content in Markdown. No preamble, no code fences, no explanations — just the file contents."""

        val exchangeBlock = StringBuilder()
        for ((i, m) in recentExchanges.withIndex()) {
            exchangeBlock.append("\n[Exchange ${i + 1}]\n")
            if (m.transcript.isNotBlank()) {
                exchangeBlock.append("They wrote: ").append(m.transcript).append("\n")
            }
            exchangeBlock.append("You replied: ").append(m.reply).append("\n")
        }

        val userPrompt = StringBuilder()
        userPrompt.append("--- Current memory.md ---\n")
        userPrompt.append(if (currentMemoryMd.isNotBlank()) currentMemoryMd.trim() else "(empty)")
        userPrompt.append("\n\n--- Recent exchanges to incorporate ---\n")
        userPrompt.append(exchangeBlock)
        userPrompt.append("\n\nNow output the fully updated memory.md:")

        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userPrompt.toString())
        })

        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", MEMORY_MAX_TOKENS)
            put("stream", false)
            put("temperature", 0.3)
        }

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            response.close()
            throw Exception("Memory summary HTTP ${response.code}: $errorBody")
        }
        try {
            val bodyText = response.body?.string() ?: ""
            val json = JSONObject(bodyText)
            val choices = json.optJSONArray("choices")
            val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
            content.trim()
        } finally {
            response.close()
        }
    }
}
