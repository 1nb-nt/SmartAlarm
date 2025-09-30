package com.example.alarmchatapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmReceiver.EXTRA_ID, 0)

        // Stop ringing immediately
        runCatching { AlarmAudio.ringtone?.stop() }
        AlarmAudio.ringtone = null
        runCatching { AlarmAudio.vibrator?.cancel() }
        AlarmAudio.vibrator = null

        // Cancel the alarm notification (targeted if id present, else all)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (id != 0) nm.cancel(id) else {
            Log.w("DismissReceiver", "Missing EXTRA_ID; calling cancelAll()")
            nm.cancelAll()
        }

        // Tell AlarmActivity to stop and finish if visible
        context.sendBroadcast(
            Intent("com.example.alarmchatapp.ACTION_STOP_RING")
                .setPackage(context.packageName)
                .putExtra(AlarmReceiver.EXTRA_ID, id)
        )
    }
}
