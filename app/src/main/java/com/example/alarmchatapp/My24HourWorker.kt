package com.example.alarmchatapp

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class My24HourWorker(appContext: Context, workerParams: WorkerParameters): Worker(appContext, workerParams) {

    /**
     * This is the method that will be executed in the background.
     */
    override fun doWork(): Result {
        // You can get input data passed from the ChatScreen here
        val taskData = inputData.getString("TASK_DATA") ?: "No data"
        Log.d("My24HourWorker", "Work is starting... Data: $taskData")

        // --- PLACE YOUR BACKGROUND TASK LOGIC HERE ---
        // For example, you could:
        // - Make a network request to fetch updated data.
        // - Sync local data with a remote server.
        // - Show a notification that the daily task is complete.
        // ---------------------------------------------

        Log.d("My24HourWorker", "Work finished successfully.")

        // Indicate whether the work finished successfully or failed
        return Result.success()
    }
}
