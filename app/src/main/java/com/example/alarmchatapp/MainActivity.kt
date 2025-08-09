package com.example.alarmchatapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.alarmchatapp.ui.ChatScreen
import com.example.alarmchatapp.ui.theme.AlarmChatAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create notification channel used by AlarmReceiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                "alarm_channel",
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            chan.description = "Channel for alarm notifications"
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(chan)
        }

        setContent {
            AlarmChatAppTheme {
                ChatScreen()
            }
        }
    }
}
