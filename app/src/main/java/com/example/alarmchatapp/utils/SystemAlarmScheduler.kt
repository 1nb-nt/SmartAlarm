package com.example.alarmchatapp.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object SystemAlarmScheduler {
    private val CLOCK_PACKAGES = listOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.miui.clock",
        "com.coloros.alarmclock",
        "com.oneplus.deskclock",
        "com.huawei.deskclock",
        "com.vivo.alarmclock"
    )

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
        if (ringtone != null) base.putExtra(AlarmClock.EXTRA_RINGTONE, ringtone.toString())

        // First attempt: silent vendor-aware launch
        if (launchForVendors(context, base, preferShowUiIfDenied = false)) return

        // OEM minute-10 fallback: some devices reject EXACT skip-UI at x:10
        if (skipUi && ldt.minute == 10) {
            val withUi = Intent(base).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            if (launchForVendors(context, withUi, preferShowUiIfDenied = false)) return

            // Optional last resort: nudge to +1 minute to avoid silent drop
            val nudged = ldt.plusMinutes(1)
            val nudgedIntent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                .putExtra(AlarmClock.EXTRA_HOUR, nudged.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, nudged.minute)
                .putExtra(AlarmClock.EXTRA_VIBRATE, vibrate)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (launchForVendors(context, nudgedIntent, preferShowUiIfDenied = false)) return
        }

        // Final attempt: allow UI generally
        val showUi = Intent(base).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        if (launchForVendors(context, showUi, preferShowUiIfDenied = false)) return

        Log.w("SystemAlarmScheduler", "No handler for ACTION_SET_ALARM (one-time)")
        showAlarms(context)
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
            .putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
            .putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (launchForVendors(context, base, preferShowUiIfDenied = false)) return

        Log.w("SystemAlarmScheduler", "No handler for ACTION_SET_ALARM (weekly)")
        showAlarms(context)
    }

    fun dismissByLabel(context: Context, label: String) {
        ClockDismissHelper.dismissByLabel(context, label)
    }

    fun dismissByTime(context: Context, hour: Int, minute: Int) {
        ClockDismissHelper.dismissByTime(context, hour, minute)
    }

    fun showAlarms(context: Context) {
        val pm = context.packageManager
        val i = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(pm) != null) {
            context.startActivity(i); return
        }
        for (pkg in CLOCK_PACKAGES) {
            runCatching {
                val launch = pm.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    return
                }
            }
        }
        Log.w("SystemAlarmScheduler", "No clock app found to show alarms")
    }

    private fun launchForVendors(
        context: Context,
        base: Intent,
        preferShowUiIfDenied: Boolean
    ): Boolean {
        val pm = context.packageManager
        for (pkg in CLOCK_PACKAGES) {
            val targeted = Intent(base).setPackage(pkg)
            if (targeted.resolveActivity(pm) != null) {
                if (startWithSkipUiFallback(context, targeted, base, preferShowUiIfDenied)) return true
            }
        }
        if (base.resolveActivity(pm) != null) {
            if (startWithSkipUiFallback(context, base, base, preferShowUiIfDenied)) return true
        }
        val show = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (show.resolveActivity(pm) != null) runCatching { context.startActivity(show) }
        return false
    }

    private fun startWithSkipUiFallback(
        context: Context,
        candidate: Intent,
        original: Intent,
        preferShowUiIfDenied: Boolean
    ): Boolean {
        return try {
            context.startActivity(candidate); true
        } catch (_: Exception) {
            if (preferShowUiIfDenied) {
                val withUi = Intent(original).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                try { context.startActivity(withUi); return true } catch (_: Exception) { }
            }
            false
        }
    }
}