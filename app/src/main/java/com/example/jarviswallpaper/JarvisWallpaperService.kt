package com.example.jarviswallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.max
import kotlin.math.min

private data class MatrixDot(
    val x: Float, val y: Float, val alpha: Int,
    val red: Int, val green: Int, val blue: Int
)

class JarvisWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = MatrixEngine()

    inner class MatrixEngine : Engine() {
        private val main = Handler(Looper.getMainLooper())
        private val prefs by lazy { getSharedPreferences("jarvis_wallpaper", MODE_PRIVATE) }
        private var visible = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var grid = emptyList<Pair<Float, Float>>()
        @Volatile private var dots: List<MatrixDot> = emptyList()
        private var imageUri: String? = null
        private var videoUri: String? = null
        private var decoderThread: HandlerThread? = null
        private var decoderHandler: Handler? = null
        private var retriever: MediaMetadataRetriever? = null
        private var durationUs = 0L
        private var positionUs = 0L
        private var decoding = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (!visible) {
                stopVideo()
            } else {
                loadSelectedMedia()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            buildGrid()
            loadSelectedMedia()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            stopVideo()
            super.onSurfaceDestroyed(holder)
        }

        private fun buildGrid() {
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return
            // Wider spacing means fewer draw operations and lower thermal load.
            val spacing = 10f * resources.displayMetrics.density
            val points = ArrayList<Pair<Float, Float>>()
            var y = spacing / 2f
            while (y < surfaceHeight) {
                var x = spacing / 2f
                while (x < surfaceWidth) {
                    points.add(x to y)
                    x += spacing
                }
                y += spacing
            }
            grid = points
        }

        private fun loadSelectedMedia() {
            if (!visible || surfaceWidth <= 0 || surfaceHeight <= 0) return
            val selectedVideo = prefs.getString("video_uri", null)
            if (!selectedVideo.isNullOrBlank()) {
                if (videoUri != selectedVideo || retriever == null) startVideo(selectedVideo)
                return
            }
            stopVideo()
            val selectedImage = prefs.getString("image_uri", null)
            if (selectedImage != imageUri || dots.isEmpty()) {
                imageUri = selectedImage
                dots = emptyList()
                if (selectedImage != null) {
                    try {
                        val bitmap = contentResolver.openInputStream(Uri.parse(selectedImage))
                            ?.use { BitmapFactory.decodeStream(it) }
                        if (bitmap != null) {
                            val scaled = downscale(bitmap, 480)
                            dots = sample(scaled)
                            if (scaled !== bitmap) bitmap.recycle()
                            scaled.recycle()
                        }
                    } catch (_: Exception) {
                        dots = emptyList()
                    }
                }
            }
            drawCurrentFrame()
        }

        private fun downscale(source: Bitmap, maxWidth: Int): Bitmap {
            if (source.width <= maxWidth) return source
            val factor = maxWidth.toFloat() / source.width
            return Bitmap.createScaledBitmap(
                source, maxWidth, (source.height * factor).toInt().coerceAtLeast(1), true
            )
        }

        private fun sample(source: Bitmap): List<MatrixDot> {
            if (grid.isEmpty()) return emptyList()
            val w = source.width
            val h = source.height
            val scale = max(surfaceWidth.toFloat() / w, surfaceHeight.toFloat() / h)
            val left = (surfaceWidth - w * scale) / 2f
            val top = (surfaceHeight - h * scale) / 2f
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, 0, 0, w, h)
            val result = ArrayList<MatrixDot>(grid.size)
            for ((x, y) in grid) {
                val sx = ((x - left) / scale).toInt()
                val sy = ((y - top) / scale).toInt()
                if (sx !in 0 until w || sy !in 0 until h) {
                    result.add(MatrixDot(x, y, 0, 190, 145, 255))
                    continue
                }
                val c = pixels[sy * w + sx]
                val a = Color.alpha(c)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val lum = r * 0.2126f + g * 0.7152f + b * 0.0722f
                val alpha = if (a < 24 || lum < 12f) 0 else lum.toInt().coerceIn(0, 255)
                result.add(MatrixDot(
                    x, y, alpha,
                    (r * 0.70f + 190 * 0.30f).toInt().coerceIn(0, 255),
                    (g * 0.55f + 145 * 0.45f).toInt().coerceIn(0, 255),
                    (b * 0.70f + 255 * 0.30f).toInt().coerceIn(0, 255)
                ))
            }
            return result
        }

        private fun startVideo(uriString: String) {
            stopVideo()
            videoUri = uriString
            dots = emptyList()
            val thread = HandlerThread("JarvisLowPowerVideo")
            thread.start()
            decoderThread = thread
            val worker = Handler(thread.looper)
            decoderHandler = worker
            worker.post {
                try {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(this@JarvisWallpaperService, Uri.parse(uriString))
                    val durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    if (durationMs <= 0L) {
                        r.release()
                        return@post
                    }
                    retriever = r
                    durationUs = durationMs * 1000L
                    positionUs = 0L
                    decodeOneFrame()
                } catch (_: Exception) {
                    main.post { drawCurrentFrame() }
                }
            }
        }

        // Low-power fallback: 4 sampled frames/second, low-resolution source, no continuous redraw.
        private fun decodeOneFrame() {
            val worker = decoderHandler ?: return
            if (!visible || decoding) return
            decoding = true
            worker.post {
                try {
                    val r = retriever ?: return@post
                    if (positionUs >= durationUs) positionUs = 0L
                    val frame = r.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (frame != null) {
                        val small = downscale(frame, 360)
                        val nextDots = sample(small)
                        if (small !== frame) frame.recycle()
                        small.recycle()
                        main.post {
                            if (visible) {
                                dots = nextDots
                                drawCurrentFrame()
                            }
                        }
                    }
                    positionUs += 250_000L
                } catch (_: Exception) {
                    // Keep last good frame.
                } finally {
                    decoding = false
                    if (visible) {
                        val power = getSystemService(POWER_SERVICE) as android.os.PowerManager
                        val saver = power.isPowerSaveMode
                        val thermal = if (android.os.Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else 0
                        val delay = when {
                            saver || thermal >= 3 -> 1000L
                            thermal >= 2 -> 600L
                            else -> 250L
                        }
                        worker.postDelayed({ decodeOneFrame() }, delay)
                    }
                }
            }
        }

        private fun stopVideo() {
            decoderHandler?.removeCallbacksAndMessages(null)
            decoderHandler = null
            try { retriever?.release() } catch (_: Exception) {}
            retriever = null
            decoderThread?.quitSafely()
            decoderThread = null
            videoUri = null
            durationUs = 0L
            positionUs = 0L
            decoding = false
        }

        private fun drawCurrentFrame() {
            if (!visible || surfaceWidth <= 0 || surfaceHeight <= 0) return
            val canvas = try { surfaceHolder.lockCanvas() } catch (_: Exception) { null } ?: return
            try {
                canvas.drawColor(Color.BLACK)
                val current = dots
                if (current.isEmpty()) {
                    paint.color = Color.rgb(190, 145, 255)
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = 14f * resources.displayMetrics.density
                    val msg = if (!prefs.getString("video_uri", null).isNullOrBlank())
                        "LOADING DOT MATRIX VIDEO" else "SELECT MEDIA IN JARVIS"
                    canvas.drawText(msg, surfaceWidth / 2f, surfaceHeight / 2f, paint)
                } else {
                    paint.style = Paint.Style.FILL
                    paint.maskFilter = null
                    val radius = 1.25f * resources.displayMetrics.density
                    for (d in current) {
                        if (d.alpha <= 0) continue
                        paint.color = Color.argb(d.alpha, d.red, d.green, d.blue)
                        canvas.drawCircle(d.x, d.y, radius, paint)
                    }
                }
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        override fun onDestroy() {
            visible = false
            stopVideo()
            main.removeCallbacksAndMessages(null)
            super.onDestroy()
        }
    }
}
