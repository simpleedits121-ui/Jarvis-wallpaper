package com.example.jarviswallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.graphics.*
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import kotlin.math.*

private data class MatrixDot(
    val x: Float, val y: Float, val alpha: Int,
    val red: Int, val green: Int, val blue: Int
)

class JarvisWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MatrixEngine()

    inner class MatrixEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var frame = 0
        private var player: MediaPlayer? = null
        private var videoMode = false
        private var bitmap: Bitmap? = null
        private var loadedUri: String? = null
        private var dots: List<MatrixDot> = emptyList()
        private var cachedWidth = 0
        private var cachedHeight = 0
        private val prefs by lazy {
            getSharedPreferences("jarvis_wallpaper", MODE_PRIVATE)
        }

        private val drawTask = object : Runnable {
            override fun run() {
                if (!visible) return
                drawFrame()
                val power = getSystemService(POWER_SERVICE) as PowerManager
                val saver = power.isPowerSaveMode
                val thermal = if (Build.VERSION.SDK_INT >= 29)
                    power.currentThermalStatus else 0
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
                loadImage()
                setupVideo()
                handler.post(drawTask)
            } else {
                releaseVideo()
                bitmap?.recycle()
                bitmap = null
                dots = emptyList()
                loadedUri = null
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder, format: Int, width: Int, height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            cachedWidth = 0
            if (visible) drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawTask)
            releaseVideo()
            super.onSurfaceDestroyed(holder)
        }

        private fun loadImage() {
            val uriString = prefs.getString("image_uri", null)
            if (uriString == loadedUri) return
            bitmap?.recycle()
            bitmap = null
            dots = emptyList()
            loadedUri = uriString
            if (uriString == null) return

            try {
                val decoded = contentResolver.openInputStream(Uri.parse(uriString))
                    ?.use { BitmapFactory.decodeStream(it) } ?: return
                val maxSide = 1400
                val scale = minOf(
                    1f,
                    maxSide.toFloat() / maxOf(decoded.width, decoded.height)
                )
                bitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1),
                        true
                    ).also { if (it !== decoded) decoded.recycle() }
                } else decoded
            } catch (_: Exception) {
                bitmap = null
            }
        }

        private fun setupVideo() {
            val uriString = prefs.getString("video_uri", null) ?: return
            if (player != null) return
            try {
                val p = MediaPlayer()
                p.setDataSource(this@JarvisWallpaperService, Uri.parse(uriString))
                p.isLooping = true
                p.setSurface(surfaceHolder.surface)
                p.setOnPreparedListener { if (visible) it.start() }
                p.setOnErrorListener { _, _, _ ->
                    releaseVideo()
                    true
                }
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

        // Dot positions and spacing are calculated once and never move.
        private fun buildFixedGrid(width: Int, height: Int) {
            val image = bitmap ?: run { dots = emptyList(); return }
            if (width == cachedWidth && height == cachedHeight && dots.isNotEmpty()) return

            cachedWidth = width
            cachedHeight = height
            val density = resources.displayMetrics.density
            val spacing = 7f * density
            val radius = 1.45f * density
            val scale = max(width.toFloat() / image.width, height.toFloat() / image.height)
            val drawnW = image.width * scale
            val drawnH = image.height * scale
            val left = (width - drawnW) / 2f
            val top = (height - drawnH) / 2f
            val result = ArrayList<MatrixDot>()

            var y = spacing / 2f
            while (y < height) {
                var x = spacing / 2f
                while (x < width) {
                    val sx = ((x - left) / scale).toInt()
                    val sy = ((y - top) / scale).toInt()
                    if (sx in 0 until image.width && sy in 0 until image.height) {
                        val pixel = image.getPixel(sx, sy)
                        val a = Color.alpha(pixel)
                        val r = Color.red(pixel)
                        val g = Color.green(pixel)
                        val b = Color.blue(pixel)
                        val lum = (r * 0.2126f + g * 0.7152f + b * 0.0722f)
                        if (a > 30 && lum > 13f) {
                            val strength = ((lum / 255f) * 225f + 25f)
                                .toInt().coerceIn(25, 250)
                            // Purple-white highlights with a subtle cyan contribution.
                            result.add(
                                MatrixDot(
                                    x, y, strength,
                                    (r * 0.72f + 190 * 0.28f).toInt().coerceIn(0,255),
                                    (g * 0.55f + 145 * 0.45f).toInt().coerceIn(0,255),
                                    (b * 0.70f + 255 * 0.30f).toInt().coerceIn(0,255)
                                )
                            )
                        }
                    }
                    x += spacing
                }
                y += spacing
            }
            dots = result
        }

        private fun drawFrame() {
            if (!visible || (videoMode && player?.isPlaying == true)) return
            val canvas = try { surfaceHolder.lockCanvas() }
                catch (_: Exception) { null } ?: return

            try {
                val w = canvas.width
                val h = canvas.height
                canvas.drawColor(Color.BLACK)

                if (bitmap == null) loadImage()
                if (dots.isEmpty() || w != cachedWidth || h != cachedHeight) {
                    buildFixedGrid(w, h)
                }

                if (dots.isEmpty()) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(190, 145, 255)
                        textAlign = Paint.Align.CENTER
                        textSize = 16f * resources.displayMetrics.density
                    }
                    canvas.drawText(
                        "SELECT A PORTRAIT IN JARVIS",
                        w / 2f, h / 2f, p
                    )
                } else {
                    val saver = (getSystemService(POWER_SERVICE) as PowerManager)
                        .isPowerSaveMode
                    val phase = frame++ * 0.045f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        maskFilter = BlurMaskFilter(
                            5f * resources.displayMetrics.density,
                            BlurMaskFilter.Blur.NORMAL
                        )
                    }
                    val radius = 1.45f * resources.displayMetrics.density

                    for (dot in dots) {
                        // Only brightness changes; grid coordinates and dot size stay fixed.
                        val pulse = if (saver) 1f else
                            0.88f + 0.12f * sin(phase + dot.x * 0.002f + dot.y * 0.001f)
                        val alpha = (dot.alpha * pulse).toInt().coerceIn(0, 255)
                        paint.color = Color.argb(
                            alpha, dot.red, dot.green, dot.blue
                        )
                        canvas.drawCircle(dot.x, dot.y, radius, paint)

                        if (!saver && alpha > 175) {
                            glow.color = Color.argb(
                                (alpha * 0.20f).toInt(),
                                dot.red, dot.green, dot.blue
                            )
                            canvas.drawCircle(dot.x, dot.y, radius, glow)
                        }
                    }
                }
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(drawTask)
            releaseVideo()
            bitmap?.recycle()
            bitmap = null
            super.onDestroy()
        }
    }
}
