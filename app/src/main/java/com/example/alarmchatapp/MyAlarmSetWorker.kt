package com.example.alarmchatapp

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.alarmchatapp.utils.AlarmHelper
import java.util.*

class MyAlarmSetWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val alarmTitle = inputData.getString("ALARM_TITLE") ?: "Scheduled Alarm"
        val eventTimeMillis = inputData.getLong("EVENT_TIME", -1L)
        val alarmId = inputData.getInt("ALARM_ID", 0)

        if (eventTimeMillis == -1L) {
            Log.e("MyAlarmSetWorker", "No event time provided for alarm.")
            return Result.failure()
        }

        val cal = Calendar.getInstance().apply { timeInMillis = eventTimeMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        return try {
            // Set alarm in default Clock app
            AlarmHelper.scheduleWeeklyAlarms(
                applicationContext,
                alarmTitle,
                hour,
                minute,
                null
            )
            Log.d("MyAlarmSetWorker", "Alarm set in clock app at $hour:$minute.")

            // Also schedule exact alarm in the app as fallback
            AlarmHelper.scheduleInAppAlarm(applicationContext, alarmTitle, eventTimeMillis, alarmId)
            Log.d("MyAlarmSetWorker", "App alarm scheduled at ${Date(eventTimeMillis)}.")

            Result.success()
        } catch (e: Exception) {
            Log.e("MyAlarmSetWorker", "Failed to set alarm", e)
            Result.failure()
        }
    }
}
