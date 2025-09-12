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

class AlarmHandlerReceiver : BroadcastReceiver() {


    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action &&
            Intent.ACTION_LOCKED_BOOT_COMPLETED != intent.action &&
            Intent.ACTION_PACKAGE_REPLACED != intent.action) {
            return // Only handle restore actions
        } //[1]

        // Keep the process alive while running suspend Room calls
        val pending = goAsync() // required when doing async work in a receiver
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val alarms = dao.getAll() // suspend DAO call to load persisted alarms
                val now = System.currentTimeMillis()

                var restored = 0
                alarms.forEach { alarm ->
                    val cal = Calendar.getInstance().apply { timeInMillis = alarm.triggerTimeMillis }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val minute = cal.get(Calendar.MINUTE)

                    if (alarm.isRecurring) {
                        // Daily: represented by 7 days; schedule only the closest next day and let AlarmReceiver chain forever
                        val nextTrigger = when {
                            alarm.recurringDays?.size == 7 -> {
                                val candidate = Calendar.getInstance().apply {
                                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                                    if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
                                }
                                candidate.timeInMillis
                            }
                            !alarm.recurringDays.isNullOrEmpty() -> {
                                // Weekly: compute the nearest among selected weekdays
                                AlarmHelper.computeNextAmongDays(hour, minute, alarm.recurringDays!!)
                            }
                            else -> {
                                // Safety: treat as one-time if no rule was stored
                                alarm.triggerTimeMillis
                            }
                        }

                        AlarmHelper.scheduleAlarmClockPublic(
                            context = context,
                            label = alarm.message,
                            triggerAt = nextTrigger,
                            alarmId = alarm.id
                        )
                        restored++
                        Log.d("AlarmHandlerReceiver", "Rescheduled recurring '${alarm.message}' -> $nextTrigger")
                    } else {
                        // One-time: if still in the future, re-arm; if missed, move to tomorrow at same time
                        val trigger = if (alarm.triggerTimeMillis > now) {
                            alarm.triggerTimeMillis
                        } else {
                            val tomorrow = Calendar.getInstance().apply {
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                                add(Calendar.DAY_OF_YEAR, 1)
                            }.timeInMillis
                            // persist the new time so the UI and future restores are correct
                            dao.update(alarm.copy(triggerTimeMillis = tomorrow))
                            tomorrow
                        }

                        AlarmHelper.scheduleAlarmClockPublic(
                            context = context,
                            label = alarm.message,
                            triggerAt = trigger,
                            alarmId = alarm.id
                        )
                        restored++
                        Log.d("AlarmHandlerReceiver", "Rescheduled one-time '${alarm.message}' -> $trigger")
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
}