package com.example.alarmchatapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alarmchatapp.Alarm
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.utils.AlarmHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlarmListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val items = remember { mutableStateListOf<Alarm>() }

    LaunchedEffect(Unit) {
        val dao = AppDatabase.getDatabase(context).alarmDao()
        val data = withContext(Dispatchers.IO) { dao.getAll() }
        items.clear()
        items.addAll(data)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Alarms", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(items, key = { it.id }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    onDelete = {
                        runBlocking {
                            val dao = AppDatabase.getDatabase(context).alarmDao()
                            // Cancel the scheduled PendingIntent first
                            AlarmHelper.cancelAlarm(context, alarm.id)
                            withContext(Dispatchers.IO) { dao.delete(alarm) }
                        }
                        items.remove(alarm)
                    }
                )
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    onDelete: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("EEE, dd MMM yyyy HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = alarm.message, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(text = sdf.format(Date(alarm.triggerTimeMillis)), style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}
