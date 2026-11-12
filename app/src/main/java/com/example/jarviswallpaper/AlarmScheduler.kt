package com.example.jarviswallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object AlarmScheduler {
    fun schedule(context: Context, atMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction("com.example.jarviswallpaper.ALARM")
        val pending = PendingIntent.getBroadcast(context, 4102, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (android.os.Build.VERSION.SDK_INT >= 23) alarm.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, pending), pending)
        else alarm.setExact(AlarmManager.RTC_WAKEUP, atMillis, pending)
    }
}
