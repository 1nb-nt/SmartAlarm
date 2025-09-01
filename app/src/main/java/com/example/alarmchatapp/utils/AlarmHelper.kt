package com.example.alarmchatapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.AlarmReceiver
import com.example.alarmchatapp.ui.dayName
import java.util.*

object AlarmHelper {
    fun scheduleSingleAlarm(context: Context, label: String, triggerAtMillis: Long, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(context, "Please allow exact alarm permission", Toast.LENGTH_LONG).show()
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_LABEL", label)
        }
        val pendingIntent = PendingIntent.getBroadcast(context, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        Toast.makeText(context, "Alarm scheduled for ${Date(triggerAtMillis)}", Toast.LENGTH_LONG).show()
    }

    fun scheduleRecurringAlarm(context: Context, label: String, baseCalendar: Calendar, days: List<Int>, baseId: Int) {
        days.forEachIndexed { i, day ->
            val cal = baseCalendar.clone() as Calendar
            cal.set(Calendar.DAY_OF_WEEK, day)
            if (cal.timeInMillis <= System.currentTimeMillis())
                cal.add(Calendar.WEEK_OF_YEAR, 1)
            val alarmId = baseId * 10 + i
            scheduleSingleAlarm(context, "$label - ${dayName(day)}", cal.timeInMillis, alarmId)
        }
    }

    fun cancelScheduledAlarm(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.cancel(pendingIntent)
    }
}
