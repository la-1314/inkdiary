package com.inkdiary.oracle

import com.inkdiary.memory.MemoryEntry

/**
 * Builds the system prompt with persona + memory injection.
 * Memory-driven growth: recent conversations are injected so the AI
 * naturally gets to know the user over time.
 */
object Persona {

    /**
     * The memory protocol appended to the persona. Instructs the LLM to:
     * 1. Use the injected memories to show it remembers past conversations
     * 2. Append a [TRANSCRIPT] of what it read at the end (for storage)
     */
    private const val MEMORY_PROTOCOL = """

---
You are having an ongoing conversation on paper. Below are your recent exchanges with this person, if any. Reference them naturally when relevant — don't force it, but let them know you remember.

IMPORTANT: At the very end of your reply, on a new line, append your best-effort transcription of what the user wrote, prefixed with [TRANSCRIPT]. This is invisible to the user and used only for memory. Example:

[TRANSCRIPT] What they actually wrote goes here"""

    fun buildSystemPrompt(persona: String, memories: List<MemoryEntry> = emptyList()): String {
        val sb = StringBuilder(persona)

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
