package com.example.jarviswallpaper

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Locale

class AlarmActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28); setBackgroundColor(0xFF080D1B.toInt())
        }
        root.addView(TextView(this).apply {
            text = "J.A.R.V.I.S"; textSize = 34f; gravity = Gravity.CENTER
            setTextColor(0xFFB987FF.toInt())
        })
        root.addView(TextView(this).apply {
            text = "GOOD MORNING.\nIT'S TIME TO WAKE UP."; textSize = 22f
            gravity = Gravity.CENTER; setTextColor(0xFFEAF6FF.toInt())
        })
        root.addView(Button(this).apply { text = "DISMISS"; setOnClickListener { tts?.stop(); finish() } })
        setContentView(root)
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.speak("Good morning. It's time to wake up. Your day is waiting.", TextToSpeech.QUEUE_FLUSH, null, "jarvis_wake")
        }
    }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}
