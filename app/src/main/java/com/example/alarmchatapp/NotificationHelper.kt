package com.example.alarmchatapp

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID = "alarm_channel"

    fun showAlarmNotification(context: Context, id: Int, title: String, note: String?) {
        // Full-screen intent to AlarmActivity
        val fullscreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alarm_message", title)
            putExtra("ALARM_ID", id)
            putExtra("INITIAL_NOTE", note)
        }
        val fsi = PendingIntent.getActivity(
            context, id, fullscreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Optional dismiss action (wire to a BroadcastReceiver if desired)
        val dismissIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, DismissReceiver::class.java).putExtra("ALARM_ID", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // built-in icon
            .setContentTitle(title)
            .setContentText(note ?: "Alarm")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissIntent)
            .setFullScreenIntent(fsi, true) // request full-screen
            .build()

        // Android 13+ runtime permission check
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // Don't crash: skip notify if permission missing (request it from an Activity elsewhere)
                return
            }
        }

        NotificationManagerCompat.from(context).notify(id, notif)
    }
}
