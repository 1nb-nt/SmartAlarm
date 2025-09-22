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
                    // REPLACE the reschedule branch in TaskExecutionWorker.doWork()

                    if (alarm.isRecurring) {
                        val firedCal = Calendar.getInstance().apply { timeInMillis = alarm.triggerTimeMillis }
                        val hour = firedCal.get(Calendar.HOUR_OF_DAY)
                        val minute = firedCal.get(Calendar.MINUTE)

                        if (!alarm.recurringDays.isNullOrEmpty()) {
                            AlarmHelper.scheduleWeeklyInClock(
                                context = applicationContext,
                                label = alarm.message,
                                hour = hour,
                                minute = minute,
                                days = alarm.recurringDays!!,
                                alarmId = alarm.id,          // REQUIRED
                                skipUi = true,
                                showToast = false
                            )
                        } else {
                            // Daily fallback: schedule tomorrow same time via prescheduler for safety
                            val next = Calendar.getInstance().apply {
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                                add(Calendar.DAY_OF_YEAR, 1)
                            }.timeInMillis
                            com.example.alarmchatapp.workers.ClockPreSchedulerWorker.enqueue(
                                context = applicationContext,
                                label = alarm.message,
                                triggerAt = next,
                                isRecurring = false,
                                recurringDays = null,
                                alarmId = alarm.id
                            )
                        }

                        // Persist the next occurrence for UI consistency
                        val updatedNext = AlarmHelper.computeNextAmongDays(
                            hour = hour,
                            minute = minute,
                            days = alarm.recurringDays ?: emptyList()
                        )
                        val updated = alarm.copy(
                            triggerTimeMillis = if (!alarm.recurringDays.isNullOrEmpty()) updatedNext else alarm.triggerTimeMillis
                        )
                        alarmDao.update(updated)
                    } else {
                        // One-shot fired: clean up the row
                        alarmDao.delete(alarm)
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