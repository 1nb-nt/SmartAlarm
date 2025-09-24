// app/src/main/java/com/example/alarmchatapp/DateTimeChangeReceiver.kt
package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.alarmchatapp.workers.DailyClockHydratorWorker

class DateTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyClockHydratorWorker.scheduleDailyHydrator(context)
        DailyClockHydratorWorker.scheduleCatchUp(context)
    }
}