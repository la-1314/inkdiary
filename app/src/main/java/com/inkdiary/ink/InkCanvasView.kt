package com.inkdiary.ink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * The writing surface. Captures pen/finger input as strokes and renders ink.
 * Also serves as the canvas for stroke animation of the reply.
 */
class InkCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val userStrokes = mutableListOf<PenStroke>()
    private var currentStroke: PenStroke? = null

    private val inkPaint = Paint().apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val fadePaint = Paint().apply {
        color = Color.parseColor("#888888")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
        alpha = 100
    }

    // Animation strokes drawn by StrokeAnimator
    private val animStrokes = mutableListOf<AnimStroke>()
    private data class AnimStroke(val points: List<Pair<Float, Float>>, val progress: Float)

    var onStrokeStart: (() -> Unit)? = null
    var onStrokeEnd: (() -> Unit)? = null

    private var isFading = false
    private var fadeAlpha = 255
    private val handler = Handler(Looper.getMainLooper())

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentStroke = PenStroke()
                currentStroke?.addPoint(
                    event.x, event.y,
                    event.pressure.coerceAtLeast(0.1f),
                    event.eventTime
                )
                onStrokeStart?.invoke()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentStroke?.addPoint(
                    event.x, event.y,
                    event.pressure.coerceAtLeast(0.1f),
                    event.eventTime
                )
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentStroke?.let {
                    if (!it.isEmpty()) {
                        userStrokes.add(it)
                    }
                }
                currentStroke = null
                onStrokeEnd?.invoke()
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#FFF8F0"))

        // Draw user strokes (fading if applicable)
        val paint = if (isFading) {
            inkPaint.alpha = fadeAlpha
            inkPaint
        } else {
            inkPaint.alpha = 255
            inkPaint
        }

        for (stroke in userStrokes) {
            drawStroke(canvas, stroke, paint)
        }
        currentStroke?.let { drawStroke(canvas, it, paint) }

        // Draw animation strokes
        for (anim in animStrokes) {
            if (anim.points.size < 2) continue
            inkPaint.alpha = 255
            val visibleCount = (anim.points.size * anim.progress).toInt()
                .coerceIn(2, anim.points.size)
            for (i in 1 until visibleCount) {
                val (x1, y1) = anim.points[i - 1]
                val (x2, y2) = anim.points[i]
                canvas.drawLine(x1, y1, x2, y2, inkPaint)
            }
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: PenStroke, paint: Paint) {
        if (stroke.points.size < 2) {
            if (stroke.points.size == 1) {
                val p = stroke.points[0]
                canvas.drawPoint(p.x, p.y, paint)
            }
            return
        }
        for (i in 1 until stroke.points.size) {
            val p0 = stroke.points[i - 1]
            val p1 = stroke.points[i]
            paint.strokeWidth = 2f + (p0.pressure + p1.pressure) * 4f
            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, paint)
        }
    }

    fun getStrokes(): List<PenStroke> = userStrokes.toList()

    fun renderStrokesToCanvas(canvas: Canvas, strokes: List<PenStroke>) {
        for (stroke in strokes) {
            drawStroke(canvas, stroke, inkPaint)
        }
    }

    /**
     * Fade out user ink gradually, then clear.
     */
    fun fadeOutInk() {
        isFading = true
        fadeAlpha = 255
        val fadeStep = object : Runnable {
            override fun run() {
                fadeAlpha -= 15
                if (fadeAlpha <= 0) {
                    isFading = false
                    fadeAlpha = 255
                    userStrokes.clear()
                } else {
                    invalidate()
                    handler.postDelayed(this, 16)
                }
            }
        }
        handler.post(fadeStep)
    }

    /**
     * Add an animation stroke segment (used by StrokeAnimator).
     */
    fun setAnimStrokes(strokes: List<List<Pair<Float, Float>>>) {
        animStrokes.clear()
        for (s in strokes) {
            animStrokes.add(AnimStroke(s, 0f))
        }
        invalidate()
    }

    fun updateAnimStrokeProgress(index: Int, progress: Float) {
        if (index < animStrokes.size) {
            animStrokes[index] = animStrokes[index].copy(progress = progress)
            invalidate()
        }
    }

    fun clearAnimStrokes() {
        animStrokes.clear()
        invalidate()
    }

    fun clear() {
        userStrokes.clear()
        animStrokes.clear()
        currentStroke = null
        isFading = false
        fadeAlpha = 255
        invalidate()
    }
}
