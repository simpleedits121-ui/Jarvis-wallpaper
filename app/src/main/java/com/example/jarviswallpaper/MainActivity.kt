package com.example.jarviswallpaper

import android.app.Activity
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import java.util.Calendar

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("jarvis_wallpaper", Context.MODE_PRIVATE) }
    private var videoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoUri = prefs.getString("video_uri", null)?.let(Uri::parse)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(0xFF07111D.toInt())
        }
        fun label(s: String, size: Float = 15f) = TextView(this).apply {
            text = s; textSize = size; setTextColor(0xFFEAF6FF.toInt()); setPadding(0, 10, 0, 10)
        }
        fun button(s: String, action: () -> Unit) = Button(this).apply {
            text = s; setOnClickListener { action() }
        }
        layout.addView(label("JARVIS WALLPAPER", 24f))
        layout.addView(label("CYBERPUNK • LIVE MATRIX • VIDEO • ALARM"))
        layout.addView(button("SET ANIMATED DOT-MATRIX WALLPAPER") {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    android.content.ComponentName(this@MainActivity, JarvisWallpaperService::class.java))
            })
        })
        layout.addView(button("CHOOSE VIDEO WALLPAPER") {
            val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(pick, 7001)
        })
        layout.addView(button("PREVIEW / APPLY WALLPAPER") {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    android.content.ComponentName(this@MainActivity, JarvisWallpaperService::class.java))
            })
        })
        layout.addView(label("ALARM", 20f))
        layout.addView(label("Choose a time. Android may ask permission for exact alarms."))
        layout.addView(button("SET JARVIS VOICE ALARM") { chooseAlarm() })
        layout.addView(button("BATTERY / APP SETTINGS") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        })
        layout.addView(label("Video mode uses the video selected above. If no video is selected, the animated matrix is shown. Android battery saver or manufacturer background limits may pause wallpaper/alarm activity.", 12f))
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    @Deprecated("Use the platform document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
            videoUri = uri
            prefs.edit().putString("video_uri", uri.toString()).apply()
            Toast.makeText(this, "Video saved for wallpaper", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseAlarm() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val whenToFire = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val manager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Allow exact alarms, then set the alarm again.", Toast.LENGTH_LONG).show()
                return@TimePickerDialog
            }
            AlarmScheduler.schedule(this, whenToFire.timeInMillis)
            Toast.makeText(this, "JARVIS alarm set for %02d:%02d".format(hour, minute), Toast.LENGTH_LONG).show()
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
    }
}
