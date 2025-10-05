package com.example.alarmchatapp

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.example.alarmchatapp.ui.AppContent

class MainActivity : ComponentActivity() {

    // Runtime permission launcher for Android 13+ notifications
    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Ensure high-importance alarm channel exists
        ensureAlarmChannel()

        // 2) Request POST_NOTIFICATIONS on Android 13+
        requestPostNotificationsIfNeeded()

        // 3) Prompt for "Exact alarms" on Android 12+ (don’t block if user declines)
        requestExactAlarmsIfNeeded()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AppContent()
        }
    }

    private fun ensureAlarmChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channelId = "alarm_clock_fsi_v2"
            val existing = nm.getNotificationChannel(channelId)
            if (existing == null || existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                if (existing != null) nm.deleteNotificationChannel(channelId)
                val ch = NotificationChannel(
                    channelId,
                    "Alarm Full Screen",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Ringing alarms"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    setBypassDnd(true) // OEMs may ignore, but request best behavior
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestExactAlarmsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                // Open the “Allow exact alarms” settings for this app; scheduling will still proceed
                try {
                    startActivity(
                        android.content.Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = android.net.Uri.parse("package:$packageName")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (_: Exception) {
                    // Some OEMs may not expose the activity; safe to ignore
                }
            }
        }
    }
}
