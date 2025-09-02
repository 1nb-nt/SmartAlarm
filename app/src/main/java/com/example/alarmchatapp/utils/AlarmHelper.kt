package com.example.alarmchatapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.alarmchatapp.AlarmHandlerReceiver
import com.example.alarmchatapp.AlarmReceiver
import java.util.*

object AlarmHelper {

    // Schedule multiple weekly days alarms
    fun scheduleWeeklyAlarms(
        context: Context,
        label: String,
        hour: Int,
        minute: Int,
        selectedDays: List<Int>,
        baseAlarmId: Int
    ) {
        selectedDays.forEachIndexed { index, dayOfWeek ->
            val nextTime = getNextAlarmTimeForDay(hour, minute, dayOfWeek)

            // Unique ID per day to distinguish alarms, e.g., baseId * 10 + index
            val uniqueId = baseAlarmId * 10 + index

            // Append day name to label for clarity
            val dayName = getDayNameByCalendar(dayOfWeek)
            val alarmLabel = "$label ($dayName)"

            // Schedule the alarm at the calculated time
            scheduleAlarm(context, alarmLabel, nextTime, uniqueId)
        }
    }

    // Helper function to calculate next trigger time in millis for given day and time
    fun getNextAlarmTimeForDay(hour: Int, minute: Int, dayOfWeek: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }
        val now = Calendar.getInstance()
        // If the time is before now, add 7 days to schedule in next week
        if (cal.before(now)) {
            cal.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    // Helper to get day name from Calendar constant
    fun getDayNameByCalendar(day: Int): String {
        return when (day) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> "Unknown"
        }
    }

    fun scheduleSingleAlarm(context: Context, label: String, triggerTime: Long, alarmId: Int) {
        scheduleAlarm(context, label, triggerTime, alarmId)
    }


    private fun scheduleAlarm(context: Context, label: String, triggerTimeMillis: Long, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(context, "Allow exact alarm permission", Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("AlarmHelper", "Cannot open exact alarm settings", e)
                }
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            }
            Toast.makeText(context, "Alarm scheduled: $label at ${Date(triggerTimeMillis)}", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Log.e("AlarmHelper", "Cannot schedule exact alarm", e)
            Toast.makeText(context, "Unable to schedule alarm. Please enable exact alarms in settings.", Toast.LENGTH_LONG).show()
        }
    }

    // Extend this to schedule single alarm with reminders
    fun scheduleAlarmWithReminders(context: Context, label: String, triggerTimeMillis: Long, alarmId: Int) {
        scheduleAlarm(context, label, triggerTimeMillis, alarmId)

        val reminder30 = triggerTimeMillis - 30 * 60 * 1000L
        val reminder10 = triggerTimeMillis - 10 * 60 * 1000L

        if (reminder30 > System.currentTimeMillis()) {
            scheduleAlarm(context, "$label (30 min reminder)", reminder30, alarmId * 10 + 1)
        }
        if (reminder10 > System.currentTimeMillis()) {
            scheduleAlarm(context, "$label (10 min reminder)", reminder10, alarmId * 10 + 2)
        }
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

fun getNextAlarmTimes(hour: Int, minute: Int, selectedDays: List<Int>): List<Long> {
    val times = mutableListOf<Long>()
    val now = Calendar.getInstance()
    for (day in selectedDays) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now.timeInMillis) {
            cal.add(Calendar.WEEK_OF_YEAR, 1)
        }
        times.add(cal.timeInMillis)
    }
    return times
}

fun getDayNameByCalendar(day: Int): String {
    val days = listOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
    return days.getOrElse(day - 1) { "Unknown" }
}
