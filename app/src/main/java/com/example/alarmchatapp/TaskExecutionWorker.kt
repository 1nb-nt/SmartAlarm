package com.example.alarmchatapp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmchatapp.utils.AlarmHelper
import java.util.*
import java.util.concurrent.TimeUnit

class TaskExecutionWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("TaskExecutionWorker", "Worker starting: checking for due tasks.")
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val taskDao = db.scheduledTaskDao()
            val dueTasks = taskDao.getTasksDue(System.currentTimeMillis())

            if (dueTasks.isEmpty()) {
                Log.d("TaskExecutionWorker", "No tasks are due.")
                return Result.success()
            }

            dueTasks.forEach { task ->
                try {
                    Log.d("TaskExecutionWorker", "Processing task: ${task.description}")

                    if (task.isRecurring) {
                        val nextExecutionTime = task.executionTimeMillis + TimeUnit.DAYS.toMillis(1)
                        if (task.endDateMillis != null) {
                            // Ranged Task Logic
                            if (nextExecutionTime <= task.endDateMillis) {
                                val updatedTask = task.copy(executionTimeMillis = nextExecutionTime)
                                taskDao.update(updatedTask)
                                // **FIX**: Call the correct function with the correct arguments
                                AlarmHelper.scheduleInAppAlarm(applicationContext, task.description, nextExecutionTime)
                                Log.d("TaskExecutionWorker", "Ranged task '${task.description}' rescheduled as in-app alarm.")
                            } else {
                                taskDao.deleteTask(task)
                                Log.d("TaskExecutionWorker", "Ranged task '${task.description}' has completed.")
                            }
                        } else {
                            // Infinite Daily Task Logic
                            val updatedTask = task.copy(executionTimeMillis = nextExecutionTime)
                            taskDao.update(updatedTask)
                            Log.d("TaskExecutionWorker", "Infinite daily task '${task.description}' rescheduled.")
                        }
                    } else {
                        // One-Time Task Logic
                        taskDao.deleteTask(task)
                        Log.d("TaskExecutionWorker", "One-time task '${task.description}' completed.")
                    }
                } catch (e: Exception) {
                    Log.e("TaskExecutionWorker", "Failed to process task id: ${task.id}", e)
                }
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e("TaskExecutionWorker", "Worker execution failed", e)
            return Result.failure()
        }
    }
}
