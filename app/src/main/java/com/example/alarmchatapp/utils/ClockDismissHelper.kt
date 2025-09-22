// utils/ClockDismissHelper.kt
package com.example.alarmchatapp.utils

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

object ClockDismissHelper {

    fun dismissByLabel(context: Context, label: String) {
        val i = Intent(AlarmClock.ACTION_DISMISS_ALARM)
            .putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // ADD
        launchClockIntent(context, i)                // CHANGED
    }

    fun dismissByTime(context: Context, hour: Int, minute: Int) {
        val i = Intent(AlarmClock.ACTION_DISMISS_ALARM)
            .putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
        if (i.resolveActivity(context.packageManager) != null) context.startActivity(i)
    }
    private fun launchClockIntent(context: Context, i: Intent) {
        val pm = context.packageManager
        if (i.resolveActivity(pm) != null) { context.startActivity(i); return }
        listOf("com.google.android.deskclock","com.android.deskclock","com.sec.android.app.clockpackage")
            .forEach { pkg ->
                val t = Intent(i).setPackage(pkg)
                if (t.resolveActivity(pm) != null) { context.startActivity(t); return }
            }
        // Final fallback: show list so the user can remove it
        Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .also { if (it.resolveActivity(pm) != null) context.startActivity(it) }
    }
}
