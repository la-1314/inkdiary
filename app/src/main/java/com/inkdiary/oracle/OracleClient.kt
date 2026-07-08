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
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * 调用 OpenAI 兼容的 /chat/completions 接口（带视觉输入）。
 * 同时提供 summarizeMemory 用于周期性刷新 memory.md。
 *
 * 所有抛出的异常均为 [OracleException]，携带可读的中文错误信息，
 * 上层可直接展示给用户。
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

    /** 可读的中文异常，message 直接面向用户。 */
    class OracleException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
            put("text", "请阅读这一页上写的内容并回复。")
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

        val response = try {
            client.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            throw OracleException("请求超时，服务器响应过慢，请稍后再试。", e)
        } catch (e: IOException) {
            throw OracleException("网络连接失败，请检查网络后重试。", e)
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            response.close()
            val code = response.code
            val msg = when (code) {
                401, 403 -> "API 密钥无效或已过期，请到设置中检查。"
                429 -> "请求过于频繁，已触发限流，请稍后再试。"
                in 500..599 -> "服务器错误（$code），请稍后再试。"
                else -> "请求失败（HTTP $code）：${errorBody.take(200)}"
            }
            throw OracleException(msg)
        }

        val fullReply = StringBuilder()
        val body = response.body ?: run {
            response.close()
            throw OracleException("服务器返回了空回复，请重试。")
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
     * 让模型根据当前 memory.md 与最近若干轮对话刷新记忆文件。
     * 返回更新后的 markdown 内容。
     */
    suspend fun summarizeMemory(
        currentMemoryMd: String,
        recentExchanges: List<MemoryEntry>
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = """你负责维护一份关于对话对象的私人记忆文件（memory.md）。你的任务是不断更新这份文件，让你随时间推移越来越了解对方。

记忆文件规则：
- 用对方使用的语言书写。
- 保持简洁（400 字以内）。这是一份持续生长的人物画像，不是对话记录。
- 记录：对方是谁、在意什么、反复出现的话题、偏好、情绪状态，以及任何值得记住的事。
- 保留现有文件中仍有价值的事实，补充新近对话带来的新洞察，剔除过时或重复的内容。
- 只输出更新后的 memory.md 内容（Markdown 格式），不要写任何前言、代码围栏或解释说明。"""

        val exchangeBlock = StringBuilder()
        for ((i, m) in recentExchanges.withIndex()) {
            exchangeBlock.append("\n[第 ${i + 1} 轮]\n")
            if (m.transcript.isNotBlank()) {
                exchangeBlock.append("对方写了：").append(m.transcript).append("\n")
            }
            exchangeBlock.append("你回复了：").append(m.reply).append("\n")
        }

        val userPrompt = StringBuilder()
        userPrompt.append("--- 当前 memory.md ---\n")
        userPrompt.append(if (currentMemoryMd.isNotBlank()) currentMemoryMd.trim() else "(空)")
        userPrompt.append("\n\n--- 需要纳入的最近对话 ---\n")
        userPrompt.append(exchangeBlock)
        userPrompt.append("\n\n现在输出完整的更新后的 memory.md：")

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

        val response = try {
            client.newCall(request).execute()
        } catch (e: SocketTimeoutException) {
            throw OracleException("记忆刷新超时，将在下个周期重试。", e)
        } catch (e: IOException) {
            throw OracleException("记忆刷新网络失败，将在下个周期重试。", e)
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            response.close()
            val code = response.code
            val msg = when (code) {
                401, 403 -> "记忆刷新失败：API 密钥无效。"
                429 -> "记忆刷新失败：请求过于频繁。"
                in 500..599 -> "记忆刷新失败：服务器错误（$code）。"
                else -> "记忆刷新失败（HTTP $code）：${errorBody.take(200)}"
            }
            throw OracleException(msg)
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
