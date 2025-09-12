package com.example.alarmchatapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
@Composable
fun AlarmListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).alarmDao() }
    var alarms by remember { mutableStateOf<List<Alarm>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    // Load alarms on first composition and after deletions
    LaunchedEffect(Unit) {
        try {
            alarms = dao.getAll()
        } catch (e: Exception) {
            // Handle DAO exceptions, e.g. log or show error message
        }
    }

    Column(modifier = Modifier.fillMaxSize() .background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) {
                Text("Back to Chat")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(alarms) { alarm ->
                AlarmItem(alarm = alarm, onDelete = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            AlarmHelper.cancelScheduledAlarm(context, alarm.id)
                            dao.delete(alarm)
                            alarms = dao.getAll()
                        } catch (e: Exception) {
                            // Handle deletion failures if required
                        }
                    }
                })
            }
        }
    }
}

@Composable
fun AlarmItem(alarm: Alarm, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = alarm.message,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete Alarm")
        }
    }
}
