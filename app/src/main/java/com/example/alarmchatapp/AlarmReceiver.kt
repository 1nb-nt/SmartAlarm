package com.example.alarmchatapp

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Collections

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private val firedIds = Collections.synchronizedSet(mutableSetOf<Int>())

        const val ACTION_DISMISS = "com.example.alarmchatapp.ACTION_DISMISS"

        private const val CHANNEL_ID = "alarm_clock_fsi_v2"
        private const val CHANNEL_NAME = "Alarm Full Screen"
        private const val NOTIF_ID_BASE = 52001

        const val EXTRA_LABEL = "alarm_message"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_NOTE = "initial_note"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISS) {
            val id = intent.getIntExtra(EXTRA_ID, 0)
            stopAudio()
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.cancel(NOTIF_ID_BASE + id)
            firedIds.remove(id)
            return
        }

        val message = intent.getStringExtra(EXTRA_LABEL) ?: "Alarm"
        val initialNote = intent.getStringExtra(EXTRA_NOTE) ?: ""
        val id = intent.getIntExtra(EXTRA_ID, 0)
        if (id == 0) {
            Log.w("AlarmReceiver", "Missing EXTRA_ID"); return
        }
        if (!firedIds.add(id)) {
            Log.d("AlarmReceiver", "Duplicate id=$id, skip"); return
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        enableVibration(true)
                        setSound(null, null)
                        description = "WOW Alarm full-screen alerts"
                    }
                )
            }
        }

        runCatching { AlarmAudio.ringtone?.let { if (it.isPlaying) it.stop() } }

        val prefs = context.getSharedPreferences("wow_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("ringtone_uri", null)
        val chosen: Uri? = saved?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val fallback: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val playUri = if (isPlayable(chosen, context)) chosen!! else fallback
        val tone: Ringtone? = runCatching { RingtoneManager.getRingtone(context, playUri) }
            .getOrElse { runCatching { RingtoneManager.getRingtone(context, fallback) }.getOrNull() }
        tone?.let { rt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rt.isLooping = true
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                rt.streamType = AudioManager.STREAM_ALARM
            }
            runCatching { rt.play() }
            AlarmAudio.ringtone = rt
        }

        val vibrator = context.getSystemService(Vibrator::class.java)
        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
        AlarmAudio.vibrator = vibrator

        val fullIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_LABEL, message)
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_NOTE, initialNote)
        }
        val fullPi = PendingIntent.getActivity(context, id, fullIntent, PendingIntent.FLAG_UPDATE_CURRENT or immutable())

        val dismissPi = PendingIntent.getBroadcast(
            context, id + 1000,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or immutable()
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(0, "Dismiss", dismissPi)
            .setFullScreenIntent(fullPi, true)
            .build()
        nm.notify(NOTIF_ID_BASE + id, notif)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val alarm = dao.getById(id) ?: return@launch

                if (alarm.isRecurring) {
                    val firedCal = Calendar.getInstance().apply { timeInMillis = alarm.triggerTimeMillis }
                    val hour = firedCal.get(Calendar.HOUR_OF_DAY)
                    val minute = firedCal.get(Calendar.MINUTE)
                    val nextTrigger = if (alarm.recurringDays == null || alarm.recurringDays.size == 7) {
                        com.example.alarmchatapp.utils.AlarmHelper.computeNextDaily(hour, minute)
                    } else {
                        com.example.alarmchatapp.utils.AlarmHelper.computeNextAmongDays(hour, minute, alarm.recurringDays)
                    }
                    dao.update(alarm.copy(triggerTimeMillis = nextTrigger))
                    com.example.alarmchatapp.utils.AlarmHelper.scheduleAlarmClockPublic(
                        context, alarm.message, nextTrigger, alarm.id, alarm.initialNote ?: ""
                    )
                } else {
                    dao.delete(alarm)
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val cancelPi = PendingIntent.getBroadcast(
                        context, id, Intent(context, AlarmReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or immutable()
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

    private fun immutable(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun isPlayable(u: Uri?, ctx: Context): Boolean {
        if (u == null) return false
        return try {
            ctx.contentResolver.openAssetFileDescriptor(u, "r")?.close()
            true
        } catch (_: Exception) { false }
    }

    private fun stopAudio() {
        runCatching { AlarmAudio.ringtone?.let { if (it.isPlaying) it.stop() } }
        AlarmAudio.ringtone = null
        runCatching { AlarmAudio.vibrator?.cancel() }
        AlarmAudio.vibrator = null
    }
}
