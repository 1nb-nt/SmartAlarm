package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarmchatapp.utils.AlarmHelper
import com.example.alarmchatapp.workers.ClockPreSchedulerWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RescheduleReceiver", "onReceive action=${intent.action}")

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val alarms = dao.getAll()
                val now = System.currentTimeMillis()

                var restored = 0
                alarms.forEach { a ->
                    try {
                        if (a.isRecurring && !a.recurringDays.isNullOrEmpty()) {
                            val cal = Calendar.getInstance().apply { timeInMillis = a.triggerTimeMillis }
                            val hour = cal.get(Calendar.HOUR_OF_DAY)
                            val minute = cal.get(Calendar.MINUTE)

                            AlarmHelper.scheduleWeeklyInClock(
                                context = context,
                                label = a.message,
                                hour = hour,
                                minute = minute,
                                days = a.recurringDays!!,
                                alarmId = a.id,
                                skipUi = true,
                                showToast = false
                            )
                            restored++
                        } else if (a.triggerTimeMillis > now) {
                            ClockPreSchedulerWorker.enqueue(
                                context = context,
                                label = a.message,
                                triggerAt = a.triggerTimeMillis,
                                isRecurring = false,
                                recurringDays = null,
                                alarmId = a.id
                            )
                            restored++
                        }
                    } catch (inner: Exception) {
                        Log.w("RescheduleReceiver", "Failed to restore alarm id=${a.id}", inner)
                    }
                }
                Log.d("RescheduleReceiver", "Restored $restored alarms.")
            } catch (e: Exception) {
                Log.e("RescheduleReceiver", "Failed to restore alarms", e)
            } finally {
                DateTimeChangeReceiver.scheduleExactMidnight(context)
                pending.finish()
            }
        }
    }
}
