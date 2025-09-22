// app/src/main/java/com/example/alarmchatapp/workers/DailyClockHydratorWorker.kt
package com.example.alarmchatapp.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.utils.AlarmHelper
import com.example.alarmchatapp.utils.HydratedStore
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

class DailyClockHydratorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val dao = AppDatabase.getDatabase(applicationContext).alarmDao()
            val all = runCatching { dao.getAll() }.getOrDefault(emptyList())

            val (todayStart, todayEnd) = dayBoundsToday()

            // One‑time alarms for TODAY that have not been pushed to Google Clock yet
            val targets = all.filter { a ->
                !a.isRecurring &&
                        a.triggerTimeMillis in todayStart until todayEnd &&
                        !HydratedStore.wasHydrated(applicationContext, a.id)
            }

            for (a in targets.sortedBy { it.triggerTimeMillis }) {
                // Create in Google Clock with unique label "Title · #id"
                AlarmHelper.scheduleAlarmClockPublic(
                    context = applicationContext,
                    label = a.message.ifBlank { "Alarm" },
                    triggerAt = a.triggerTimeMillis,
                    alarmId = a.id,
                    initialNote = null,
                    skipUi = true
                )
                HydratedStore.markHydrated(applicationContext, a.id)

                // Best‑effort cleanup 10 minutes after the ring time
                enqueueCleanup(
                    context = applicationContext,
                    label = "${a.message.trim()} · #${a.id}",
                    alarmId = a.id,
                    whenMillis = a.triggerTimeMillis + TimeUnit.MINUTES.toMillis(10)
                )

                // Small stagger so OEM clocks don’t drop back‑to‑back creates
                delay(250)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("DailyClockHydrator", "Failed", e)
            Result.retry()
        }
    }

    private fun dayBoundsToday(): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        return start to end
    }

    private fun enqueueCleanup(context: Context, label: String, alarmId: Int, whenMillis: Long) {
        val delay = (whenMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val data = Data.Builder()
            .putString("label", label)
            .putInt("alarmId", alarmId)
            .build()
        val req = OneTimeWorkRequestBuilder<ClockCleanupWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("cleanup-$alarmId")
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    companion object {
        fun scheduleDailyHydrator(context: Context) {
            // Run at ~00:00:01 local time every day
            val delay = computeDelayToNext00h00m01()
            val req = PeriodicWorkRequestBuilder<DailyClockHydratorWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("clock-daily-hydrator")
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "clock-daily-hydrator",
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun scheduleCatchUp(context: Context) {
            // Fire once now (e.g., on first app open or after reboot) to hydrate today's items
            val once = OneTimeWorkRequestBuilder<DailyClockHydratorWorker>()
                .addTag("clock-daily-hydrator-catchup")
                .build()
            WorkManager.getInstance(context).enqueue(once)
        }

        private fun computeDelayToNext00h00m01(): Long {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 1); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
            return next.timeInMillis - now.timeInMillis
        }
    }
}
