package com.example.alarmchatapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AlarmListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).alarmDao() }
    var alarms by remember { mutableStateOf<List<Alarm>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun refresh() = scope.launch(Dispatchers.IO) {
        val list = runCatching { dao.getAll() }.getOrElse { emptyList() }
        withContext(Dispatchers.Main) { alarms = list }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) { Text("Back to Chat") }
            Spacer(Modifier.width(12.dp))
            Text("Manage Alarms", style = MaterialTheme.typography.titleMedium)
        }

        if (alarms.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No alarms scheduled")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = alarms, key = { it.id }) { alarm ->
                    AlarmItem(
                        alarm = alarm,
                        onDelete = {
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    // Derive hour/minute once for both paths
                                    val cal = Calendar.getInstance().apply { timeInMillis = alarm.triggerTimeMillis }
                                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                                    val minute = cal.get(Calendar.MINUTE)

                                    // First remove from Google Clock, then delete local row
                                    if (alarm.isRecurring && !alarm.recurringDays.isNullOrEmpty()) {
                                        AlarmHelper.cancelRecurringInClock(
                                            context = context,
                                            title = alarm.message,
                                            alarmId = alarm.id,
                                            hour = hour,
                                            minute = minute
                                        )
                                    } else {
                                        AlarmHelper.cancelOneShotInClock(
                                            context = context,
                                            title = alarm.message,
                                            alarmId = alarm.id,
                                            triggerAtMillis = alarm.triggerTimeMillis
                                        )
                                    }
                                    dao.delete(alarm)
                                }
                                refresh()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmItem(alarm: Alarm, onDelete: () -> Unit) {
    val timeStr = remember(alarm.triggerTimeMillis) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(alarm.triggerTimeMillis))
    }
    val dateStr = remember(alarm.triggerTimeMillis) {
        SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(alarm.triggerTimeMillis))
    }
    val dayStr = remember(alarm.triggerTimeMillis, alarm.recurringDays) {
        if (alarm.isRecurring && !alarm.recurringDays.isNullOrEmpty()) {
            alarm.recurringDays.joinToString(", ") { shortDayName(it) }
        } else {
            Calendar.getInstance().apply { timeInMillis = alarm.triggerTimeMillis }
                .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: "Day"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alarm.message.ifBlank { "Alarm" },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$timeStr • $dayStr • $dateStr",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete Alarm")
        }
    }
}

private fun shortDayName(calendarConst: Int): String = when (calendarConst) {
    Calendar.SUNDAY -> "Sun"
    Calendar.MONDAY -> "Mon"
    Calendar.TUESDAY -> "Tue"
    Calendar.WEDNESDAY -> "Wed"
    Calendar.THURSDAY -> "Thu"
    Calendar.FRIDAY -> "Fri"
    Calendar.SATURDAY -> "Sat"
    else -> "Day"
}
