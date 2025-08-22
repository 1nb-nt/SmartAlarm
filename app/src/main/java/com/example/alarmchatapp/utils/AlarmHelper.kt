package com.example.alarmchatapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import com.example.alarmchatapp.AlarmReceiver
import java.util.*

object AlarmHelper {

    // Set alarm in the device's default Clock app with optional recurrence days
    fun setTimeInClockApp(context: Context, label: String, hour: Int, minute: Int, repeatDays: List<Int>? = null) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                repeatDays?.let {
                    if (it.isNotEmpty()) {
                        putExtra(AlarmClock.EXTRA_DAYS, ArrayList(it))
                    }
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                val recurringText = if (repeatDays != null && repeatDays.isNotEmpty()) " (recurring days)" else ""
                Toast.makeText(context, "Alarm set in clock app: $label at $hour:$minute$recurringText", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Schedule exact alarm in the app using AlarmManager with unique alarmId.
    fun scheduleInAppAlarm(context: Context, label: String, executionTime: Long, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(context, "Permission needed to schedule exact alarms.", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        }
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_LABEL", label)
            putExtra("ALARM_ID", alarmId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, executionTime, pendingIntent)
        Toast.makeText(context, "Alarm scheduled: $label at ${Date(executionTime)}", Toast.LENGTH_LONG).show()
    }

    // Cancel an existing scheduled alarm by its id
    fun cancelScheduledAlarm(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
