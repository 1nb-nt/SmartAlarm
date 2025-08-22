package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.NotificationManager
import android.app.NotificationChannel
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.AlarmActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("ALARM_LABEL") ?: "Alarm!"

        val channelId = "alarm_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel to show alarms"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)

        // Launch AlarmActivity to show alarm UI
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ALARM_MESSAGE", message)
        }
        context.startActivity(alarmIntent)

        // Delete alarm from database if it is NOT recurring
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        if (alarmId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val alarmDao = db.alarmDao()
            val alarm = alarmDao.getById(alarmId)
            if (alarm != null && !alarm.isRecurring) {
                alarmDao.delete(alarm)
            }
        }
    }
}
