package com.example.alarmchatapp

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
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmchatapp.NotificationHelper

class AlarmActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var vibratorLegacy: Vibrator? = null
    private var alarmId: Int = 0

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.alarmchatapp.STOP_RING" -> stopRinging()
                "com.example.alarmchatapp.FINISH_ALARM" -> finish()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen flags
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

        // Register receiver
        registerReceiver(
            dismissReceiver,
            IntentFilter().apply {
                addAction("com.example.alarmchatapp.STOP_RING")
                addAction("com.example.alarmchatapp.FINISH_ALARM")
            },
            RECEIVER_NOT_EXPORTED
        )

        // Extract extras
        alarmId = intent.getIntExtra("ALARM_ID", 0)
        val message = intent.getStringExtra("alarm_message") ?: "Alarm"
        val initialNote = intent.getStringExtra("INITIAL_NOTE").orEmpty()

        // Setup audio + ringtone
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val alarmTone: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(applicationContext, alarmTone)?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            streamType = AudioManager.STREAM_ALARM
        }

        // Start playback + vibration
        requestAlarmAudioFocus()
        ringtone?.play()
        startAlarmVibration()

        // Compose UI
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(30.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = message, color = Color.White, fontSize = 30.sp)
                        Spacer(Modifier.height(24.dp))
                        if (initialNote.isNotBlank()) {
                            AlarmNoteMarquee(note = initialNote)
                            Spacer(Modifier.height(24.dp))
                        }
                        Button(onClick = {
                            stopRinging()
                            NotificationHelper.cancelAlarmNotification(this@AlarmActivity, alarmId)
                            finish()
                        }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }

    private fun requestAlarmAudioFocus() { /* unchanged */ }

    private fun abandonAlarmAudioFocus() { /* unchanged */ }

    private fun startAlarmVibration() { /* unchanged */ }

    private fun stopRinging() {
        try { ringtone?.stop() } catch (_: Exception) { }
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.cancel()
        } else {
            vibratorLegacy?.cancel()
        }
        abandonAlarmAudioFocus()
    }

    override fun onDestroy() {
        stopRinging()
        NotificationHelper.cancelAlarmNotification(this, alarmId)
        unregisterReceiver(dismissReceiver)
        super.onDestroy()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmNoteMarquee(note: String) { /* unchanged */ }
