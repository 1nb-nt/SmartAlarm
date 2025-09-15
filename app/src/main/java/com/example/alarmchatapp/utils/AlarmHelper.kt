package com.example.alarmchatapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.example.alarmchatapp.AlarmActivity
import com.example.alarmchatapp.AlarmReceiver
import java.util.Calendar
import java.util.Date

object AlarmHelper {

    // Schedules a user-visible exact alarm with AlarmClockInfo
    fun scheduleAlarmClockPublic(context: Context, label: String, triggerAt: Long, alarmId: Int,initialNote: String? = null) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            Toast.makeText(
                context,
                "Allow exact alarms in settings to schedule.",
                Toast.LENGTH_LONG
            ).show()
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {
            }
            return
        }

        val fire = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_LABEL", label)
            putExtra("ALARM_ID", alarmId)
            putExtra("INITIAL_NOTE", initialNote)
        }
        val op = PendingIntent.getBroadcast(
            context, alarmId, fire,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val show = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alarm_message", label)
            putExtra("ALARM_ID", alarmId)
            putExtra("INITIAL_NOTE", initialNote)
        }
        val showPi = PendingIntent.getActivity(
            context, alarmId, show,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setAlarmClock is exact and behaves properly under Doze for user alarms
        val info = AlarmManager.AlarmClockInfo(triggerAt, showPi)
        am.setAlarmClock(info, op)
        Toast.makeText(context, "Alarm scheduled: $label at ${Date(triggerAt)}", Toast.LENGTH_SHORT)
            .show()
    }

    // Keep single-shot wrapper
    fun scheduleSingleAlarm(
        context: Context,
        label: String,
        triggerAtMillis: Long,
        requestCode: Int
    ) {
        scheduleAlarmClockPublic(context, label, triggerAtMillis, requestCode)
    }

    // Compute the NEXT occurrence among provided weekdays (Calendar.SUNDAY..SATURDAY) at hour:minute.
    fun computeNextAmongDays(hour: Int, minute: Int, days: List<Int>): Long {
        var best: Long? = null
        for (dow in days) {
            val candidate = getNextAlarmTimeForDay(hour, minute, dow)
            if (best == null || candidate < best) best = candidate
        }
        return best!!
    }

    // Compute the next time for a specific weekday at hour:minute.
    fun getNextAlarmTimeForDay(hour: Int, minute: Int, dayOfWeek: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }
        if (cal.before(Calendar.getInstance())) cal.add(Calendar.WEEK_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun getDayNameByCalendar(day: Int) = when (day) {
        Calendar.SUNDAY -> "Sunday"
        Calendar.MONDAY -> "Monday"
        Calendar.TUESDAY -> "Tuesday"
        Calendar.WEDNESDAY -> "Wednesday"
        Calendar.THURSDAY -> "Thursday"
        Calendar.FRIDAY -> "Friday"
        Calendar.SATURDAY -> "Saturday"
        else -> "Unknown"
    }

    fun cancelScheduledAlarm(context: Context, alarmId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, alarmId, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
