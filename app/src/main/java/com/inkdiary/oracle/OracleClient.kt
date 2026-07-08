package com.inkdiary.oracle

import android.util.Base64
import android.util.Log
import com.inkdiary.memory.MemoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Calls an OpenAI-compatible /chat/completions endpoint with a vision payload.
 * Reads handwriting from a PNG image and streams back the reply.
 *
 * Uses blocking OkHttp (called on IO dispatcher) with SSE line streaming
 * to get sentence-by-sentence replies.
 */
class OracleClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) {
    companion object {
        private const val TAG = "OracleClient"
        private const val MAX_TOKENS = 2000
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Send a PNG to the vision LLM and get the full reply text.
     * The system prompt includes persona + memory context.
     *
     * Uses streaming for faster first ink, but collects the full reply.
     */
    suspend fun ask(
        pngPath: String,
        persona: String,
        memories: List<MemoryEntry> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val pngFile = File(pngPath)
        val pngBytes = pngFile.readBytes()
        val base64Image = Base64.encodeToString(pngBytes, Base64.NO_WRAP)

        val messages = JSONArray()

        // System message with persona + memories
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", persona)
        })

        // User message with image
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
            throw Exception("Oracle HTTP ${response.code}: $errorBody")
        }

        val fullReply = StringBuilder()
        val source = response.source

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
                    // Skip malformed chunks
                }
            }
        } finally {
            response.close()
        }

        val reply = fullReply.toString().trim()
        Log.d(TAG, "Oracle reply length: ${reply.length}")
        reply
    }
}
