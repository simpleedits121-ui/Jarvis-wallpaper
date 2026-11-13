package com.example.jarviswallpaper

import android.app.*
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import java.util.Calendar

class MainActivity : Activity() {
    private val prefs by lazy {
        getSharedPreferences("jarvis_wallpaper", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 8100)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF05030B.toInt())
        }

        fun label(s: String, size: Float = 15f) = TextView(this).apply {
            text = s
            textSize = size
            setTextColor(0xFFF2E9FF.toInt())
            setPadding(0, 9, 0, 9)
        }
        fun button(s: String, action: () -> Unit) = Button(this).apply {
            text = s
            minHeight = (48 * resources.displayMetrics.density).toInt()
            setOnClickListener { action() }
        }
        fun pick(type: String, request: Int) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                this.type = type
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, request)
        }
        fun openWallpaperPicker() {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    android.content.ComponentName(this@MainActivity, JarvisWallpaperService::class.java))
            })
        }

        layout.addView(label("JARVIS WALLPAPER", 25f))
        layout.addView(label("CYBERPUNK DOT MATRIX • VOLUME VISUALIZER • VIDEO ALARM"))
        layout.addView(button("CHOOSE CHARACTER IMAGE") { pick("image/*", 7002) })
        layout.addView(button("CHOOSE VIDEO WALLPAPER") { pick("video/*", 7001) })
        layout.addView(button("PREVIEW / APPLY WALLPAPER") { openWallpaperPicker() })

        layout.addView(label("SYSTEM-WIDE VOLUME EFFECT", 19f))
        layout.addView(label("A glowing dot equalizer appears whenever you press the phone's volume buttons, over other apps. Android requires you to enable the optional accessibility service and display-over-apps permission."))
        layout.addView(button("ENABLE SYSTEM-WIDE VOLUME DOTS") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Allow display over other apps, then tap Enable again.", Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, "Open Installed apps / Downloaded apps → JARVIS Volume Dots → Enable.", Toast.LENGTH_LONG).show()
            }
        })

        layout.addView(label("VIDEO ALARM", 19f))
        layout.addView(button("CHOOSE VIDEO FOR ALARM") { pick("video/*", 7003) })
        layout.addView(button("SET JARVIS ALARM TIME") { chooseAlarm() })
        layout.addView(button("CLEAR VIDEO ALARM") {
            prefs.edit().remove("alarm_video_uri").apply()
            Toast.makeText(this, "Video alarm cleared. Voice alarm will be used.", Toast.LENGTH_SHORT).show()
        })

        layout.addView(label("APP CONTROLS", 19f))
        layout.addView(button("RESET WALLPAPER TO DEFAULT") {
            AlertDialog.Builder(this)
                .setTitle("Reset JARVIS Wallpaper?")
                .setMessage("Clear the selected wallpaper media and restore the system wallpaper.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("RESET") { _, _ ->
                    prefs.edit().remove("image_uri").remove("video_uri").apply()
                    try {
                        WallpaperManager.getInstance(this).clear()
                        Toast.makeText(this, "Wallpaper reset.", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                        Toast.makeText(this, "Media cleared. Set wallpaper from Home settings.", Toast.LENGTH_LONG).show()
                    }
                }.show()
        })
        layout.addView(button("BATTERY / APP SETTINGS") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        layout.addView(label("Video wallpaper uses the current dot-matrix renderer. Video alarm plays your selected clip at alarm time. Volume dots require manual Android accessibility approval."))
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    @Deprecated("Platform document picker callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {}
        when (requestCode) {
            7001 -> {
                prefs.edit().putString("video_uri", uri.toString()).remove("image_uri").apply()
                Toast.makeText(this, "Video selected for wallpaper.", Toast.LENGTH_SHORT).show()
            }
            7002 -> {
                prefs.edit().putString("image_uri", uri.toString()).remove("video_uri").apply()
                Toast.makeText(this, "Image selected.", Toast.LENGTH_SHORT).show()
            }
            7003 -> {
                prefs.edit().putString("alarm_video_uri", uri.toString()).apply()
                Toast.makeText(this, "Video alarm clip saved.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun chooseAlarm() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val fireAt = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val manager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Allow exact alarms, then set the alarm again.", Toast.LENGTH_LONG).show()
                return@TimePickerDialog
            }
            AlarmScheduler.schedule(this, fireAt.timeInMillis)
            Toast.makeText(this, "JARVIS alarm set for %02d:%02d".format(hour, minute), Toast.LENGTH_LONG).show()
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
    }
}
