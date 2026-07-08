package com.inkdiary.gesture

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent

/**
 * Detects a three-finger long-press gesture (5 seconds by default).
 * Reports progress (0–100) for visual feedback while the user holds.
 */
class ThreeFingerLongPressDetector(
    private val onTrigger: () -> Unit,
    private val onProgress: (Int) -> Unit,
    private val onCancel: () -> Unit,
    private val holdDurationMs: Long = 5000L,
    private val moveTolerancePx: Float = 60f
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pointerCount = 0
    private var startTime = 0L
    private var initialX = FloatArray(3)
    private var initialY = FloatArray(3)
    private var fired = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (startTime == 0L) return
            val elapsed = SystemClock.uptimeMillis() - startTime
            val progress = ((elapsed.toFloat() / holdDurationMs) * 100).toInt().coerceIn(0, 100)
            onProgress(progress)
            if (elapsed >= holdDurationMs) {
                if (!fired) {
                    fired = true
                    onTrigger()
                }
            } else {
                handler.postDelayed(this, 50)
            }
        }
    }

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerCount = event.pointerCount
                if (pointerCount == 3 && startTime == 0L) {
                    startTime = SystemClock.uptimeMillis()
                    fired = false
                    for (i in 0 until 3) {
                        initialX[i] = event.getX(i)
                        initialY[i] = event.getY(i)
                    }
                    handler.post(progressRunnable)
                } else if (pointerCount > 3) {
                    cancel()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount == 3 && startTime != 0L) {
                    for (i in 0 until minOf(3, event.pointerCount)) {
                        val dx = event.getX(i) - initialX[i]
                        val dy = event.getY(i) - initialY[i]
                        if (dx * dx + dy * dy > moveTolerancePx * moveTolerancePx) {
                            cancel()
                            break
                        }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                cancel()
            }
        }
    }

    private fun cancel() {
        handler.removeCallbacks(progressRunnable)
        if (startTime != 0L) {
            startTime = 0L
            pointerCount = 0
            fired = false
            onCancel()
        }
    }
}
