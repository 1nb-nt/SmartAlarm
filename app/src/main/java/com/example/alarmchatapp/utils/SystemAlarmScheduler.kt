// app/src/main/java/com/example/alarmchatapp/utils/SystemAlarmScheduler.kt
package com.example.alarmchatapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.ArrayList
import com.example.alarmchatapp.utils.ClockDismissHelper


object SystemAlarmScheduler {

    fun setOneTimeAlarm(
        context: Context,
        label: String,
        triggerAtMillis: Long,
        vibrate: Boolean = true,
        ringtone: Uri? = null,
        skipUi: Boolean = true
    ) {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(triggerAtMillis), ZoneId.systemDefault())
        val base = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_HOUR, ldt.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, ldt.minute)
            .putExtra(AlarmClock.EXTRA_VIBRATE, vibrate)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ringtone != null) base.putExtra(AlarmClock.EXTRA_RINGTONE, ringtone)

        if (launchIntent(context, base)) return

        // Fallback: retry showing UI so user can confirm creation if silent path is blocked
        val showUi = Intent(base).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        if (launchIntent(context, showUi)) return

        Log.w("SystemAlarmScheduler", "No handler for ACTION_SET_ALARM (one-time)")
    }

    fun setWeeklyAlarm(
        context: Context,
        label: String,
        hour: Int,
        minute: Int,
        days: List<Int>,
        vibrate: Boolean = true,
        skipUi: Boolean = true
    ) {
        val base = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_VIBRATE, vibrate)
            .putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (launchIntent(context, base)) return

        // Fallback: retry with UI visible
        val showUi = Intent(base).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        if (launchIntent(context, showUi)) return

        Log.w("SystemAlarmScheduler", "No handler for ACTION_SET_ALARM (weekly)")
    }

    private fun launchIntent(context: Context, i: Intent): Boolean {
        val pm = context.packageManager
        if (i.resolveActivity(pm) != null) {
            context.startActivity(i); return true
        }
        // Try common clock packages explicitly
        val candidates = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage"
        )
        for (pkg in candidates) {
            val targeted = Intent(i).setPackage(pkg)
            if (targeted.resolveActivity(pm) != null) {
                context.startActivity(targeted); return true
            }
        }
        return false
    }

    fun dismissByLabel(context: Context, label: String) {
        ClockDismissHelper.dismissByLabel(context, label)
    }

    fun dismissByTime(context: Context, hour: Int, minute: Int) {
        ClockDismissHelper.dismissByTime(context, hour, minute)
    }



    fun showAlarms(context: Context) {
        val i = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(context.packageManager) != null) context.startActivity(i)
    }
}
