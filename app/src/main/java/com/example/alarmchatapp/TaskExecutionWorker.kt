package com.example.alarchatmapp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.utils.AlarmHelper
import java.util.*
import java.util.concurrent.TimeUnit

class TaskExecutionWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("TaskExecutionWorker", "Worker starting: checking due tasks.")

        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val taskDao = db.scheduledTaskDao()
            val now = System.currentTimeMillis()
            val dueTasks = taskDao.getTasksDue(now)

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
                            // Ranged recurring task with end date
                            if (nextExecutionTime <= task.endDateMillis) {
                                val updatedTask = task.copy(executionTimeMillis = nextExecutionTime)
                                taskDao.update(updatedTask)
                                AlarmHelper.scheduleInAppAlarm(applicationContext, task.description, nextExecutionTime, task.id)
                                Log.d("TaskExecutionWorker", "Ranged task '${task.description}' rescheduled.")
                            } else {
                                taskDao.deleteTask(task)
                                Log.d("TaskExecutionWorker", "Ranged task '${task.description}' completed and deleted.")
                            }
                        } else {
                            // Infinite recurring task
                            val updatedTask = task.copy(executionTimeMillis = nextExecutionTime)
                            taskDao.update(updatedTask)
                            AlarmHelper.scheduleInAppAlarm(applicationContext, task.description, nextExecutionTime, task.id)
                            Log.d("TaskExecutionWorker", "Infinite recurring task '${task.description}' rescheduled.")
                        }
                    } else {
                        // One-time task
                        taskDao.deleteTask(task)
                        Log.d("TaskExecutionWorker", "One-time task '${task.description}' deleted after execution.")
                    }
                } catch (ex: Exception) {
                    Log.e("TaskExecutionWorker", "Failed processing task id ${task.id}", ex)
                }
            }
            Result.success()
        } catch (ex: Exception) {
            Log.e("TaskExecutionWorker", "Worker failed", ex)
            Result.failure()
        }
    }
}
