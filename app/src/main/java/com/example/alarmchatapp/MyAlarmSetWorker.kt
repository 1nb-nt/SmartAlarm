package com.example.alarmchatapp

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.alarmchatapp.utils.AlarmHelper
import java.util.*

class MyAlarmSetWorker(private val context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        try {
            val title = inputData.getString("ALARM_TITLE") ?: "Alarm"
            val time = inputData.getLong("EVENT_TIME", -1L)
            val id = inputData.getInt("ALARM_ID", -1)

            if (time == -1L || id == -1) {
                Log.e("MyAlarmSetWorker", "Missing alarm info")
                return Result.failure()
            }

            val cal = Calendar.getInstance().apply {
                timeInMillis = time
            }
            AlarmHelper.scheduleRecurringAlarm(context, title, cal, listOf(cal.get(Calendar.DAY_OF_WEEK)), id)
            Log.d("MyAlarmSetWorker", "Alarm scheduled in worker")
            return Result.success()
        } catch (e: Exception) {
            Log.e("MyAlarmSetWorker", "Error scheduling alarm", e)
            return Result.failure()
        }
    }
}
