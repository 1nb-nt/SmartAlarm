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
import android.util.Log
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.withContext
import java.util.*

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("ALARM_LABEL") ?: "Alarm!"
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        Log.d("AlarmReceiver", "Alarm received. ID: $alarmId, Message: $message")

        val channelId = "alarm_channel"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for alarm notifications"
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

        // Launch Alarm UI
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ALARM_MESSAGE", message)
        }
        context.startActivity(alarmIntent)

        if (alarmId == -1) {
            Log.e("AlarmReceiver", "Invalid alarm ID")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val alarmDao = db.alarmDao()
                val alarm = alarmDao.getById(alarmId)

                if (alarm == null) {
                    Log.e("AlarmReceiver", "No alarm found in DB with ID $alarmId")
                    return@launch
                }

                if (alarm.isRecurring) {
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = alarm.triggerTimeMillis
                        add(Calendar.WEEK_OF_YEAR, 1) // For weekly recurrence; change to DAY_OF_YEAR for daily
                    }
                    val nextTriggerTime = calendar.timeInMillis
                    Log.d("AlarmReceiver", "Rescheduling recurring alarm for next week at $nextTriggerTime")

                    val updatedAlarm = alarm.copy(triggerTimeMillis = nextTriggerTime)
                    alarmDao.update(updatedAlarm)

                    withContext(Dispatchers.Main) {
                        AlarmHelper.scheduleInAppAlarm(context, alarm.message, nextTriggerTime, alarm.id)
                    }
                } else {
                    Log.d("AlarmReceiver", "Deleting one-time alarm ID: $alarmId")
                    alarmDao.delete(alarm)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error handling alarm reschedule: ${e.localizedMessage}", e)
            }
        }
    }
}
