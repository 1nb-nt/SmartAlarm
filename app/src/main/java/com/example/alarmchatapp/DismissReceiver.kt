package com.example.alarmchatapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("ALARM_ID", 0)
        // stop sound and finish the ringing UI
        context.sendBroadcast(Intent("com.example.alarmchatapp.STOP_RING"))
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel("alarm", id)
        context.sendBroadcast(Intent("com.example.alarmchatapp.FINISH_ALARM"))
    }
}
