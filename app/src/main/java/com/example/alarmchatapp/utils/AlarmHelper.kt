package com.example.alarmchatapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.alarmchatapp.AlarmReceiver
import java.util.*

object AlarmHelper {

    // Use this for INFINITE daily alarms in the clock app
    fun setAlarmInClockApp(context: Context, label: String, hour: Int, minute: Int, isRecurring: Boolean = false) {
        // This function does not require the new permission, as it uses a standard intent.
        // Its logic can remain the same.
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (isRecurring) {
                    val days = arrayListOf(
                        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
                    )
                    putExtra(AlarmClock.EXTRA_DAYS, days)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                val recurringText = if (isRecurring) " (daily)" else ""
                Toast.makeText(context, "Alarm set in clock app: $label at $hour:$minute$recurringText", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // **MODIFIED**: Use this for PRECISE one-time or ranged alarms
    fun scheduleInAppAlarm(context: Context, label: String, executionTimeMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // **FIX**: Check for the exact alarm permission before setting the alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Permission is not granted, guide the user to the settings screen
                Toast.makeText(context, "Permission needed to set precise alarms.", Toast.LENGTH_LONG).show()
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
                    context.startActivity(it)
                }
                return // Stop execution until permission is granted
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_LABEL", label)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            executionTimeMillis.toInt(), // Unique ID for each alarm
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // This line will now only be called if permission is granted
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            executionTimeMillis,
            pendingIntent
        )

        Toast.makeText(context, "App alarm scheduled for your target date/time!", Toast.LENGTH_LONG).show()
    }
}
