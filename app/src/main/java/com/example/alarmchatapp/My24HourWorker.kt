package com.example.alarmchatapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class My24HourWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Implement any other periodic background actions you need
        return Result.success()
    }
}
