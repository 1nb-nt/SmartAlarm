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

        if (eventTimeMillis == -1L) {
            Log.e("MyAlarmSetWorker", "No event time provided for alarm.")
            return Result.failure()
        }

        val cal = Calendar.getInstance().apply { timeInMillis = eventTimeMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        return try {
            // 1. Set alarm in the real Clock app
            AlarmHelper.setAlarmInClockApp(
                applicationContext,
                alarmTitle,
                hour,
                minute,
                isRecurring = false
            )
            Log.d("MyAlarmSetWorker", "Alarm assigned in clock app for '$alarmTitle' at $hour:$minute on ${Date(eventTimeMillis)}.")

            // 2. Also schedule in-app alarm as fallback or for custom notification handling
            AlarmHelper.scheduleInAppAlarm(applicationContext, alarmTitle, eventTimeMillis)
            Log.d("MyAlarmSetWorker", "In-app alarm scheduled for '$alarmTitle' at ${Date(eventTimeMillis)}.")

            Result.success()
        } catch (e: Exception) {
            Log.e("MyAlarmSetWorker", "Failed to set alarm", e)
            Result.failure()
        }
    }
}
