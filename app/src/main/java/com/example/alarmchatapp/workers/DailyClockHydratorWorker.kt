package com.example.alarmchatapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.utils.AlarmHelper

class DailyClockHydratorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val alarms = AppDatabase.getDatabase(applicationContext).alarmDao().getAllAlarms()
        AlarmHelper.scheduleInAppAlarms(applicationContext, alarms)
        Log.d("DailyClockHydratorWorker", "Rehydrated ${alarms.size} alarms")
        return Result.success()
    }
}
