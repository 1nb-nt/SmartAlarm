package com.example.alarmchatapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.alarmchatapp.AlarmReceiver

object AlarmHelper {

    fun scheduleInAppAlarm(context: Context, label: String, hour: Int, minute: Int, customDateMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // This intent will be broadcasted to our AlarmReceiver when the alarm time is reached.
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_LABEL", label)
        }

        // The PendingIntent wraps the intent and makes it executable by the system's AlarmManager.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            customDateMillis.toInt(), // A unique ID for each alarm is important.
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // Set the precise alarm. This requires the SCHEDULE_EXACT_ALARM permission.
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                customDateMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Error handling is done in ChatScreen, but this prevents crashes here.
        }
    }
}
