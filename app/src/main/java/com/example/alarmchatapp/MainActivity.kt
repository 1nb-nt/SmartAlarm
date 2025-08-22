package com.example.alarmchatapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.alarmchatapp.ui.theme.AppContent
import com.example.alarmchatapp.ui.theme.AlarmListScreen

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                "alarm_channel",
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Channel for alarm notifications"
            notificationManager.createNotificationChannel(channel)
        }

        setContent {
            AppContent()  // Compose entrypoint shows ChatScreen or AlarmListScreen based on state
        }
    }
}
