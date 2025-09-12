package com.example.alarmchatapp

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
            // Retrieve what this worker needs (adjust keys to your inputs if any)
            val alarmId = inputData.getInt("alarm_id", 0)
            val alarmTitle = inputData.getString("alarm_title") ?: "Alarm"
            val triggerMillis = inputData.getLong("trigger_at", 0L)
            val recurringDays = inputData.getIntArray("recurring_days")?.toList() // Calendar constants

            if (alarmId == 0 || triggerMillis <= 0L) {
                Log.w("MyAlarmSetWorker", "Missing required inputs; id=$alarmId trigger=$triggerMillis")
                return Result.success()
            }

            val cal = Calendar.getInstance().apply { timeInMillis = triggerMillis }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)

            val next: Long = when {
                // Daily rule if 7 days provided
                recurringDays?.size == 7 -> {
                    val candidate = Calendar.getInstance().apply {
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                    }
                    candidate.timeInMillis
                }
                // Weekly rule when specific weekdays provided
                !recurringDays.isNullOrEmpty() -> {
                    AlarmHelper.computeNextAmongDays(hour, minute, recurringDays)
                }
                // One-time
                else -> triggerMillis
            }

            // Set one exact, user-visible alarm via AlarmClockInfo
            AlarmHelper.scheduleAlarmClockPublic(
                context = applicationContext,
                label = alarmTitle,
                triggerAt = next,
                alarmId = alarmId
            )
            Log.d("MyAlarmSetWorker", "Alarm scheduled '$alarmTitle' at $next (id=$alarmId)")
            Result.success()
        } catch (e: Exception) {
            Log.e("MyAlarmSetWorker", "Failed to schedule", e)
            Result.failure()
        }
    }
}