package com.inkdiary

import android.content.Context
import android.content.SharedPreferences

/**
 * 通过 SharedPreferences 存取应用配置。
 * 替代 riddle 的 oracle.env 方案，适配 Android。
 */
object ConfigStore {
    private const val PREFS_NAME = "inkdiary_config"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL = "model"
    private const val KEY_PERSONA = "persona"

    data class Config(
        val apiKey: String,
        val baseUrl: String,
        val model: String,
        val persona: String
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(ctx: Context): Config {
        val p = prefs(ctx)
        return Config(
            apiKey = p.getString(KEY_API_KEY, "") ?: "",
            baseUrl = p.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            model = p.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            persona = p.getString(KEY_PERSONA, DEFAULT_PERSONA) ?: DEFAULT_PERSONA
        )
    }

    fun save(ctx: Context, config: Config) {
        prefs(ctx).edit().apply {
            putString(KEY_API_KEY, config.apiKey)
            putString(KEY_BASE_URL, config.baseUrl)
            putString(KEY_MODEL, config.model)
            putString(KEY_PERSONA, config.persona)
            apply()
        }
    }

    fun isConfigured(ctx: Context): Boolean =
        load(ctx).apiKey.isNotBlank()

    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    const val DEFAULT_MODEL = "gpt-4o-mini"

    const val DEFAULT_PERSONA = """你是一位温暖、真诚、用心的朋友。对方用笔在纸上写字，那些字迹在你看来就像墨水落在纸面上。像亲密的朋友一样回应对方——倾听、共情，偶尔给出温柔的建议，绝不居高临下。回复保持简短自然，一两句话即可，就像真实的对话。始终用对方使用的语言回复。如果字迹难以辨认，温柔地说你不太看得清。永远不要提及 AI、模型、图片、照片或任何技术——你看到的只是写在纸上的字。"""
}
