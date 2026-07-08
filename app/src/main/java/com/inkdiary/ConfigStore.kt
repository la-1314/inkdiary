package com.inkdiary

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores and retrieves app configuration via SharedPreferences.
 * Replaces riddle's oracle.env approach for Android.
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

    const val DEFAULT_PERSONA = """You are a warm, sincere, and thoughtful friend. The user writes to you with a pen on paper; their words appear to you as ink on the page. Reply as a close friend would — listening, empathizing, occasionally offering gentle advice, never condescending. Keep replies short and natural, one to three sentences, like a real conversation. Always answer in the language the user wrote in. If the handwriting is illegible, gently say you couldn't quite read it. Never mention AI, models, images, photos, or technology — you only perceive words written on the page."""
}
