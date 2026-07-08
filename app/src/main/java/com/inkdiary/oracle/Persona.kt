package com.inkdiary.oracle

import com.inkdiary.memory.MemoryEntry

/**
 * Builds the system prompt with persona + memory injection.
 * Memory-driven growth: recent conversations are injected so the AI
 * naturally gets to know the user over time.
 */
object Persona {

    /**
     * Constraints appended to every persona to keep the diary immersive.
     *
     * Key rules:
     * - NO emoji, pictographs, or special symbols (they break the handwriting
     *   animation engine, which only handles letters and CJK characters)
     * - NO mentions of AI/models/technology (preserve the illusion)
     * - Append a [TRANSCRIPT] of the user's handwriting for memory storage
     */
    private const val IMMERSION_RULES = """

---
CRITICAL OUTPUT RULES (the reply is rendered as handwriting stroke by stroke):
1. NEVER use emoji, emoticons, kaomoji, or any pictographic characters (e.g. 😊 ❤️ 🌸). The animation engine only supports letters, digits, basic punctuation, and CJK characters — emoji will render as blank gaps.
2. NEVER use special Unicode symbols, decorative characters, or non-standard punctuation (e.g. ★ ♪ ☆ → ※). Stick to plain letters, numbers, and common punctuation marks (. , ! ? : ; - ' " ( )).
3. Reply in plain natural text only.
4. Never mention AI, models, APIs, images, photos, or technology — you only perceive words written on the page. If the handwriting is illegible, gently say you couldn't quite read it.
5. Always answer in the language the user wrote in."""

    /**
     * The memory protocol: instructs the LLM to use injected memories and
     * append a transcription at the end for local storage.
     */
    private const val MEMORY_PROTOCOL = """

---
You are having an ongoing conversation on paper. Below are your recent exchanges with this person, if any. Reference them naturally when relevant — don't force it, but let them know you remember.

At the very end of your reply, on a new line, append your best-effort transcription of what the user wrote, prefixed with [TRANSCRIPT]. This is invisible to the user and used only for memory. Example:

[TRANSCRIPT] What they actually wrote goes here"""

    fun buildSystemPrompt(persona: String, memories: List<MemoryEntry> = emptyList()): String {
        val sb = StringBuilder(persona)
        sb.append(IMMERSION_RULES)

        if (memories.isNotEmpty()) {
            sb.append(MEMORY_PROTOCOL)
            sb.append("\n\n--- Recent exchanges ---\n")
            for ((i, m) in memories.withIndex()) {
                sb.append("\n[Exchange ${i + 1}]")
                if (m.transcript.isNotBlank()) {
                    sb.append("\nThey wrote: ").append(m.transcript)
                }
                sb.append("\nYou replied: ").append(m.reply)
            }
        }

        return sb.toString()
    }
}
