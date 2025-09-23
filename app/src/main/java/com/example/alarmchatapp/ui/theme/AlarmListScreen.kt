package com.example.alarmchatapp.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.AppDatabase
import androidx.compose.ui.platform.LocalContext

@Composable
fun AlarmListScreen() {
    val context = LocalContext.current
    val dao = AppDatabase.getDatabase(context).alarmDao()
    var alarms by remember { mutableStateOf(dao.getAll()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        items(alarms) { alarm ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Label: ${alarm.label}")
                    Text("Time: ${java.util.Date(alarm.timeMillis)}")
                    if (alarm.recurringDays != null) {
                        Text("Recurring: ${alarm.recurringDays}")
                    }
                }
            }
        }
    }
}
