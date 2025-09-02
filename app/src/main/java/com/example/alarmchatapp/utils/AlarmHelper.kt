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

    // Schedule multiple weekly days alarms
    fun scheduleWeeklyAlarms(
        context: Context,
        label: String,
        hour: Int,
        minute: Int,
        selectedDays: List<Int>,
        baseAlarmId: Int
    ) {
        val times = getNextAlarmTimes(hour, minute, selectedDays)
        for ((index, time) in times.withIndex()) {
            val id = baseAlarmId * 10 + index
            val dayLabel = "${label} (${getDayNameByCalendar(selectedDays.getOrNull(index) ?: Calendar.SUNDAY)})"
            scheduleAlarm(context, dayLabel, time, id)
        }
    }

    fun scheduleSingleAlarm(context: Context, label: String, triggerTime: Long, alarmId: Int) {
        scheduleAlarm(context, label, triggerTime, alarmId)
    }

    private fun scheduleAlarm(context: Context, label: String, time: Long, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(context, "Allow exact alarm permission", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
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

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent)
        Toast.makeText(context, "Alarm scheduled: $label at ${Date(time)}", Toast.LENGTH_LONG).show()
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
