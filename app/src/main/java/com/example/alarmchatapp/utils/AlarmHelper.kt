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

    fun scheduleAlarmClockPublic(
        context: Context,
        label: String,
        triggerAt: Long,
        alarmId: Int
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

        // Full-screen UI intent (for the status bar affordance shown by setAlarmClock)
        val showIntent = Intent(context, AlarmActivity::class.java).apply {
            action = "SHOW_ALARM_UI_$alarmId"
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_ID, alarmId)
        }
        val showPi = PendingIntent.getActivity(
            context,
            alarmId,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlagImmutable()
        )

        // Actual fire broadcast delivered at trigger time
// Actual fire broadcast delivered at trigger time
        val fireIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "FIRE_ALARM_$alarmId"
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_ID, alarmId)
        }
        val firePi = PendingIntent.getBroadcast(
            context,
            alarmId,
            fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingFlagMutableIfNeeded()
        )

        val info = AlarmManager.AlarmClockInfo(triggerAt, showPi)
        am.setAlarmClock(info, firePi)
    }

    fun computeNextAmongDays(hour: Int, minute: Int, days: List<Int>): Long {
        // days must be Calendar.DAY_OF_WEEK values (1..7)
        val now = Calendar.getInstance()
        val base = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }

        val todayDow = now.get(Calendar.DAY_OF_WEEK) // 1..7
        var bestDiff = Int.MAX_VALUE

        for (dow in days) {
            var diff = dow - todayDow
            // If today but the time already passed, roll 1 week
            if (diff < 0 || (diff == 0 && base.timeInMillis <= now.timeInMillis)) {
                diff += 7
            }
            if (diff < bestDiff) bestDiff = diff
        }

        if (bestDiff == Int.MAX_VALUE) {
            // fallback: next day
            base.add(Calendar.DAY_OF_YEAR, 1)
            return base.timeInMillis
        }

        base.add(Calendar.DAY_OF_YEAR, bestDiff)
        return base.timeInMillis
    }

    private fun pendingFlagImmutable(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun pendingFlagMutableIfNeeded(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    }
}
