package com.example.alarmchatapp.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ClockPreSchedulerWorker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("alarm_label") ?: "Alarm"
        Log.d("ClockPreSchedulerWorker", "Alarm triggered: $label")
        // Add notification trigger logic here
    }

    // workers/ClockPreSchedulerWorker.kt
    companion object {
        fun enqueue(
            context: Context,
            label: String,
            triggerAt: Long,
            isRecurring: Boolean,
            recurringDays: IntArray?,
            alarmId: Int
        ) {
            val data = ContactsContract.Contacts.Data.Builder()
                .putString("label", label)
                .putLong("triggerAt", triggerAt)
                .putBoolean("isRecurring", isRecurring)
                .putString("recurringDaysCsv", recurringDays?.joinToString(","))
                .putInt("alarmId", alarmId)
                .build()
            val delay =
                (triggerAt - System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2)).coerceAtLeast(
                    0L
                )
            val req = OneTimeWorkRequestBuilder<ClockPreSchedulerWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("presched-$alarmId-$triggerAt", ExistingWorkPolicy.REPLACE, req)
        }
    }

    override suspend fun doWork(): Result {
        val label = inputData.getString("label") ?: "Alarm"
        val triggerAt = inputData.getLong("triggerAt", 0L)
        val alarmId = inputData.getInt("alarmId", -1)
        if (triggerAt <= 0L || alarmId <= 0) return Result.success()

        val now = System.currentTimeMillis()
        val windowStart = triggerAt - TimeUnit.MINUTES.toMillis(2)
        val lateGrace = TimeUnit.MINUTES.toMillis(5)

        if (now < windowStart - TimeUnit.SECONDS.toMillis(20)) {
            val reDelay = (windowStart - now).coerceAtLeast(0L)
            val again = OneTimeWorkRequestBuilder<ClockPreSchedulerWorker>()
                .setInitialDelay(reDelay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                    "presched-$alarmId-$triggerAt",
                    ExistingWorkPolicy.REPLACE,
                    again
                )
            return Result.success()
        }

        if (now <= triggerAt + lateGrace) {
            com.example.alarmchatapp.utils.AlarmHelper.scheduleAlarmClockPublic(
                context = applicationContext,
                label = label,
                triggerAt = triggerAt,
                alarmId = alarmId,
                initialNote = null,
                skipUi = true,
                showToast = false
            )
        }
        return Result.success()
    }
}


