// workers/ClockCleanupWorker.kt
package com.example.alarmchatapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.utils.ClockDismissHelper
import java.util.Calendar

class ClockCleanupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val label = inputData.getString("label") ?: return Result.success()
            val alarmId = inputData.getInt("alarmId", -1)

            // First try by unique label (e.g., "Title · #id")
            ClockDismissHelper.dismissByLabel(applicationContext, label)

            val dao = AppDatabase.getDatabase(applicationContext).alarmDao()

            // Prefer exact row by id; if not present, fallback to latest one-shot with same plain title
            val target = runCatching { if (alarmId >= 0) dao.getById(alarmId) else null }
                .getOrNull()
                ?: run {
                    val plainTitle = label.substringBefore(" · #").trim()
                    val rows = runCatching { dao.getAll() }.getOrDefault(emptyList())
                    rows.filter { !it.isRecurring && it.message == plainTitle }
                        .maxByOrNull { it.triggerTimeMillis }
                }

            if (target != null) {
                val cal = Calendar.getInstance().apply { timeInMillis = target.triggerTimeMillis }
                ClockDismissHelper.dismissByTime(
                    applicationContext,
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE)
                )
                runCatching { dao.delete(target) }
            }

            Result.success()
        } catch (e: Exception) {
            Log.w("ClockCleanupWorker", "Cleanup best-effort failed", e)
            Result.success()
        }
    }
}
