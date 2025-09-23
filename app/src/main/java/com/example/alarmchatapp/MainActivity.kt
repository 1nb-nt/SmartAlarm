package com.example.alarmchatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.alarmchatapp.ui.ChatScreen
import com.example.alarmchatapp.ui.theme.AlarmChatAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AlarmChatAppTheme {
                ChatScreen(onShow = { /* Navigate to AlarmListScreen */ })
            }
        }
    }
}
