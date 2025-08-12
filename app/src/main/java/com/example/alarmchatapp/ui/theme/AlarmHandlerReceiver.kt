package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class AlarmHandlerReceiver : BroadcastReceiver() {

    companion object {
        // This unique name is essential for managing and checking the task.
        const val UNIQUE_WORK_NAME = "MyManagedDailyWork"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmHandlerReceiver", "Alarm has been received. Starting WorkManager task.")

        // Prepare data for your worker if needed
        val workData = workDataOf("TASK_DATA" to "Triggered by managed alarm")

        // Create the WorkManager task request
        val workRequest = OneTimeWorkRequestBuilder<My24HourWorker>() // Assumes My24HourWorker.kt exists
            .setInputData(workData)
            .build()

        // Enqueue the task as unique work. This prevents duplicates.
        // If a pending task with this name exists, KEEP it and ignore the new request.
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
}
