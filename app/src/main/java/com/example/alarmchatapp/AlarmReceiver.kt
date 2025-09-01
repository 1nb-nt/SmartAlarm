package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.app.NotificationManager
import android.app.NotificationChannel
import androidx.core.app.NotificationCompat
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.*
import java.util.*

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val message = intent.getStringExtra("ALARM_LABEL") ?: "Alarm"

        Log.d("AlarmReceiver", "Alarm received: ID=$alarmId, Msg=$message")

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "alarm_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Alarm Notifications", NotificationManager.IMPORTANCE_HIGH)
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

        notificationManager.notify(alarmId, notification)

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ALARM_MESSAGE", message)
        }
        context.startActivity(alarmIntent)

        if (alarmId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val alarmDao = db.alarmDao()
            val alarm = alarmDao.getById(alarmId) ?: return@launch
            if (alarm.isRecurring) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = alarm.triggerTimeMillis
                cal.add(Calendar.WEEK_OF_YEAR, 1)
                val nextTrigger = cal.timeInMillis
                alarmDao.update(alarm.copy(triggerTimeMillis = nextTrigger))
                withContext(Dispatchers.Main) {
                    AlarmHelper.scheduleSingleAlarm(context, alarm.message, nextTrigger, alarm.id)
                }
            } else {
                alarmDao.delete(alarm)
            }
        }
    }
}
