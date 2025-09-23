package com.example.alarmchatapp.utils

import android.content.Context
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.workers.ClockPreSchedulerWorker

object AlarmHelper {

    // Public helper for weekly alarms: accept List<Int>, convert internally
    fun scheduleWeeklyInClock(
        context: Context,
        label: String,
        hour: Int,
        minute: Int,
        days: List<Int>
    ) {
        SystemAlarmScheduler.setWeeklyAlarm(
            context = context,
            label = label,
            hour = hour,
            minute = minute,
            days = days.toIntArray(),          // fix: convert List<Int> -> IntArray
            vibrate = true,
            ringtone = null,
            skipUi = true
        )
    }

    // One-shot exact alarm placed immediately via SystemAlarmScheduler
    fun scheduleExactAlarm(
        context: Context,
        label: String,
        epochMillis: Long,
        alarmId: Int
    ) {
        SystemAlarmScheduler.setOneTimeAlarm(
            context = context,
            label = "$label · #$alarmId",
            triggerAtMillis = epochMillis,
            vibrate = true,
            ringtone = null,
            skipUi = true
        )
    }

    // Compute next occurrence among selected weekdays at hour:minute
    fun computeNextAmongDays(hour: Int, minute: Int, daysOfWeek: List<Int>): Long {
        val now = java.util.Calendar.getInstance()
        val base = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        for (offset in 0..7) {
            val c = (base.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, offset)
            }
            val dow = c.get(java.util.Calendar.DAY_OF_WEEK)
            if (daysOfWeek.contains(dow) && c.timeInMillis > now.timeInMillis) {
                return c.timeInMillis
            }
        }
        return base.timeInMillis
    }

    // Persist alarms and schedule appropriately
    fun scheduleInAppAlarms(context: Context, alarms: List<Alarm>) {
        val dao = AppDatabase.getDatabase(context).alarmDao()
        alarms.forEach { alarm ->
            val rowId = dao.insert(alarm).toInt()
            if (alarm.isRecurring && !alarm.recurringDays.isNullOrEmpty()) {
                // For recurring, derive hour/minute from triggerTimeMillis
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = alarm.triggerTimeMillis
                }
                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val minute = cal.get(java.util.Calendar.MINUTE)
                scheduleWeeklyInClock(
                    context = context,
                    label = alarm.message,
                    hour = hour,
                    minute = minute,
                    days = alarm.recurringDays!!
                )
            } else {
                // For one-shots: place near-time via prescheduler, and optionally immediate exact
                // If immediate Clock entry is desired now, call scheduleExactAlarm(...)
                ClockPreSchedulerWorker.enqueue(
                    context = context,
                    label = "${alarm.message} · #$rowId",
                    triggerAt = alarm.triggerTimeMillis,
                    isRecurring = false,
                    recurringDays = null,
                    alarmId = rowId
                )
            }
        }
    }
}
