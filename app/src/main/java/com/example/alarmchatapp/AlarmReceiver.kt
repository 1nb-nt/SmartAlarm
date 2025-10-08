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
        const val EXTRA_LABEL = "alarm_message"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_NOTE = "initial_note"
        private const val NOTIF_ID_BASE = 52001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra(EXTRA_LABEL) ?: "Alarm"
        val initialNote = intent.getStringExtra(EXTRA_NOTE) ?: ""
        val id = intent.getIntExtra(EXTRA_ID, 0)
        if (id == 0) {
            Log.w("AlarmReceiver", "Missing EXTRA_ID")
            return
        }
        if (!firedIds.add(id)) {
            Log.d("AlarmReceiver", "Duplicate delivery for id=$id, skipping")
            return
        }

        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    description = "Ringing alarms"
                    enableVibration(true)
                    setSound(null, null) // manage sound via Ringtone, not channel
                }
                nm.createNotificationChannel(ch)
            }
        }

        // Ensure any previous tone is stopped to avoid overlaps
        runCatching { AlarmAudio.ringtone?.let { if (it.isPlaying) it.stop() } }.onFailure {
            Log.w("AlarmReceiver", "Failed stopping previous tone: ${it.message}")
        }

        // Resolve saved ringtone URI from SharedPreferences
        val prefs = context.getSharedPreferences("wow_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("ringtone_uri", null)
        val chosen: Uri? = saved?.let { runCatching { Uri.parse(it) }.getOrNull() }

        fun isPlayable(uri: Uri?, ctx: Context): Boolean {
            if (uri == null) return false
            return try {
                ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.close()
                true
            } catch (_: Exception) {
                false
            }
        }

        val fallback: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val playUri: Uri = if (isPlayable(chosen, context)) chosen!! else fallback

        // Start audio immediately in the receiver (looping)
        val tone: Ringtone? = try {
            RingtoneManager.getRingtone(context, playUri)
        } catch (se: SecurityException) {
            Log.w("AlarmReceiver", "No read permission for chosen URI, fallback. ${se.message}")
            runCatching { RingtoneManager.getRingtone(context, fallback) }.getOrNull()
        }

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
            runCatching { rt.play() }.onFailure {
                Log.e("AlarmReceiver", "Ringtone play failed: ${it.message}")
            }
            AlarmAudio.ringtone = rt
        }

        // Vibrate pattern
        val vibrator = context.getSystemService(Vibrator::class.java)
        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
        AlarmAudio.vibrator = vibrator

        // Full-screen activity intent
        val fullIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_LABEL, message)
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_NOTE, initialNote)
        }
        val fullPi = PendingIntent.getActivity(
            context,
            id,
            fullIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissPi = PendingIntent.getBroadcast(
            context,
            id + 1000,
            Intent(context, DismissReceiver::class.java)
                .setAction(ACTION_DISMISS)
                .putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(0) // no channel sound; we handle sound ourselves
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(0, "Dismiss", dismissPi)
            .setFullScreenIntent(fullPi, true)
            .build()

        nm.notify(NOTIF_ID_BASE + id, notif)

        // Handle DB updates/rescheduling off the main thread
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).alarmDao()
                val alarm = dao.getById(id) ?: return@launch

                if (alarm.isRecurring) {
                    val firedCal = Calendar.getInstance().apply {
                        timeInMillis = alarm.triggerTimeMillis
                    }
                    val hour = firedCal.get(Calendar.HOUR_OF_DAY)
                    val minute = firedCal.get(Calendar.MINUTE)

                    val nextTrigger: Long? = when {
                        alarm.recurringDays?.size == 7 -> {
                            Calendar.getInstance().apply {
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                add(Calendar.DAY_OF_YEAR, 1)
                            }.timeInMillis
                        }
                        !alarm.recurringDays.isNullOrEmpty() -> {
                            com.example.alarmchatapp.utils.AlarmHelper
                                .computeNextAmongDays(hour, minute, alarm.recurringDays!!)
                        }
                        else -> null
                    }

                    if (nextTrigger != null) {
                        dao.update(alarm.copy(triggerTimeMillis = nextTrigger))
                        com.example.alarmchatapp.utils.AlarmHelper
                            .scheduleAlarmClockPublic(context, alarm.message, nextTrigger, alarm.id)
                    } else {
                        dao.delete(alarm)
                    }
                } else {
                    dao.delete(alarm)
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val cancelPi = PendingIntent.getBroadcast(
                        context,
                        id,
                        Intent(context, AlarmReceiver::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
