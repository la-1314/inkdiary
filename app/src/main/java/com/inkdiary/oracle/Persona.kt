package com.inkdiary.oracle

import com.inkdiary.memory.MemoryEntry

/**
 * Builds the system prompt with persona + memory-file injection.
 *
 * Memory model: instead of stuffing recent conversation history into the
 * prompt, we inject a compact AI-maintained memory profile (memory.md).
 * The AI refreshes this file every few exchanges via OracleClient.summarizeMemory.
 */
object Persona {

    private const val IMMERSION_RULES = """
---
CRITICAL OUTPUT RULES (the reply is rendered as handwriting stroke by stroke):
1. NEVER use emoji, emoticons, kaomoji, or any pictographic characters (e.g. they break the animation engine). The animation engine only supports letters, digits, basic punctuation, and CJK characters.
2. NEVER use special Unicode symbols, decorative characters, or non-standard punctuation (e.g. arrows, stars). Stick to plain letters, numbers, and common punctuation marks (. , ! ? : ; - ' ( )).
3. Reply in plain natural text only.
4. Never mention AI, models, APIs, images, photos, or technology — you only perceive words written on the page. If the handwriting is illegible, gently say you couldn't quite read it.
5. Always answer in the language the user wrote in.
6. At the very end of your reply, on a new line, append your best-effort transcription of what the user wrote, prefixed with [TRANSCRIPT]. This is invisible to the user and used only for memory. Example:
[TRANSCRIPT] What they actually wrote goes here"""

    private const val MEMORY_PROTOCOL = """
---
Below is your long-term memory about this person (maintained in a memory file). It is updated periodically as you get to know them. Treat it as what you remember about them, and refer to it naturally when relevant — never mention that you're reading from a file."""

    fun buildSystemPrompt(persona: String, memoryMd: String = ""): String {
        val sb = StringBuilder(persona)
        sb.append(IMMERSION_RULES)
        if (memoryMd.isNotBlank()) {
            sb.append(MEMORY_PROTOCOL)
            sb.append("\n\n--- memory.md ---\n").append(memoryMd.trim())
        }
        return sb.toString()
    }
}
