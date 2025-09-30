package com.example.alarmchatapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Collections

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private val firedIds = Collections.synchronizedSet(mutableSetOf<Int>())
        const val ACTION_DISMISS = "com.example.alarmchatapp.ACTION_DISMISS"
        private const val CHANNEL_ID = "alarm_clock_fsi_v2"
        private const val CHANNEL_NAME = "Alarm Full Screen"
        const val EXTRA_LABEL = "alarm_message"
        const val EXTRA_ID = "alarm_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra(EXTRA_LABEL) ?: "Alarm"
        val id = intent.getIntExtra(EXTRA_ID, 0)
        if (id == 0) {
            Log.w("AlarmReceiver", "Missing EXTRA_ID")
            return
        }

        // Idempotency
        if (!firedIds.add(id)) {
            Log.d("AlarmReceiver", "Duplicate delivery for id=$id, skipping")
            return
        }

        val nm = context.getSystemService(NotificationManager::class.java)

        // Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    description = "Ringing alarms"
                }
                nm.createNotificationChannel(ch)
            }
        }

        // Full-screen Activity
        val full = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_LABEL, message)
            putExtra(EXTRA_ID, id)
        }
        val fullPi = PendingIntent.getActivity(
            context, id, full,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 14+ FSI policy
        val canFsi = if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true

        val dismissIntent = Intent(ACTION_DISMISS).setPackage(context.packageName)
        val dismissPi = PendingIntent.getBroadcast(
            context, id, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Dismiss", dismissPi)
            .apply {
                if (canFsi) {
                    // critical to auto-launch AlarmActivity
                    setFullScreenIntent(fullPi, true)
                } else {
                    setContentIntent(fullPi) // heads-up; tap opens
                }
            }
            .build()

        nm.notify(id, notif)

        // Reschedule/cleanup (goAsync for suspend DAO)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val alarm = dao.getById(id)
                if (alarm == null) {
                    Log.d("AlarmReceiver", "No DB row for id=$id")
                    return@launch
                }
                if (alarm.isRecurring) {
                    val firedCal = Calendar.getInstance().apply {
                        timeInMillis = alarm.triggerTimeMillis
                    }
                    val hour = firedCal.get(Calendar.HOUR_OF_DAY)
                    val minute = firedCal.get(Calendar.MINUTE)
                    val nextTrigger: Long? = when {
                        alarm.recurringDays?.size == 7 -> {
                            Calendar.getInstance().apply {
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                add(Calendar.DAY_OF_YEAR, 1)
                            }.timeInMillis
                        }
                        !alarm.recurringDays.isNullOrEmpty() -> {
                            AlarmHelper.computeNextAmongDays(hour, minute, alarm.recurringDays!!)
                        }
                        else -> null
                    }
                    if (nextTrigger != null) {
                        dao.update(alarm.copy(triggerTimeMillis = nextTrigger))
                        AlarmHelper.scheduleAlarmClockPublic(
                            context = context,
                            label = alarm.message,
                            triggerAt = nextTrigger,
                            alarmId = alarm.id
                        )
                        Log.d("AlarmReceiver", "Rescheduled recurring id=${alarm.id} next=$nextTrigger")
                    } else {
                        dao.delete(alarm)
                        Log.d("AlarmReceiver", "Recurring alarm missing days, deleted id=${alarm.id}")
                    }
                } else {
                    // One-time cleanup
                    dao.delete(alarm)
                    val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    val cancelIntent = Intent(context, AlarmReceiver::class.java)
                    val cancelPi = PendingIntent.getBroadcast(
                        context, id, cancelIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    am.cancel(cancelPi)
                    Log.d("AlarmReceiver", "One-time alarm cleaned up id=$id")
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error handling alarm id=$id", e)
            } finally {
                pending.finish()
            }
        }
    }
}
