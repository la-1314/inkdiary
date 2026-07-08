package com.inkdiary

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.inkdiary.gesture.ThreeFingerLongPressDetector
import com.inkdiary.ink.InkCanvasView
import com.inkdiary.ink.PenStroke
import com.inkdiary.memory.MemoryEntry
import com.inkdiary.memory.MemoryStore
import com.inkdiary.oracle.OracleClient
import com.inkdiary.oracle.Persona
import com.inkdiary.stroke.StrokeAnimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * The main diary screen. Handles pen input, idle detection, oracle calls,
 * and stroke-by-stroke reply animation.
 */
class DiaryActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DiaryActivity"
        private const val IDLE_COMMIT_MS = 2800L
        private const val ORACLE_TIMEOUT_MS = 120_000L
        private val FONT_PATH = "fonts/DancingScript.ttf"
    }

    private lateinit var inkCanvas: InkCanvasView
    private lateinit var tvHint: TextView
    private lateinit var progressGesture: ProgressBar

    private lateinit var config: ConfigStore.Config
    private lateinit var oracleClient: OracleClient
    private lateinit var memoryStore: MemoryStore
    private lateinit var strokeAnimator: StrokeAnimator

    private val handler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { commitPage() }
    private var isProcessing = false

    private val gestureDetector = ThreeFingerLongPressDetector(
        onTrigger = { enterConfig() },
        onProgress = { progress ->
            progressGesture.visibility = View.VISIBLE
            progressGesture.progress = progress
        },
        onCancel = {
            progressGesture.visibility = View.INVISIBLE
            progressGesture.progress = 0
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diary)

        inkCanvas = findViewById(R.id.inkCanvas)
        tvHint = findViewById(R.id.tvHint)
        progressGesture = findViewById(R.id.progressGesture)

        config = ConfigStore.load(this)
        if (config.apiKey.isBlank()) {
            startActivity(Intent(this, ConfigActivity::class.java))
            finish()
            return
        }

        oracleClient = OracleClient(config.apiKey, config.baseUrl, config.model)
        memoryStore = MemoryStore(this)
        strokeAnimator = StrokeAnimator(inkCanvas, assets, FONT_PATH)

        tvHint.visibility = View.VISIBLE
        inkCanvas.onStrokeStart = {
            tvHint.visibility = View.GONE
            handler.removeCallbacks(idleRunnable)
        }
        inkCanvas.onStrokeEnd = {
            handler.postDelayed(idleRunnable, IDLE_COMMIT_MS)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private fun enterConfig() {
        progressGesture.visibility = View.INVISIBLE
        startActivity(Intent(this, ConfigActivity::class.java))
    }

    /**
     * User paused writing → commit the page as PNG and send to oracle.
     */
    private fun commitPage() {
        if (isProcessing) return
        val strokes = inkCanvas.getStrokes()
        if (strokes.isEmpty()) return

        isProcessing = true
        tvHint.visibility = View.VISIBLE
        tvHint.text = "…"

        lifecycleScope.launch {
            try {
                val pngFile = renderStrokesToPng(strokes)
                val reply = withContext(Dispatchers.IO) {
                    oracleClient.ask(
                        pngPath = pngFile.absolutePath,
                        persona = Persona.buildSystemPrompt(config.persona),
                        memories = memoryStore.getRecent(6)
                    )
                }

                // Fade out user ink
                inkCanvas.fadeOutInk()

                // Animate reply stroke by stroke
                strokeAnimator.animateReply(reply) {
                    // After animation completes, store memory and reset
                    val transcript = extractTranscript(reply)
                    memoryStore.add(MemoryEntry(
                        transcript = transcript,
                        reply = reply,
                        strokes = strokes
                    ))
                    handler.postDelayed({
                        inkCanvas.clear()
                        tvHint.visibility = View.VISIBLE
                        tvHint.text = getString(R.string.diary_hint)
                        isProcessing = false
                    }, 3000L)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Oracle call failed", e)
                tvHint.text = "(something went wrong…)"
                handler.postDelayed({
                    inkCanvas.clear()
                    tvHint.text = getString(R.string.diary_hint)
                    isProcessing = false
                }, 2000L)
            }
        }
    }

    /**
     * Render the current strokes to a PNG file for the vision LLM.
     */
    private fun renderStrokesToPng(strokes: List<PenStroke>): File {
        val w = inkCanvas.width.coerceAtLeast(1)
        val h = inkCanvas.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        inkCanvas.renderStrokesToCanvas(canvas, strokes)

        val file = File(cacheDir, "diary_page.png")
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
        return file
    }

    /**
     * Extract the user's transcribed text from the LLM reply.
     * The oracle appends a structured transcript at the end.
     */
    private fun extractTranscript(reply: String): String {
        val marker = "[TRANSCRIPT]"
        val idx = reply.lastIndexOf(marker)
        return if (idx >= 0) {
            reply.substring(idx + marker.length).trim()
        } else {
            ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(idleRunnable)
        strokeAnimator.cancel()
    }
}
