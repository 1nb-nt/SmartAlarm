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

object AlarmHelper {

    // Map short day names from API to Calendar constants
    val dayShortToCal = mapOf(
        "Sun" to Calendar.SUNDAY,
        "Mon" to Calendar.MONDAY,
        "Tue" to Calendar.TUESDAY,
        "Wed" to Calendar.WEDNESDAY,
        "Thu" to Calendar.THURSDAY,
        "Fri" to Calendar.FRIDAY,
        "Sat" to Calendar.SATURDAY
    )

    fun scheduleAlarmClockPublic(
        context: Context,
        label: String,
        triggerAt: Long,
        alarmId: Int,
        initialNote: String = ""
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            Toast.makeText(context, "Allow exact alarms in settings to schedule.", Toast.LENGTH_LONG).show()
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }

        // Full-screen activity affordance shown by AlarmClockInfo
        val showIntent = Intent(context, AlarmActivity::class.java).apply {
            action = "SHOW_ALARM_UI_$alarmId"
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_NOTE, initialNote)
        }
        val showPi = PendingIntent.getActivity(
            context, alarmId, showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutable()
        )

        // Broadcast that actually fires — unique action per id so cancel() matches exactly
        val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "FIRE_ALARM_$alarmId"
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_NOTE, initialNote)
        }
        val firePi = PendingIntent.getBroadcast(
            context, alarmId, fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableIfS()
        )

        val info = AlarmManager.AlarmClockInfo(triggerAt, showPi)
        am.setAlarmClock(info, firePi)
    }

    // Compute the nearest future trigger among selected weekdays at hour:minute in device timezone.
    fun computeNextAmongDays(hour: Int, minute: Int, days: List<Int>, nowMs: Long = System.currentTimeMillis()): Long {
        require(days.isNotEmpty()) { "days must not be empty" }

        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val base = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }

        val todayDow = now.get(Calendar.DAY_OF_WEEK)
        var bestDiff = Int.MAX_VALUE

        for (dow in days.distinct()) {
            var diff = dow - todayDow
            if (diff < 0 || (diff == 0 && base.timeInMillis <= now.timeInMillis)) {
                diff += 7
            }
            if (diff < bestDiff) bestDiff = diff
        }

        if (bestDiff == Int.MAX_VALUE) {
            base.add(Calendar.DAY_OF_YEAR, 1)
            return base.timeInMillis
        }

        base.add(Calendar.DAY_OF_YEAR, bestDiff)
        return base.timeInMillis
    }

    // New: helper to cancel a scheduled alarm by id (call this before deleting DB row)
    fun cancelAlarm(context: Context, alarmId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "FIRE_ALARM_$alarmId"
            putExtra(AlarmReceiver.EXTRA_ID, alarmId)
        }
        val firePi = PendingIntent.getBroadcast(
            context, alarmId, fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableIfS()
        )
        am.cancel(firePi)
    }

    private fun immutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun mutableIfS(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
}
