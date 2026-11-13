package com.example.jarviswallpaper

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import java.util.Locale

class AlarmActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var videoView: VideoView? = null
    private var hasVideo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
             View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        tts = TextToSpeech(this, this)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(5, 3, 14)) }
        val selectedAlarmVideo = getSharedPreferences("jarvis_wallpaper", MODE_PRIVATE)
            .getString("alarm_video_uri", null)

        if (!selectedAlarmVideo.isNullOrBlank()) {
            hasVideo = true
            videoView = VideoView(this).also { video ->
                root.addView(video, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
                video.setVideoURI(Uri.parse(selectedAlarmVideo))
                video.setOnPreparedListener { player ->
                    player.isLooping = true
                    player.setVolume(0.85f, 0.85f)
                    video.start()
                }
                video.setOnErrorListener { _, _, _ ->
                    hasVideo = false
                    speakFallback()
                    true
                }
            }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0x99080512.toInt())
        }
        panel.addView(TextView(this).apply {
            text = "J.A.R.V.I.S"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(0xFFCE9BFF.toInt())
        })
        panel.addView(TextView(this).apply {
            text = "GOOD MORNING.\\nIT'S TIME TO WAKE UP."
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(0xFFF4EDFF.toInt())
            setPadding(0, 12, 0, 18)
        })
        panel.addView(Button(this).apply {
            text = "DISMISS ALARM"
            setOnClickListener {
                videoView?.stopPlayback()
                tts?.stop()
                finish()
            }
        })
        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        root.addView(panel, panelParams)
        setContentView(root)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            if (!hasVideo) speakFallback()
        }
    }

    private fun speakFallback() {
        tts?.speak(
            "Good morning. It's time to wake up. Your day is waiting.",
            TextToSpeech.QUEUE_FLUSH, null, "jarvis_wake"
        )
    }

    override fun onDestroy() {
        videoView?.stopPlayback()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
