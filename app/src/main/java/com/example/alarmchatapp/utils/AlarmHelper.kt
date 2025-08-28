package com.example.alarmchatapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.example.alarmchatapp.AlarmReceiver
import java.util.*

object AlarmHelper {

    // Set alarm in the device's default Clock app with optional recurrence days
    fun scheduleWeeklyAlarms(
        context: Context,
        label: String,
        hour: Int,
        minute: Int,
        selectedDays: List<Int>?, // Should match Calendar.DAY_OF_WEEK, ex: Monday=2
        baseAlarmId: Int
    ) {
        val times = getNextAlarmTimes(hour, minute, selectedDays)
        for ((idx, time) in times.withIndex()) {
            val alarmIdForDay = baseAlarmId + idx
            scheduleInAppAlarm(context, "$label (${getDayNameByCalendar(selectedDays?.getOrNull(idx) ?: 1)})", time, alarmIdForDay)
        }
    }

    fun scheduleSingleAlarm(context: Context, label: String, executionTime: Long, alarmId: Int) {
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
        Toast.makeText(context, "One-time alarm scheduled: $label at ${Date(executionTime)}", Toast.LENGTH_LONG).show()
    }


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

// This function assumes selectedDays uses Calendar.DAY_OF_WEEK (1=Sunday, ..., 7=Saturday)
fun getNextAlarmTimes(hour: Int, minute: Int, selectedDays: List<Int>?): List<Long> {
    val times = mutableListOf<Long>()
    if (selectedDays.isNullOrEmpty()) return times
    val now = Calendar.getInstance()
    for (dayOfWeek in selectedDays) {
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (alarmTime.timeInMillis <= now.timeInMillis) {
            alarmTime.add(Calendar.WEEK_OF_YEAR, 1)
        }
        times.add(alarmTime.timeInMillis)
    }
    return times
}


// Helper: Calendar.DAY_OF_WEEK is 1=Sunday ... 7=Saturday
fun getDayNameByCalendar(calendarDay: Int): String {
    return listOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")[
        (if (calendarDay in 1..7) calendarDay else 1) - 1
    ]
}
