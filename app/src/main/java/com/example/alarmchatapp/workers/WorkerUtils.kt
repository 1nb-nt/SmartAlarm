package com.example.alarmchatapp.workers

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import java.util.concurrent.TimeUnit

object WorkerUtils {
    fun scheduleWorker(context: Context, label: String, timeMillis: Long) {
        val delay = timeMillis - System.currentTimeMillis()
        val data = Data.Builder().putString("alarm_label", label).build()
        val workRequest = OneTimeWorkRequestBuilder<ClockPreSchedulerWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
