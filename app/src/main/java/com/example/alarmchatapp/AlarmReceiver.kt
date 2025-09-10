package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.Notification
import android.graphics.Color
import androidx.core.app.NotificationCompat
import android.os.Build
import kotlinx.coroutines.launch
import java.util.Collections

// AlarmReceiver.kt
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private val firedIds =  Collections.synchronizedSet(mutableSetOf<Int>())
        const val ACTION_DISMISS = "com.example.alarmchatapp.ACTION_DISMISS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("ALARM_LABEL") ?: "Alarm"
        val id = intent.getIntExtra("ALARM_ID", 0)

        // Idempotency: if already fired, do nothing
        if (!firedIds.add(id)) return  // already handled once [2]

        val nm = context.getSystemService(NotificationManager::class.java)
        val channelId = "alarm_clock_fsi_v2" // new ID if importance changed

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                val ch = NotificationChannel(channelId, "Alarm (Full Screen)", NotificationManager.IMPORTANCE_HIGH)
                ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                nm.createNotificationChannel(ch)
            }
        }

        // Full-screen target
        val full = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("alarm_message", message)
            putExtra("alarm_id", id)
        }
        val fullPi = PendingIntent.getActivity(context, id, full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Dismiss action
        val dismiss = Intent(context, DismissReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra("ALARM_ID", id)
        }
        val dismissPi = PendingIntent.getBroadcast(context, id, dismiss, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullPi, true)    // lockscreen full-screen; heads-up when unlocked [2][1]
            .setOnlyAlertOnce(true)               // don’t alert again on updates [1]
            .setOngoing(true)                     // persistent until dismissed
            .addAction(0, "Dismiss", dismissPi)
            .build()

        nm.notify("alarm", id, notif)

        // Optional: mark one-shot alarms as consumed in DB to avoid re-scheduling
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(context).alarmDao()
            val rec = dao.getById(id)
            if (rec != null && !rec.isRecurring) {
                // mark consumed flag or delete, depending on your schema
                // dao.delete(rec)
            }
        }
    }
}


