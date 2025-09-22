// app/src/main/java/com/example/alarmchatapp/DateTimeChangeReceiver.kt
package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.alarmchatapp.workers.DailyClockHydratorWorker

class DateTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // On time/date/timezone change, immediately hydrate "tomorrow" one-shots
        DailyClockHydratorWorker.scheduleCatchUp(context)
    }
}
