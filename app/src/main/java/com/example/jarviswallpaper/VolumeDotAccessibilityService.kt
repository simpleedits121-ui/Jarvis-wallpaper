package com.example.jarviswallpaper

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.BlurMaskFilter
import android.media.AudioManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper
import kotlin.math.ceil
import kotlin.math.max

/**
 * Optional accessibility service used only to observe hardware volume-key presses.
 * Returning false lets Android continue handling the volume key normally.
 */
class VolumeDotAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: VolumeDotView? = null
    private var overlayAdded = false
    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private val hideOverlay = Runnable { removeOverlay() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = removeOverlay()

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
             event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            val stream = if (audioManager.isMusicActive) AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING
            val rising = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
            // Read the new system volume after Android has processed the hardware key.
            handler.postDelayed({
                val maxVolume = max(1, audioManager.getStreamMaxVolume(stream))
                val current = audioManager.getStreamVolume(stream)
                showOverlay(current, maxVolume, rising)
            }, 90L)
        }
        return false
    }

    private fun showOverlay(level: Int, maxLevel: Int, rising: Boolean) {
        handler.removeCallbacks(hideOverlay)
        if (!overlayAdded) {
            overlay = VolumeDotView(this)
            val params = WindowManager.LayoutParams(
                (260 * resources.displayMetrics.density).toInt(),
                (104 * resources.displayMetrics.density).toInt(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (resources.displayMetrics.heightPixels * 0.19f).toInt()
            }
            try {
                windowManager.addView(overlay, params)
                overlayAdded = true
            } catch (_: Exception) {
                overlay = null
                return
            }
        }
        overlay?.update(level, maxLevel, rising)
        handler.postDelayed(hideOverlay, 1250L)
    }

    private fun removeOverlay() {
        handler.removeCallbacks(hideOverlay)
        if (overlayAdded) {
            try { windowManager.removeView(overlay) } catch (_: Exception) {}
        }
        overlayAdded = false
        overlay = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}

private class VolumeDotView(context: android.content.Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var level = 0
    private var maxLevel = 1
    private var rising = true
    private var phase = 0f
    private val dot = 2.15f * density
    private val gap = 7.2f * density

    fun update(value: Int, maximum: Int, isRising: Boolean) {
        level = value.coerceIn(0, maximum)
        maxLevel = maximum.coerceAtLeast(1)
        rising = isRising
        phase += if (isRising) 0.7f else -0.7f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = 0xD9090712.toInt()
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(0f, 0f, w, h, 18f * density, 18f * density, paint)

        val columns = 25
        val rows = 7
        val totalDots = columns * rows
        val litCount = ceil(totalDots * level.toFloat() / maxLevel).toInt()
        val startX = (w - (columns - 1) * gap) / 2f
        val startY = 30f * density
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val index = row * columns + col
                val x = startX + col * gap
                val y = startY + row * gap
                val lit = index < litCount
                val wave = (0.72f + 0.28f * kotlin.math.sin(phase + col * 0.34f + row * 0.22f)).coerceIn(0.25f, 1f)
                val alpha = if (lit) (190 + 65 * wave).toInt() else 34
                paint.color = if (!lit) 0x553F315A else if (rising) {
                    android.graphics.Color.argb(alpha, 190, 125, 255)
                } else {
                    android.graphics.Color.argb(alpha, 90, 205, 255)
                }
                paint.maskFilter = if (lit) BlurMaskFilter(2.4f * density, BlurMaskFilter.Blur.NORMAL) else null
                canvas.drawCircle(x, y, if (lit) dot * (0.9f + wave * 0.18f) else dot * 0.72f, paint)
            }
        }
        paint.maskFilter = null
        paint.color = 0xFFEDE4FF.toInt()
        paint.textSize = 11f * density
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("J.A.R.V.I.S  •  VOLUME  ${(level * 100 / maxLevel)}%", w / 2f, h - 8f * density, paint)
    }
}
