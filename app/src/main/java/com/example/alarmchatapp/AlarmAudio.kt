package com.example.alarmchatapp

import android.media.Ringtone
import android.os.Vibrator

object AlarmAudio {
    @Volatile var ringtone: Ringtone? = null
    @Volatile var vibrator: Vibrator? = null
}
