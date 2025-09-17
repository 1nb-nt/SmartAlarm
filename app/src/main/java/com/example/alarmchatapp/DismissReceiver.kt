package com.example.alarmchatapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("ALARM_ID", 0)

        // Tell AlarmActivity (or service) to stop ringtone/vibration
        context.sendBroadcast(Intent("com.example.alarmchatapp.STOP_RING"))

        // Cancel the active alarm notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(id)

        // Tell AlarmActivity to finish itself
        context.sendBroadcast(Intent("com.example.alarmchatapp.FINISH_ALARM"))
    }
}
