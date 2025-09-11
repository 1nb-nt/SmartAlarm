package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.Notification
import androidx.core.app.NotificationCompat
import android.os.Build
import kotlinx.coroutines.launch
import java.util.Collections

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private val firedIds = Collections.synchronizedSet(mutableSetOf<Int>())
        const val ACTION_DISMISS = "com.example.alarmchatapp.ACTION_DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("ALARM_LABEL") ?: "Alarm"
        val id = intent.getIntExtra("ALARM_ID", 0)

        // Idempotency: if already fired, do nothing
        if (!firedIds.add(id)) return

        val nm = context.getSystemService(NotificationManager::class.java)
        val channelId = "alarm_clock_fsi_v2" // keep stable; bump to a new ID only if you must recreate at HIGH

        // Channel: must be HIGH for heads-up/FSI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(channelId)
            if (existing == null) {
                val ch = NotificationChannel(
                    channelId,
                    "Alarm (Full Screen)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    description = "Ringing alarms"
                }
                nm.createNotificationChannel(ch)
            }
        }

        // Full-screen target
        val full = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("alarm_message", message)
            putExtra("alarm_id", id)
        }
        val fullPi = PendingIntent.getActivity(
            context, id, full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canFsi = if (Build.VERSION.SDK_INT >= 34) {
            nm.canUseFullScreenIntent()
        } else true

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                // Launch full-screen when allowed (Android 14+ policy)
                if (canFsi) {
                    setFullScreenIntent(fullPi, true) // lockscreen full-screen; heads-up when unlocked
                }
                // Always provide a content intent so a heads-up tap opens AlarmActivity
                setContentIntent(fullPi)
                // Optional fallback haptics if FSI is denied (uncomment if desired)
                // setVibrate(longArrayOf(0, 700, 300, 700))
            }
            .build()

        nm.notify("alarm", id, notif)

        // One-time alarm cleanup: delete from DB and cancel PendingIntent
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val rec = dao.getById(id)
                if (rec != null && !rec.isRecurring) {
                    // Remove DB row
                    dao.delete(rec)

                    // Cancel any matching PendingIntent, just in case
                    val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    val cancelIntent = Intent(context, AlarmReceiver::class.java)
                    val cancelPi = android.app.PendingIntent.getBroadcast(
                        context,
                        id,
                        cancelIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    am.cancel(cancelPi) // Safe even if not scheduled anymore
                }
            } catch (_: Exception) { }
        }
    }
}
