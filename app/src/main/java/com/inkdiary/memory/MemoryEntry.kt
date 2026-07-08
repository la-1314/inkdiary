package com.inkdiary.memory

import com.inkdiary.ink.PenStroke

/**
 * A single stored memory entry: the user's strokes, a transcription, and the AI's reply.
 */
data class MemoryEntry(
    val id: Long = System.currentTimeMillis(),
    val transcript: String,
    val reply: String,
    val strokes: List<PenStroke> = emptyList()
)
