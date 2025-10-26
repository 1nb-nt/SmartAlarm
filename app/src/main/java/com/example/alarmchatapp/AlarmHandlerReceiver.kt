package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max

class AlarmHandlerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_PACKAGE_REPLACED
        ) {
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val alarms = dao.getAll()
                val now = System.currentTimeMillis()
                var restored = 0

                alarms.forEach { alarm ->
                    // INTERVAL-BASED restore (grid-aligned)
                    if (alarm.isIntervalBased && alarm.intervalMinutes != null && alarm.intervalMinutes > 0) {
                        val intervalMs = alarm.intervalMinutes.toLong() * 60_000L
                        if (alarm.expiryTimeMillis != null && now >= alarm.expiryTimeMillis!!) {
                            dao.delete(alarm)
                            return@forEach
                        }
                        val base = alarm.triggerTimeMillis
                        val steps = max(
                            1L,
                            ceil((now - base).coerceAtLeast(0L).toDouble() / intervalMs.toDouble()).toLong()
                        )
                        val nextTrigger = base + steps * intervalMs
                        dao.update(alarm.copy(triggerTimeMillis = nextTrigger))
                        AlarmHelper.scheduleAlarmClockPublic(
                            context, alarm.message, nextTrigger, alarm.id, alarm.initialNote ?: ""
                        )
                        restored++
                        Log.d("AlarmHandlerReceiver", "Restored interval id=${alarm.id} -> $nextTrigger")
                        return@forEach
                    }

                    val cal = Calendar.getInstance().apply { timeInMillis = alarm.triggerTimeMillis }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val minute = cal.get(Calendar.MINUTE)

                    if (alarm.isRecurring) {
                        // Fallback for single-day weekly: if days list is empty, assume the fired weekday
                        val effectiveDays: List<Int>? = when {
                            alarm.recurringDays?.size == 7 -> null // daily
                            alarm.recurringDays.isNullOrEmpty() -> listOf(cal.get(Calendar.DAY_OF_WEEK))
                            else -> alarm.recurringDays
                        }

                        val nextTrigger = when {
                            // Daily
                            effectiveDays == null -> {
                                Calendar.getInstance().apply {
                                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                                    if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
                                }.timeInMillis
                            }
                            // One or many weekdays (includes the single-day case)
                            else -> computeNextAmongDaysLocal(hour, minute, effectiveDays)
                        }

                        AlarmHelper.scheduleAlarmClockPublic(
                            context = context,
                            label = alarm.message,
                            triggerAt = nextTrigger,
                            alarmId = alarm.id,
                            initialNote = alarm.initialNote ?: ""
                        )
                        restored++
                        Log.d("AlarmHandlerReceiver", "Restored weekly id=${alarm.id} -> $nextTrigger")
                    } else {
                        // One-time: if past, roll to tomorrow same time for UI consistency
                        val trigger = if (alarm.triggerTimeMillis > now) {
                            alarm.triggerTimeMillis
                        } else {
                            val tomorrow = Calendar.getInstance().apply {
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                                add(Calendar.DAY_OF_YEAR, 1)
                            }.timeInMillis
                            dao.update(alarm.copy(triggerTimeMillis = tomorrow))
                            tomorrow
                        }
                        AlarmHelper.scheduleAlarmClockPublic(
                            context = context,
                            label = alarm.message,
                            triggerAt = trigger,
                            alarmId = alarm.id,
                            initialNote = alarm.initialNote ?: ""
                        )
                        restored++
                        Log.d("AlarmHandlerReceiver", "Restored one-time id=${alarm.id} -> $trigger")
                    }
                }

                Log.d("AlarmHandlerReceiver", "Restore complete: $restored alarms rescheduled")
            } catch (e: Exception) {
                Log.e("AlarmHandlerReceiver", "Restore failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun computeNextAmongDaysLocal(hour: Int, minute: Int, days: List<Int>): Long {
        val now = System.currentTimeMillis()
        var best: Long? = null
        for (day in days) {
            val c = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                set(Calendar.DAY_OF_WEEK, day)
            }
            while (c.timeInMillis <= now) c.add(Calendar.WEEK_OF_YEAR, 1)
            if (best == null || c.timeInMillis < best!!) best = c.timeInMillis
        }
        return best ?: (now + 24 * 60 * 60 * 1000L)
    }
}
