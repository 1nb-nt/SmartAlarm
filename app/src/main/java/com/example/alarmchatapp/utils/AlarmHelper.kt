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
        selectedDays: List<Int>? // Sunday=0 ... Saturday=6
    ) {
        val times = getNextAlarmTimes(hour, minute, selectedDays)
        for ((idx, time) in times.withIndex()) {
            scheduleInAppAlarm(context, "${label} (${getDayName(idx)})", time, 1000 + idx)
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

fun getNextAlarmTimes(hour: Int, minute: Int, selectedDays: List<Int>?): List<Long> {
    val times = mutableListOf<Long>()
    if (selectedDays.isNullOrEmpty()) return times
    for (dayIndex in selectedDays) {  // Iterate over selected day values
        val c = Calendar.getInstance()
        val todayDayOfWeek = c.get(Calendar.DAY_OF_WEEK) // SUNDAY=1 ... SATURDAY=7
        val dayOfWeek = ((dayIndex + 1) % 7) + 1 // Map 0-6 -> 1-7

        var daysUntil = dayOfWeek - todayDayOfWeek
        // If today/alarm time has passed, schedule for next week
        if (daysUntil < 0 || (daysUntil == 0 && (
                    c.get(Calendar.HOUR_OF_DAY) > hour || (
                            c.get(Calendar.HOUR_OF_DAY) == hour && c.get(Calendar.MINUTE) >= minute
                            )
                    ))) {
            daysUntil += 7
        }
        c.add(Calendar.DAY_OF_YEAR, daysUntil)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        times.add(c.timeInMillis)
    }
    return times
}


// Helper to get weekday name from index
fun getDayName(index: Int): String {
    return listOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")[index]
}
