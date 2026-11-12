package com.example.jarviswallpaper

import android.app.*
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
    private val prefs by lazy {
        getSharedPreferences("jarvis_wallpaper", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(0xFF05030B.toInt())
        }

        fun label(s: String, size: Float = 15f) =
            TextView(this).apply {
                text = s
                textSize = size
                setTextColor(0xFFF2E9FF.toInt())
                setPadding(0, 10, 0, 10)
            }

        fun button(s: String, action: () -> Unit) =
            Button(this).apply {
                text = s
                setOnClickListener { action() }
            }

        fun openWallpaperPicker() {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        android.content.ComponentName(
                            this@MainActivity,
                            JarvisWallpaperService::class.java
                        )
                    )
                }
            )
        }

        layout.addView(label("JARVIS WALLPAPER", 25f))
        layout.addView(label("FIXED DOT MATRIX • CYBERPUNK • VIDEO • ALARM"))

        layout.addView(button("CHOOSE CHARACTER IMAGE") {
            val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            startActivityForResult(pick, 7002)
        })

        layout.addView(button("CHOOSE VIDEO WALLPAPER") {
            val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            startActivityForResult(pick, 7001)
        })

        layout.addView(button("PREVIEW / APPLY WALLPAPER") {
            openWallpaperPicker()
        })

        layout.addView(button("RESET TO DEFAULT WALLPAPER") {
            AlertDialog.Builder(this)
                .setTitle("Reset JARVIS Wallpaper?")
                .setMessage(
                    "This clears the selected image and video and restores the phone's default wallpaper."
                )
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("RESET") { _, _ ->
                    prefs.edit().remove("image_uri").remove("video_uri").apply()
                    try {
                        WallpaperManager.getInstance(this).clear()
                        Toast.makeText(
                            this,
                            "Wallpaper reset to default",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Media cleared. Set your phone wallpaper from Home settings.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .show()
        })

        layout.addView(label("ALARM", 20f))
        layout.addView(button("SET JARVIS VOICE ALARM") { chooseAlarm() })
        layout.addView(button("BATTERY / APP SETTINGS") {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        })
        layout.addView(
            label(
                "Image mode converts your portrait into a fixed dot grid. Dot positions and spacing remain still; only brightness gently pulses. Video mode plays your selected video separately.",
                12f
            )
        )
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    @Deprecated("Platform document picker callback")
    override fun onActivityResult(
        requestCode: Int, resultCode: Int, data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {}

        when (requestCode) {
            7001 -> {
                prefs.edit()
                    .putString("video_uri", uri.toString())
                    .remove("image_uri")
                    .apply()
                Toast.makeText(this, "Video selected", Toast.LENGTH_SHORT).show()
            }
            7002 -> {
                prefs.edit()
                    .putString("image_uri", uri.toString())
                    .remove("video_uri")
                    .apply()
                Toast.makeText(
                    this,
                    "Image selected. Apply the wallpaper to see the dot effect.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun chooseAlarm() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                val whenToFire = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                val manager = getSystemService(ALARM_SERVICE) as AlarmManager
                if (
                    Build.VERSION.SDK_INT >= 31 &&
                    !manager.canScheduleExactAlarms()
                ) {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                    )
                    Toast.makeText(
                        this,
                        "Allow exact alarms, then set the alarm again.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@TimePickerDialog
                }
                AlarmScheduler.schedule(this, whenToFire.timeInMillis)
                Toast.makeText(
                    this,
                    "JARVIS alarm set for %02d:%02d".format(hour, minute),
                    Toast.LENGTH_LONG
                ).show()
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            false
        ).show()
    }
}
