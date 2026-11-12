\
package com.example.jarviswallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.graphics.*
import android.media.MediaMetadataRetriever
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
        private val drawHandler = Handler(Looper.getMainLooper())
        private var videoThread: HandlerThread? = null
        private var videoHandler: Handler? = null
        private var retriever: MediaMetadataRetriever? = null
        private var videoDurationUs = 0L
        private var videoPositionUs = 0L
        private var visible = false
        private var pulseFrame = 0
        private var bitmap: Bitmap? = null
        private var loadedUri: String? = null
        @Volatile private var dots: List<MatrixDot> = emptyList()
        @Volatile private var gridPositions: List<Pair<Float, Float>> = emptyList()
        private var cachedWidth = 0
        private var cachedHeight = 0
        private var videoUri: String? = null
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
                    else -> 33L
                }
                drawHandler.postDelayed(this, delay)
            }
        }

        override fun onVisibilityChanged(visibleNow: Boolean) {
            visible = visibleNow
            drawHandler.removeCallbacks(drawTask)
            if (visible) {
                loadSelectedMedia()
                drawHandler.post(drawTask)
            } else {
                stopVideoProcessing()
                bitmap?.recycle()
                bitmap = null
                dots = emptyList()
                loadedUri = null
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            cachedWidth = 0
            gridPositions = emptyList()
            dots = emptyList()
            if (visible) {
                loadSelectedMedia()
                drawFrame()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            drawHandler.removeCallbacks(drawTask)
            stopVideoProcessing()
            super.onSurfaceDestroyed(holder)
        }

        private fun loadSelectedMedia() {
            val selectedVideo = prefs.getString("video_uri", null)
            if (!selectedVideo.isNullOrBlank()) {
                if (videoUri != selectedVideo || retriever == null) startVideoProcessing(selectedVideo)
                return
            }
            stopVideoProcessing()
            loadImage()
            if (bitmap != null) buildImageDots()
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
                val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
                bitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        decoded, (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1), true
                    ).also { if (it !== decoded) decoded.recycle() }
                } else decoded
            } catch (_: Exception) {
                bitmap = null
            }
        }

        // This grid is created once for the surface. Video frames only replace each dot's colour/brightness.
        private fun ensureFixedGrid(width: Int, height: Int) {
            if (width == cachedWidth && height == cachedHeight && gridPositions.isNotEmpty()) return
            cachedWidth = width
            cachedHeight = height
            val spacing = 7f * resources.displayMetrics.density
            val positions = ArrayList<Pair<Float, Float>>()
            var y = spacing / 2f
            while (y < height) {
                var x = spacing / 2f
                while (x < width) {
                    positions.add(x to y)
                    x += spacing
                }
                y += spacing
            }
            gridPositions = positions
        }

        private fun buildImageDots() {
            val image = bitmap ?: return
            val w = cachedWidth
            val h = cachedHeight
            if (w <= 0 || h <= 0) return
            ensureFixedGrid(w, h)
            dots = sampleBitmapToDots(image)
        }

        private fun sampleBitmapToDots(source: Bitmap): List<MatrixDot> {
            val w = cachedWidth
            val h = cachedHeight
            if (w <= 0 || h <= 0) return emptyList()
            val scale = max(w.toFloat() / source.width, h.toFloat() / source.height)
            val drawnW = source.width * scale
            val drawnH = source.height * scale
            val left = (w - drawnW) / 2f
            val top = (h - drawnH) / 2f
            val result = ArrayList<MatrixDot>(gridPositions.size)
            for ((x, y) in gridPositions) {
                val sx = ((x - left) / scale).toInt()
                val sy = ((y - top) / scale).toInt()
                if (sx !in 0 until source.width || sy !in 0 until source.height) {
                    result.add(MatrixDot(x, y, 0, 190, 145, 255))
                    continue
                }
                val pixel = source.getPixel(sx, sy)
                val a = Color.alpha(pixel)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = r * 0.2126f + g * 0.7152f + b * 0.0722f
                val alpha = if (a <= 30 || lum <= 9f) 0
                    else ((lum / 255f) * 255f).toInt().coerceIn(0, 255)
                result.add(MatrixDot(
                    x, y, alpha,
                    (r * 0.72f + 190 * 0.28f).toInt().coerceIn(0, 255),
                    (g * 0.55f + 145 * 0.45f).toInt().coerceIn(0, 255),
                    (b * 0.70f + 255 * 0.30f).toInt().coerceIn(0, 255)
                ))
            }
            return result
        }

        private fun startVideoProcessing(uriString: String) {
            stopVideoProcessing()
            videoUri = uriString
            dots = emptyList()
            val thread = HandlerThread("JarvisVideoDotDecoder")
            thread.start()
            videoThread = thread
            val worker = Handler(thread.looper)
            videoHandler = worker
            worker.post {
                try {
                    val decoder = MediaMetadataRetriever()
                    decoder.setDataSource(this@JarvisWallpaperService, Uri.parse(uriString))
                    val durationMs = decoder.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L
                    if (durationMs <= 0L) {
                        decoder.release()
                        return@post
                    }
                    retriever = decoder
                    videoDurationUs = durationMs * 1000L
                    videoPositionUs = 0L
                    decodeNextVideoFrame()
                } catch (_: Exception) {
                    stopVideoProcessing()
                }
            }
        }

        // Decode at 8 frames/second off the UI thread. Each frame is sampled onto the exact same grid.
        private fun decodeNextVideoFrame() {
            val worker = videoHandler ?: return
            if (!visible) return
            worker.post {
                try {
                    val decoder = retriever ?: return@post
                    if (videoPositionUs >= videoDurationUs) videoPositionUs = 0L
                    val frameBitmap = decoder.getFrameAtTime(
                        videoPositionUs, MediaMetadataRetriever.OPTION_CLOSEST
                    )
                    if (frameBitmap != null) {
                        val scaled = if (frameBitmap.width > 720 || frameBitmap.height > 720) {
                            val factor = min(720f / frameBitmap.width, 720f / frameBitmap.height)
                            Bitmap.createScaledBitmap(
                                frameBitmap,
                                (frameBitmap.width * factor).toInt().coerceAtLeast(1),
                                (frameBitmap.height * factor).toInt().coerceAtLeast(1),
                                true
                            ).also { if (it !== frameBitmap) frameBitmap.recycle() }
                        } else frameBitmap
                        val newDots = sampleBitmapToDots(scaled)
                        if (scaled !== frameBitmap) scaled.recycle() else frameBitmap.recycle()
                        if (visible) dots = newDots
                    }
                    videoPositionUs += 125_000L
                    worker.postDelayed({ decodeNextVideoFrame() }, 125L)
                } catch (_: Exception) {
                    // Keep the last good frame rather than crashing the wallpaper service.
                    worker.postDelayed({ decodeNextVideoFrame() }, 250L)
                }
            }
        }

        private fun stopVideoProcessing() {
            videoHandler?.removeCallbacksAndMessages(null)
            videoHandler = null
            try { retriever?.release() } catch (_: Exception) {}
            retriever = null
            videoThread?.quitSafely()
            videoThread = null
            videoUri = null
            videoDurationUs = 0L
            videoPositionUs = 0L
        }

        private fun drawFrame() {
            if (!visible) return
            val canvas = try { surfaceHolder.lockCanvas() } catch (_: Exception) { null } ?: return
            try {
                val w = canvas.width
                val h = canvas.height
                canvas.drawColor(Color.BLACK)
                ensureFixedGrid(w, h)
                if (prefs.getString("video_uri", null).isNullOrBlank() && bitmap == null) {
                    loadImage()
                    if (bitmap != null) buildImageDots()
                }
                val currentDots = dots
                if (currentDots.isEmpty()) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(190, 145, 255)
                        textAlign = Paint.Align.CENTER
                        textSize = 15f * resources.displayMetrics.density
                    }
                    val message = if (!prefs.getString("video_uri", null).isNullOrBlank())
                        "PREPARING VIDEO DOT MATRIX" else "SELECT MEDIA IN JARVIS"
                    canvas.drawText(message, w / 2f, h / 2f, p)
                } else {
                    val saver = (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode
                    val phase = pulseFrame++ * 0.045f
                    val radius = 1.45f * resources.displayMetrics.density
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        maskFilter = BlurMaskFilter(4f * resources.displayMetrics.density, BlurMaskFilter.Blur.NORMAL)
                    }
                    for (dot in currentDots) {
                        val pulse = if (saver) 1f else
                            0.94f + 0.06f * sin(phase + dot.x * 0.002f + dot.y * 0.001f)
                        val alpha = (dot.alpha * pulse).toInt().coerceIn(0, 255)
                        if (alpha <= 0) continue
                        paint.color = Color.argb(alpha, dot.red, dot.green, dot.blue)
                        canvas.drawCircle(dot.x, dot.y, radius, paint)
                        if (!saver && alpha > 175) {
                            glow.color = Color.argb((alpha * 0.16f).toInt(), dot.red, dot.green, dot.blue)
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
            drawHandler.removeCallbacks(drawTask)
            stopVideoProcessing()
            bitmap?.recycle()
            bitmap = null
            super.onDestroy()
        }
    }
}
