package com.example.jarviswallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.graphics.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import java.util.Random
import kotlin.math.*

class JarvisWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MatrixEngine()

    inner class MatrixEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val random = Random(9)
        private var visible = false
        private var frame = 0
        private var player: MediaPlayer? = null
        private var videoMode = false
        private var lastThermal = 0
        private val prefs by lazy { getSharedPreferences("jarvis_wallpaper", MODE_PRIVATE) }
        private val drawTask = object : Runnable {
            override fun run() {
                if (!visible) return
                drawFrame()
                val power = getSystemService(POWER_SERVICE) as PowerManager
                val saver = power.isPowerSaveMode
                val thermal = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else 0
                val delay = when {
                    saver || thermal >= 3 -> 100L
                    thermal >= 2 -> 66L
                    else -> 40L
                }
                handler.postDelayed(this, delay)
            }
        }

        override fun onVisibilityChanged(visibleNow: Boolean) {
            visible = visibleNow
            handler.removeCallbacks(drawTask)
            if (visible) {
                setupVideoIfSelected()
                handler.post(drawTask)
            } else {
                releaseVideo()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (visible) drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawTask)
            releaseVideo()
            super.onSurfaceDestroyed(holder)
        }

        private fun setupVideoIfSelected() {
            val uriString = prefs.getString("video_uri", null) ?: return
            if (player != null) return
            try {
                val p = MediaPlayer()
                p.setDataSource(this@JarvisWallpaperService, Uri.parse(uriString))
                p.isLooping = true
                p.setSurface(surfaceHolder.surface)
                p.setOnPreparedListener { if (visible) it.start() }
                p.setOnErrorListener { _, _, _ -> releaseVideo(); true }
                p.prepareAsync()
                player = p
                videoMode = true
            } catch (_: Exception) {
                releaseVideo()
            }
        }

        private fun releaseVideo() {
            try { player?.stop() } catch (_: Exception) {}
            try { player?.release() } catch (_: Exception) {}
            player = null
            videoMode = false
        }

        private fun drawFrame() {
            if (!visible || videoMode && player?.isPlaying == true) return
            val canvas = try { surfaceHolder.lockCanvas() } catch (_: Exception) { null } ?: return
            try {
                val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
                canvas.drawColor(Color.rgb(5, 10, 23))
                val saver = (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode
                val step = if (saver) 22f else 16f
                val t = frame++ * 0.035f
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(174, 93, 255); alpha = if (saver) 100 else 165
                    maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
                }
                val dot = Paint(Paint.ANTI_ALIAS_FLAG)
                var y = 0f
                while (y < h) {
                    var x = 0f
                    while (x < w) {
                        val wave = sin(x * 0.018f + t) * 9f + cos(y * 0.025f - t * 0.8f) * 7f
                        val cx = w * 0.5f + cos(y * 0.009f + t) * 32f
                        val cy = h * 0.48f + sin(x * 0.012f + t) * 42f
                        val dx = x - cx; val dy = y - cy
                        val ring = abs(sqrt(dx*dx + dy*dy) - (min(w,h)*0.22f + wave))
                        val pulse = (0.5f + 0.5f * sin(t * 1.7f + x * 0.012f + y * 0.009f))
                        val alpha = (55 + pulse * 160 - min(90f, ring * 1.4f)).toInt().coerceIn(22, 210)
                        dot.color = Color.argb(alpha, 166, 101, 255)
                        val radius = 1.1f + pulse * 1.25f
                        canvas.drawCircle(x, y, radius, dot)
                        if (ring < 8f && !saver) canvas.drawCircle(x, y, radius + 1.6f, glow)
                        x += step
                    }
                    y += step
                }
                paint.color = Color.argb(110, 90, 210, 255)
                paint.textSize = 18f
                paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                canvas.drawText("J.A.R.V.I.S", 24f, h * 0.12f, paint)
                paint.textSize = 9f
                paint.color = Color.argb(90, 180, 205, 255)
                canvas.drawText("NEURAL INTERFACE  //  ONLINE", 25f, h * 0.12f + 16f, paint)
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onDestroy() {
            visible = false; handler.removeCallbacks(drawTask); releaseVideo()
            super.onDestroy()
        }
    }
}
