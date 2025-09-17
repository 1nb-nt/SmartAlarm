package com.example.alarmchatapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Collections

class AlarmReceiver : BroadcastReceiver() {


    companion object {
        private val firedIds = Collections.synchronizedSet(mutableSetOf<Int>())
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("ALARM_ID", 0)
        val label = intent.getStringExtra("ALARM_LABEL") ?: "Alarm"
        val note = intent.getStringExtra("INITIAL_NOTE")

        if (id == 0) {
            Log.w("AlarmReceiver", "Missing ALARM_ID")
            return
        }
        if (!firedIds.add(id)) {
            Log.d("AlarmReceiver", "Duplicate delivery for id=$id; skipping")
            return
        }

        // Start the alarm screen directly in full-screen
        val full = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alarm_message", label)
            putExtra("ALARM_ID", id)
            putExtra("INITIAL_NOTE", note)
        }
        try {
            context.startActivity(full) // launch UI without any notification[1]
            NotificationHelper.showAlarmNotification(context, id, label, note)
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Failed to start AlarmActivity", e)
        }

        NotificationHelper.showAlarmNotification(context, id, label, note)
        // Reschedule or clean up asynchronously (unchanged)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val alarm = dao.getById(id) ?: return@launch
                if (alarm.isRecurring) {
                    val firedCal = Calendar.getInstance().apply { timeInMillis = alarm.triggerTimeMillis }
                    val hour = firedCal.get(Calendar.HOUR_OF_DAY)
                    val minute = firedCal.get(Calendar.MINUTE)
                    val nextTrigger: Long? = when {
                        alarm.recurringDays?.size == 7 -> {
                            Calendar.getInstance().apply {
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                                add(Calendar.DAY_OF_YEAR, 1)
                            }.timeInMillis
                        }
                        !alarm.recurringDays.isNullOrEmpty() -> {
                            AlarmHelper.computeNextAmongDays(hour, minute, alarm.recurringDays!!)
                        }
                        else -> null
                    }
                    if (nextTrigger != null) {
                        dao.update(alarm.copy(triggerTimeMillis = nextTrigger))
                        // pass the same note to next occurrence
                        AlarmHelper.scheduleAlarmClockPublic(
                            context = context,
                            label = alarm.message,
                            triggerAt = nextTrigger,
                            alarmId = alarm.id,
                            initialNote = note
                        )

                    } else {
                        dao.delete(alarm)
                    }
                } else {
                    dao.delete(alarm)
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val cancelIntent = Intent(context, AlarmReceiver::class.java)
                    val cancelPi = PendingIntent.getBroadcast(
                        context, id, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    am.cancel(cancelPi)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error handling alarm id=$id", e)
            } finally {
                pending.finish()
            }
        }
    }
}