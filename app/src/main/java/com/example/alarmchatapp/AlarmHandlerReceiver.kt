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
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_PACKAGE_REPLACED
        ) {
            // Only handle restore actions
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
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = alarm.triggerTimeMillis
                    }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val minute = cal.get(Calendar.MINUTE)

                    if (alarm.isRecurring) {
                        val nextTrigger = when {
                            alarm.recurringDays?.size == 7 -> {
                                Calendar.getInstance().apply {
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                    set(Calendar.HOUR_OF_DAY, hour)
                                    set(Calendar.MINUTE, minute)
                                    if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
                                }.timeInMillis
                            }
                            !alarm.recurringDays.isNullOrEmpty() -> {
                                AlarmHelper.computeNextAmongDays(hour, minute, alarm.recurringDays!!)
                            }
                            else -> alarm.triggerTimeMillis // fallback
                        }

                        AlarmHelper.scheduleAlarmClockPublic(
                            context = context,
                            label = alarm.message,
                            triggerAt = nextTrigger,
                            alarmId = alarm.id
                        )
                        restored++
                        Log.d("AlarmHandlerReceiver", "Rescheduled recurring ${alarm.message} -> $nextTrigger")
                    } else {
                        val trigger = if (alarm.triggerTimeMillis > now) {
                            alarm.triggerTimeMillis
                        } else {
                            val tomorrow = Calendar.getInstance().apply {
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                add(Calendar.DAY_OF_YEAR, 1)
                            }.timeInMillis
                            // Persist tomorrow so UI aligns
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
                        Log.d("AlarmHandlerReceiver", "Rescheduled one-time ${alarm.message} -> $trigger")
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
