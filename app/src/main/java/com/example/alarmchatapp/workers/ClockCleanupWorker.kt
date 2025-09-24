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

            // 1) Best‑effort dismiss in the vendor clock app
            // Try unique label first (e.g., "Title · #id"), then time.
            runCatching { ClockDismissHelper.dismissByLabel(applicationContext, label) }

            // 2) Find the matching row:
            //    Prefer exact id; otherwise, fallback to most recent one‑shot with same plain title.
            val dao = AppDatabase.getDatabase(applicationContext).alarmDao()

            val rowById = runCatching { if (alarmId >= 0) dao.getById(alarmId) else null }.getOrNull()

            val target = rowById ?: run {
                val plainTitle = label.substringBefore(" · #").trim()
                val rows = runCatching { dao.getAll() }.getOrDefault(emptyList())
                rows.filter { !it.isRecurring && it.message.trim() == plainTitle }
                    .maxByOrNull { it.triggerTimeMillis }
            }

            // 3) Time‑based dismiss fallback and local DB cleanup for one‑shots
            target?.let { a ->
                val cal = Calendar.getInstance().apply { timeInMillis = a.triggerTimeMillis }
                runCatching {
                    ClockDismissHelper.dismissByTime(
                        applicationContext,
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE)
                    )
                }

                // Remove one‑shots from local DB so UI stays in sync; keep recurring entries intact
                if (!a.isRecurring) {
                    runCatching { dao.delete(a) }
                }
            }

            Result.success()
        } catch (e: Exception) {
            // Do not retry; cleanup is best‑effort and should not loop
            Log.w("ClockCleanupWorker", "Cleanup best‑effort failed", e)
            Result.success()
        }
    }
}