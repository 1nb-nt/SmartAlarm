package com.example.alarmchatapp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmchatapp.utils.AlarmHelper

class TaskExecutionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("TaskExecutionWorker", "Checking due alarms and rescheduling")
        try {
            // Get database reference (adjust to your singleton, e.g., getDatabase or getInstance)
            val db = AppDatabase.getDatabase(applicationContext)
            // You must implement getDueAlarms in your AlarmDao:
            // @Query("SELECT * FROM alarms WHERE triggerTimeMillis <= :now")
            // fun getDueAlarms(now: Long): List<Alarm>
            val alarms = db.alarmDao().getAll()

            if (alarms.isEmpty()) return Result.success()

            alarms.forEach { alarm ->
                if (alarm.isRecurring) {
                    val cal = java.util.Calendar.getInstance().apply {
                        timeInMillis = alarm.triggerTimeMillis
                        add(java.util.Calendar.WEEK_OF_YEAR, 1)
                    }
                    val newTime = cal.timeInMillis
                    val updatedAlarm = alarm.copy(triggerTimeMillis = newTime)
                    db.alarmDao().update(updatedAlarm)
                    AlarmHelper.scheduleSingleAlarm(
                        applicationContext,
                        updatedAlarm.message,
                        newTime,
                        updatedAlarm.id
                    )
                } else {
                    db.alarmDao().delete(alarm)
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("TaskExecutionWorker", "Error in doWork: ${e.message}")
            return Result.failure()
        }
    }
}
