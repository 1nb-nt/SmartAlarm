package com.example.alarmchatapp.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.alarmchatapp.utils.AlarmHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

class ClockPreSchedulerWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val label = inputData.getString("label") ?: "Alarm"
            val triggerAt = inputData.getLong("triggerAt", 0L)
            val isRecurring = inputData.getBoolean("isRecurring", false)
            val daysCsv = inputData.getString("recurringDaysCsv")
            val alarmId = inputData.getInt("alarmId", -1)
            if (triggerAt <= 0L) return Result.success()

            val now = System.currentTimeMillis()
            // lead/guard rails
            val SAFETY_LEAD_MS = TimeUnit.MINUTES.toMillis(2)   // create ~2 min before the JSON instant
            val EARLY_SLACK_MS = TimeUnit.SECONDS.toMillis(20)  // if woke too early, re-enqueue precisely
            val LATE_GRACE_MS = TimeUnit.SECONDS.toMillis(60)   // if ≥60s late, skip to avoid “tomorrow”

            // Only create inside the final window just before the JSON instant
            val targetWindowStart = triggerAt - SAFETY_LEAD_MS
            if (now < targetWindowStart - EARLY_SLACK_MS) {
                // Re-enqueue for the start of the window
                reenqueueSelf(
                    uniqueName = "presched-$alarmId",
                    delayMs = (targetWindowStart - now).coerceAtLeast(0L),
                    label = label,
                    triggerAt = triggerAt,
                    isRecurring = isRecurring,
                    daysCsv = daysCsv,
                    alarmId = alarmId
                )
                return Result.success()
            }
            if (now > triggerAt + LATE_GRACE_MS) {
                // Too late — creating now could roll Clock to the next day
                Log.w("ClockPreScheduler", "Skipping late create to avoid rolling to tomorrow (id=$alarmId)")
                return Result.success()
            }

            if (isRecurring && !daysCsv.isNullOrBlank()) {
                // Weekly/daily series: create by HH:mm with id-backed label
                val days = daysCsv.split(",").mapNotNull { it.toIntOrNull() }
                val cal = Calendar.getInstance().apply { timeInMillis = triggerAt }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                AlarmHelper.scheduleWeeklyInClock(
                    context = applicationContext,
                    label = label,
                    hour = hour,
                    minute = minute,
                    days = days,
                    alarmId = alarmId,
                    skipUi = true,
                    showToast = false
                )
            } else {
                // One-shot: create inside the window so Clock uses the correct date’s HH:mm
                AlarmHelper.scheduleAlarmClockPublic(
                    context = applicationContext,
                    label = label,
                    triggerAt = triggerAt,
                    alarmId = alarmId,
                    initialNote = null,
                    skipUi = true,
                    showToast = false
                )
                // Light stagger improves reliability when multiple alarms share a minute
                delay(250)

                // Schedule cleanup ~10 minutes after ring with the unique label "Title · #id"
                enqueueCleanup(
                    label = "${label.trim()} · #$alarmId",
                    whenMillis = triggerAt + TimeUnit.MINUTES.toMillis(10),
                    alarmId = alarmId
                )
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("ClockPreScheduler", "Failed", e)
            return Result.retry()
        }
    }

    private fun reenqueueSelf(
        uniqueName: String,
        delayMs: Long,
        label: String,
        triggerAt: Long,
        isRecurring: Boolean,
        daysCsv: String?,
        alarmId: Int
    ) {
        val data = Data.Builder()
            .putString("label", label)
            .putLong("triggerAt", triggerAt)
            .putBoolean("isRecurring", isRecurring)
            .putString("recurringDaysCsv", daysCsv)
            .putInt("alarmId", alarmId)
            .build()
        val req = OneTimeWorkRequestBuilder<ClockPreSchedulerWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, req)
    }

    private fun enqueueCleanup(label: String, whenMillis: Long, alarmId: Int) {
        val delay = (whenMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val data = Data.Builder()
            .putString("label", label)
            .putInt("alarmId", alarmId)
            .build()
        val req = OneTimeWorkRequestBuilder<ClockCleanupWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork("cleanup-$alarmId-$whenMillis", ExistingWorkPolicy.REPLACE, req)
    }

    companion object {
        // Enqueue to fire just before the exact JSON notification instant
        fun enqueue(
            context: Context,
            label: String,
            triggerAt: Long,
            isRecurring: Boolean,
            recurringDays: List<Int>?,
            alarmId: Int = -1
        ) {
            val now = System.currentTimeMillis()
            val SAFETY_LEAD_MS = TimeUnit.MINUTES.toMillis(2)
            val delay = (triggerAt - SAFETY_LEAD_MS - now).coerceAtLeast(0L)

            val data = Data.Builder()
                .putString("label", label)
                .putLong("triggerAt", triggerAt)
                .putBoolean("isRecurring", isRecurring)
                .putString("recurringDaysCsv", recurringDays?.joinToString(","))
                .putInt("alarmId", alarmId)
                .build()

            val req = OneTimeWorkRequestBuilder<ClockPreSchedulerWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("presched-$alarmId", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
