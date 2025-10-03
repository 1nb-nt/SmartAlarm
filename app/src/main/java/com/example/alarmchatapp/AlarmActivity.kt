package com.example.alarmchatapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmchatapp.utils.AlarmHelper

class AlarmActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            stopRinging()
            finishAndRemoveTask()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        val message = intent.getStringExtra(AlarmReceiver.EXTRA_LABEL) ?: "Alarm"
        val initialNote = intent.getStringExtra(AlarmReceiver.EXTRA_NOTE) ?: ""
        val id = intent.getIntExtra(AlarmReceiver.EXTRA_ID, 0)
        getSystemService(NotificationManager::class.java).cancel(id)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Start or adopt shared audio objects
        if (AlarmAudio.ringtone == null || AlarmAudio.vibrator == null) {
            requestAlarmAudioFocus()
            setAlarmVolumeLoud()
            startRingingAndVibrating()
            AlarmAudio.ringtone = ringtone
            AlarmAudio.vibrator = vibrator
        } else {
            ringtone = AlarmAudio.ringtone
            vibrator = AlarmAudio.vibrator
        }

        // Register stop receiver (for notification dismiss action)
        val filter = IntentFilter("com.example.alarmchatapp.ACTION_STOP_RING")
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED); true
            } else false
        }.getOrDefault(false)
        if (!registered) {
            @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopReceiver, filter)
        }

        setContent {
            AlarmScreen(
                message = message,
                initialNote = initialNote,
                onDismiss = {
                    stopRinging()
                    finish()
                },
                onSnooze = {
                    // Simple 5‑minute snooze using the same label
                    val trigger = System.currentTimeMillis() + 5 * 60_000
                    AlarmHelper.scheduleAlarmClockPublic(
                        context = this,
                        label = message,
                        triggerAt = trigger,
                        alarmId = 0 // new alarm row not persisted here; adjust if you want to store
                    )
                    stopRinging()
                    finish()
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun requestAlarmAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener { }
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            audioFocusRequest = afr
            am.requestAudioFocus(afr)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
    }

    private fun setAlarmVolumeLoud() {
        val am = audioManager ?: return
        @Suppress("DEPRECATION")
        am.setStreamVolume(
            AudioManager.STREAM_ALARM,
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )
    }

    private fun startRingingAndVibrating() {
        val prefs = getSharedPreferences("wow_prefs", MODE_PRIVATE)
        val saved = prefs.getString("ringtone_uri", null)
        val preferredUri: Uri? = saved?.let { runCatching { Uri.parse(it) }.getOrNull() }

        // Prefer user‑selected Uri; fallback to system defaults only if null or play fails
        val chosenUri = preferredUri
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        ringtone = runCatching { RingtoneManager.getRingtone(this, chosenUri) }.getOrNull()?.apply {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }.onFailure {
                // If user-picked Uri failed, try a pure default as last resort
                val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                runCatching { RingtoneManager.getRingtone(this@AlarmActivity, fallback) }
                    .onSuccess { fb ->
                        fb?.apply {
                            audioAttributes = this@apply.audioAttributes
                            play()
                        }
                    }
            }
        }

        vibrator = getSystemService(Vibrator::class.java)
        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopRinging() {
        runCatching { AlarmAudio.ringtone?.stop() }
        AlarmAudio.ringtone = null
        runCatching { AlarmAudio.vibrator?.cancel() }
        AlarmAudio.vibrator = null

        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null

        abandonAlarmAudioFocus()
    }

    private fun abandonAlarmAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(stopReceiver) }
        stopRinging()
        super.onDestroy()
    }
}

@Composable
fun AlarmScreen(
    message: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(30.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (initialNote.isNotBlank()) {
                MarqueeCenter(text = initialNote)
                Spacer(modifier = Modifier.height(24.dp))
            }
            Text(text = message, color = Color.White, fontSize = 30.sp)
            Spacer(modifier = Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onSnooze) { Text("Snooze 5 min") }
                Button(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
fun MarqueeCenter(
    text: String,
    width: Dp = 280.dp,
    fontSize: Int = 18,
    color: Color = Color(0xFFFFF59D)
) {
    val density = LocalDensity.current
    val anim = rememberInfiniteTransition(label = "marquee")
    val progress by anim.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "progress"
    )
    Box(
        modifier = Modifier.width(width).height(28.dp).clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val rangePx = with(density) { width.toPx() }
        val tx = progress * rangePx
        Text(
            text = text,
            color = color,
            fontSize = fontSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.graphicsLayer { translationX = tx }
        )
    }
}
