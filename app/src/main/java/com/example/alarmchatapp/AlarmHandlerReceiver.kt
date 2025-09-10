package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class AlarmHandlerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val alarms = dao.getAll()
                val now = System.currentTimeMillis()

                alarms.forEach { alarm ->
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = alarm.triggerTimeMillis
                    }
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    val minute = calendar.get(Calendar.MINUTE)

                    if (alarm.isRecurring) {
                        // ✅ Use stored recurringDays from DB, not hardcoded weekends
                        val days: List<Int> = alarm.recurringDays ?: listOf(
                            Calendar.MONDAY,
                            Calendar.TUESDAY,
                            Calendar.WEDNESDAY,
                            Calendar.THURSDAY,
                            Calendar.FRIDAY,
                            Calendar.SATURDAY,
                            Calendar.SUNDAY
                        )

                        AlarmHelper.scheduleWeeklyAlarms(
                            context = context,
                            label = alarm.message,   // was: message = alarm.message
                            hour = hour,
                            minute = minute,
                            selectedDays = days,
                            baseAlarmId = alarm.id
                        )


                        Log.d("AlarmHandlerReceiver", "Rescheduled recurring alarm '${alarm.message}' on $days")

                    } else {
                        // ✅ One-time alarms: if missed, reschedule for tomorrow
                        if (alarm.triggerTimeMillis > now) {
                            AlarmHelper.scheduleAlarmClockPublic(
                                context,
                                alarm.message,
                                alarm.triggerTimeMillis,
                                alarm.id
                            )
                        } else {
                            val tomorrow = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            AlarmHelper.scheduleAlarmClockPublic(
                                context,
                                alarm.message,
                                tomorrow.timeInMillis,
                                alarm.id
                            )

                            dao.update(alarm.copy(triggerTimeMillis = tomorrow.timeInMillis))
                            Log.d("AlarmHandlerReceiver", "Rescheduled one-time alarm '${alarm.message}' for tomorrow")
                        }
                    }
                }
            }
        }
    }
}
