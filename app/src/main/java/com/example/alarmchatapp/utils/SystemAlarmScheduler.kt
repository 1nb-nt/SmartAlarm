// app/src/main/java/com/example/alarmchatapp/utils/SystemAlarmScheduler.kt
package com.example.alarmchatapp.utils

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.ArrayList

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

        if (launchViaPendingIntent(context, base)) return

        // Fallback: retry showing UI so user can confirm creation if silent path is blocked
        val showUi = Intent(base).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        if (launchViaPendingIntent(context, showUi)) return

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

        if (launchViaPendingIntent(context, base)) return

        // Fallback: retry with UI visible
        val showUi = Intent(base).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        if (launchViaPendingIntent(context, showUi)) return

        Log.w("SystemAlarmScheduler", "No handler for ACTION_SET_ALARM (weekly)")
    }

    private fun launchViaPendingIntent(context: Context, intent: Intent): Boolean {
        val pm = context.packageManager

        // Try default resolver first
        if (intent.resolveActivity(pm) != null && sendPI(context, intent)) return true

        // Try common clock packages explicitly
        val candidates = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage"
        )
        for (pkg in candidates) {
            val targeted = Intent(intent).setPackage(pkg)
            if (targeted.resolveActivity(pm) != null && sendPI(context, targeted)) return true
        }
        return false
    }

    // Uses PendingIntent to avoid background-activity launch blocks at midnight.
    // SystemAlarmScheduler.kt — drop-in replacement for sendPI
    private fun sendPI(context: Context, i: Intent): Boolean {
        return try {
            val pi = android.app.PendingIntent.getActivity(
                context,
                0,
                i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                // Use reflection so this compiles with compileSdk < 34
                try {
                    val clazz = Class.forName("android.app.ActivityOptions")
                    val makeBasic = clazz.getMethod("makeBasic")
                    val opts = makeBasic.invoke(null) as android.app.ActivityOptions
                    val field = clazz.getField("PENDING_INTENT_BACKGROUND_ACTIVITY_START_MODE_ALLOWED")
                    val allowed = field.getInt(null)
                    val setter = clazz.getMethod(
                        "setPendingIntentBackgroundActivityStartMode",
                        Int::class.javaPrimitiveType
                    )
                    setter.invoke(opts, allowed)
                    pi.send(context, 0, null, null, null, null, opts.toBundle())
                } catch (rt: Throwable) {
                    // Fallback if reflection fails
                    pi.send()
                }
            } else {
                pi.send()
            }
            true
        } catch (t: Throwable) {
            android.util.Log.w("SystemAlarmScheduler", "PendingIntent launch failed: ${t.message}")
            false
        }
    }



    fun dismissByLabel(context: Context, label: String) {
        ClockDismissHelper.dismissByLabel(context, label)
    }

    fun dismissByTime(context: Context, hour: Int, minute: Int) {
        ClockDismissHelper.dismissByTime(context, hour, minute)
    }

    fun showAlarms(context: Context) {
        val i = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pm = context.packageManager
        if (i.resolveActivity(pm) != null) {
            // Showing UI is user-driven; direct start is fine here
            context.startActivity(i)
        }
    }
}
