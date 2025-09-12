package com.example.alarmchatapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("RescheduleReceiver", "onReceive action=$action")

        // Use goAsync so the process can keep running while suspend Room calls finish.[1]
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                // Use your suspend DAO
                val alarms = dao.getAll() // suspend fun getAll(): List<Alarm>
                val now = System.currentTimeMillis()

                var restored = 0
                alarms.forEach { a ->
                    if (a.triggerTimeMillis > now) {
                        AlarmHelper.scheduleAlarmClockPublic(
                            context = context,
                            label = a.message,
                            triggerAt = a.triggerTimeMillis,
                            alarmId = a.id
                        )
                        restored++
                    }
                }
                Log.d("RescheduleReceiver", "Restored $restored alarms (future ones rescheduled).")
            } catch (e: Exception) {
                Log.e("RescheduleReceiver", "Failed to restore alarms", e)
            } finally {
                pending.finish()
            }
        }
    }
}