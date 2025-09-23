package com.example.alarmchatapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.alarmchatapp.workers.DailyClockHydratorWorker
import java.util.Calendar

class DateTimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("MidnightHydrator", "onReceive action=${intent.action}")
        DailyClockHydratorWorker.scheduleCatchUp(context.applicationContext)
        scheduleExactMidnight(context.applicationContext)
    }

    companion object {
        private const val ACTION = "com.example.alarmchatapp.MIDNIGHT_HYDRATE"

        private fun nextMidnightPlusOne(): Long {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, 1)
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 1)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        fun scheduleExactMidnight(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = nextMidnightPlusOne()
            android.util.Log.d("MidnightHydrator", "Arming at=${java.util.Date(at)} ms=$at")

            val pi = PendingIntent.getBroadcast(
                context,
                1001,
                Intent(ACTION).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                DailyClockHydratorWorker.scheduleDailyHydrator(context, at)
                return
            }
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }
}
