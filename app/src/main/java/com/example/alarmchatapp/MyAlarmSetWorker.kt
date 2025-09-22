/*package com.example.alarmchatapp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmchatapp.utils.AlarmHelper
import java.util.Calendar

class MyAlarmSetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val alarmId = inputData.getInt("alarm_id", 0)
            val alarmTitle = inputData.getString("alarm_title") ?: "Alarm"
            val triggerMillis = inputData.getLong("trigger_at", 0L)
            val recurringDays: List<Int>? = inputData.getIntArray("recurring_days")?.toList()

            if (alarmId == 0 || triggerMillis <= 0L) {
                Log.w("MyAlarmSetWorker", "Missing required inputs; id=$alarmId trigger=$triggerMillis")
                return Result.success()
            }

            val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)

            if (!recurringDays.isNullOrEmpty()) {
                // Weekly recurrence via system Clock app
                AlarmHelper.scheduleWeeklyInClock(
                    context = applicationContext,
                    label = alarmTitle,
                    hour = hour,
                    minute = minute,
                    days = recurringDays
                )
                Log.d(
                    "MyAlarmSetWorker",
                    "Weekly alarm scheduled '$alarmTitle' at $hour:$minute on $recurringDays (id=$alarmId)"
                )
            } else {
                // One-time via system Clock app
                AlarmHelper.scheduleAlarmClockPublic(
                    context = applicationContext,
                    label = alarmTitle,
                    triggerAt = triggerMillis,
                    alarmId = alarmId
                )
                Log.d(
                    "MyAlarmSetWorker",
                    "One-time alarm scheduled '$alarmTitle' at $triggerMillis (id=$alarmId)"
                )
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("MyAlarmSetWorker", "Failed to schedule alarm", e)
            Result.retry()
        }
    }
}
*/