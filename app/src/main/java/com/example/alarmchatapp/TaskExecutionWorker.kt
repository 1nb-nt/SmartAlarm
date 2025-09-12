package com.example.alarmchatapp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmchatapp.utils.AlarmHelper
import java.util.Calendar

class TaskExecutionWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {


    override suspend fun doWork(): Result {
        Log.d("TaskExecutionWorker", "Worker starting: checking due alarms.")
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val alarmDao = db.alarmDao()
            val now = System.currentTimeMillis()

            // Requires AlarmDao.getDue(now)
            val dueAlarms = alarmDao.getDue(now)
            if (dueAlarms.isEmpty()) {
                Log.d("TaskExecutionWorker", "No alarms are due.")
                return Result.success()
            }

            dueAlarms.forEach { alarm ->
                try {
                    if (alarm.isRecurring) {
                        val firedCal = Calendar.getInstance().apply { timeInMillis = alarm.triggerTimeMillis }
                        val hour = firedCal.get(Calendar.HOUR_OF_DAY)
                        val minute = firedCal.get(Calendar.MINUTE)

                        val next: Long = when {
                            alarm.recurringDays?.size == 7 -> {
                                Calendar.getInstance().apply {
                                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                                    add(Calendar.DAY_OF_YEAR, 1)
                                }.timeInMillis
                            }
                            !alarm.recurringDays.isNullOrEmpty() -> {
                                AlarmHelper.computeNextAmongDays(hour, minute, alarm.recurringDays!!)
                            }
                            else -> {
                                // No rule persisted; fallback to next day same time
                                Calendar.getInstance().apply {
                                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                                    add(Calendar.DAY_OF_YEAR, 1)
                                }.timeInMillis
                            }
                        }

                        val updated = alarm.copy(triggerTimeMillis = next)
                        alarmDao.update(updated) // suspend within coroutine
                        AlarmHelper.scheduleAlarmClockPublic(applicationContext, alarm.message, next, alarm.id)
                        Log.d("TaskExecutionWorker", "Recurring alarm '${alarm.message}' rescheduled to $next.")
                    } else {
                        // One-time: cleanup after firing
                        alarmDao.delete(alarm)
                        Log.d("TaskExecutionWorker", "One-time alarm '${alarm.message}' deleted after execution.")
                    }
                } catch (e: Exception) {
                    Log.e("TaskExecutionWorker", "Failed alarm id=${alarm.id}", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("TaskExecutionWorker", "Worker failed", e)
            Result.failure()
        }
    }

}