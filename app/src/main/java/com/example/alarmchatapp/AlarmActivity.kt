package com.example.alarmchatapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarmchatapp.utils.AlarmHelper

class AlarmActivity : ComponentActivity() {

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

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(30.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Primary line: show initial note if present, else message
                        val primaryText = if (initialNote.isNotBlank()) initialNote else message

                        Text(
                            text = primaryText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(iterations = Int.MAX_VALUE),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Optional secondary line: show message only when a distinct note exists
                        if (initialNote.isNotBlank() && initialNote != message) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = message,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFBDBDBD),
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.height(28.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedButton(onClick = {
                                val trigger = System.currentTimeMillis() + 5 * 60_000
                                AlarmHelper.scheduleAlarmClockPublic(
                                    context = this@AlarmActivity,
                                    label = message,
                                    triggerAt = trigger,
                                    alarmId = (trigger % Int.MAX_VALUE).toInt(),
                                    initialNote = initialNote
                                )
                                sendDismiss(id)
                                finish()
                            }) { Text("Snooze 5 min") }

                            Button(onClick = { sendDismiss(id); finish() }) { Text("Dismiss") }
                        }
                    }

                }
            }
        }
    }

    private fun sendDismiss(id: Int) {
        // Let AlarmReceiver handle stopping audio + canceling notification
        sendBroadcast(
            Intent(this, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_DISMISS)
                .putExtra(AlarmReceiver.EXTRA_ID, id)
        )
    }
}
