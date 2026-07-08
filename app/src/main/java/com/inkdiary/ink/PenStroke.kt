package com.inkdiary.ink

/**
 * A single pen stroke: a sequence of points with pressure.
 */
data class PenStroke(
    val points: MutableList<Point> = mutableListOf()
) {
    data class Point(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val timestamp: Long
    )

    fun addPoint(x: Float, y: Float, pressure: Float, timestamp: Long) {
        points.add(Point(x, y, pressure.coerceIn(0f, 1f), timestamp))
    }

    fun isEmpty(): Boolean = points.isEmpty()
}
