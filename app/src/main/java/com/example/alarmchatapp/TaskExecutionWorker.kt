package com.example.alarmchatapp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class TaskExecutionWorker(appContext: Context, workerParams: WorkerParameters): CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("TaskExecutionWorker", "Worker starting: checking for upcoming tasks.")
        val db = AppDatabase.getDatabase(applicationContext)

        // Define the time window: from now until 24 hours from now
        val startTime = System.currentTimeMillis()
        val endTime = startTime + TimeUnit.HOURS.toMillis(24)

        // Fetch tasks due in the next 24 hours
        val upcomingTasks = db.scheduledTaskDao().getTasksDue(startTime, endTime)

        if (upcomingTasks.isEmpty()) {
            Log.d("TaskExecutionWorker", "No tasks due in the next 24 hours.")
            return Result.success()
        }

        // Process each upcoming task
        upcomingTasks.forEach { task ->
            Log.d("TaskExecutionWorker", "Executing task: ${task.description}")

            // --- YOUR EXECUTION LOGIC HERE ---
            // For example, show a notification about the task.
            // For now, we will just log it.
            // ---------------------------------

            // After execution, delete the task so it doesn't run again
            db.scheduledTaskDao().deleteTask(task)
            Log.d("TaskExecutionWorker", "Task ${task.description} completed and deleted.")
        }

        return Result.success()
    }
}
