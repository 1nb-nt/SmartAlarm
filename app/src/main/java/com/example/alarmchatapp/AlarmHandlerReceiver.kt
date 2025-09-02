package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmHandlerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted, rescheduling alarms.")
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val alarms = db.alarmDao().getAll()
                alarms.forEach { alarm ->
                    if (alarm.isRecurring) {
                        // reschedule missing alarms here using AlarmHelper (implementation depends on design)
                    } else {
                        // schedule single alarm if still pending
                        AlarmHelper.scheduleSingleAlarm(context, alarm.message, alarm.triggerTimeMillis, alarm.id)
                    }
                }
            }
        }
    }
}
