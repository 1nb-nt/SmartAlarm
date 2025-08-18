package com.example.alarmchatapp.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.R
import com.example.alarmchatapp.ScheduledTask
import com.example.alarmchatapp.TaskExecutionWorker
import com.example.alarmchatapp.MyAlarmSetWorker
import com.example.alarmchatapp.network.AlarmApiRequest
import com.example.alarmchatapp.network.RetrofitClient
import com.example.alarmchatapp.utils.AlarmHelper
import com.example.alarmchatapp.utils.LocationUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val messageList = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf(TextFieldValue("")) }

    // Permissions setup (same as before)
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        schedulePeriodicTaskChecker(context)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notifyLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {}
        LaunchedEffect(Unit) { notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    // --- UI (unchanged as per your request) ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    Toast.makeText(context, "Settings clicked", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("grox", style = MaterialTheme.typography.bodyMedium)
            }
        }

        val wowLogo: Painter = painterResource(id = R.drawable.wow_logo)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(painter = wowLogo, contentDescription = "WOW Logo", modifier = Modifier.size(140.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "What You Want to be notified at",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    val rawLabel = "Wake up"
                    val (h, m) = parseHourMinute("07:00")
                    if (h != null && m != null) {
                        AlarmHelper.setAlarmInClockApp(context, rawLabel, h, m, isRecurring = false)
                        messageList.add(0, "✅ Clock alarm scheduled for 07:00")
                    }
                }) { Text("Wake me up") }

                Button(onClick = {
                    val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val timeStr = sdf.format(cal.time)
                    val (h, m) = parseHourMinute(timeStr)
                    if (h != null && m != null) {
                        AlarmHelper.setAlarmInClockApp(context, "Quick reminder", h, m, isRecurring = false)
                        messageList.add(0, "✅ Clock reminder scheduled at $timeStr")
                    }
                }) { Text("Remind me") }

                Button(onClick = {
                    val loc = LocationUtils.getLastKnownLocation(context)
                    messageList.add(0, "🔌 Connected tools — ${loc ?: "Location unavailable"}")
                }) { Text("Connect tools") }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp)
                .padding(horizontal = 12.dp),
            reverseLayout = true
        ) {
            items(messageList) { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(text = msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Set an alarm via API...") },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                val rawText = input.text.trim()
                if (rawText.isEmpty()) return@IconButton

                messageList.add(0, "👤: $rawText")
                input = TextFieldValue("")

                coroutineScope.launch {
                    try {
                        // --- 1. Send input to API ---
                        messageList.add(0, "🤖 Calling API...")
                        val request = AlarmApiRequest(query = rawText)
                        val response = RetrofitClient.instance.getAlarmDetails(request)
                        // At this point, you have a parsed JSON AlarmApiResponse from your server:
                        val alarmTitle = response.title
                        val eventDateString = response.datetime

                        // --- 2. Parse event datetime (as per your app's backend format) ---
                        val eventDate = parseApiOrCustomDate(eventDateString, rawText)
                        if (eventDate == null) {
                            messageList.add(0, "⚠️ Could not parse event date/time. Please use 'dd-MM-yyyy at hh:mm am/pm'.")
                            return@launch
                        }
                        val eventMillis = eventDate.time
                        val triggerMillis = eventMillis - TimeUnit.HOURS.toMillis(24)
                        val nowMillis = System.currentTimeMillis()

                        // --- 3. Schedule with WorkManager or alarm directly ---
                        if (triggerMillis > nowMillis) {
                            // Set alarm via WorkManager 24hrs before event
                            val workRequest = OneTimeWorkRequestBuilder<MyAlarmSetWorker>()
                                .setInitialDelay(triggerMillis - nowMillis, TimeUnit.MILLISECONDS)
                                .setInputData(
                                    workDataOf(
                                        "ALARM_TITLE" to alarmTitle,
                                        "EVENT_TIME" to eventMillis
                                    )
                                )
                                .build()
                            WorkManager.getInstance(context).enqueue(workRequest)
                            messageList.add(0, "✅ Alarm will be set in Clock app 24 hours before your event.")
                            showTaskScheduledNotification(
                                context,
                                "Alarm Scheduled",
                                "Alarm '$alarmTitle' will be set 24 hours before the event on ${formatDateTime(eventDate)}"
                            )
                        } else {
                            // Event is soon: set alarm in Clock app immediately
                            val cal = Calendar.getInstance().apply { timeInMillis = eventMillis }
                            AlarmHelper.setAlarmInClockApp(
                                context,
                                alarmTitle,
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                isRecurring = false
                            )
                            messageList.add(0, "✅ Event is soon or past; alarm set in your Clock app.")
                            showTaskScheduledNotification(
                                context,
                                "Alarm Scheduled",
                                "Alarm '$alarmTitle' set immediately for ${formatDateTime(eventDate)}"
                            )
                        }

                    } catch (e: Exception) {
                        Log.e("ChatScreen", "API or alarm scheduling failed", e)
                        messageList.add(0, "⚠️ Error: Could not reach server or process request.")
                    }
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}


// --- Helpers ---

// Tries both API datetime string, and fallback to trying the custom pattern in user's message
private fun parseApiOrCustomDate(apiDate: String, userInput: String): Date? {
    if (apiDate.isNotBlank()) {
        val isoFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (f in isoFormats) {
            try { return SimpleDateFormat(f, Locale.getDefault()).parse(apiDate) } catch(_: Exception) { }
        }
    }
    // Try custom format found in userInput
    try {
        val datePart = Regex("""\b(\d{2}-\d{2}-\d{4})\b""").find(userInput)?.value
        val timePart = Regex("""\b(\d{1,2}:\d{2}\s*[apAP][mM])\b""").find(userInput)?.value
        if (datePart != null && timePart != null) {
            val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm aa", Locale.getDefault())
            return sdf.parse("$datePart $timePart")
        }
    } catch(_: Exception) {}
    return null
}

private fun formatDateTime(date: Date): String =
    SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(date)

private fun showTaskScheduledNotification(context: Context, title: String, message: String) {
    val channelId = "TASK_SCHEDULED_CHANNEL"
    val notificationId = System.currentTimeMillis().toInt()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId, "Task Notifications", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for successfully scheduled tasks"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    } else {
        Toast.makeText(context, "$title: $message", Toast.LENGTH_LONG).show()
    }
}

private fun schedulePeriodicTaskChecker(context: Context) {
    val periodicWorkRequest = androidx.work.PeriodicWorkRequestBuilder<TaskExecutionWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "PeriodicTaskChecker",
        androidx.work.ExistingPeriodicWorkPolicy.KEEP,
        periodicWorkRequest
    )
    Log.d("ChatScreen", "Periodic task checker scheduled.")
}

private fun parseHourMinute(text: String): Pair<Int?, Int?> {
    return try {
        val parts = text.split(":")
        Pair(parts[0].toIntOrNull(), parts[1].toIntOrNull())
    } catch (_: Exception) {
        Pair(null, null)
    }
}

private fun displayLocationInAppMap(context: Context, locationName: String) {
    Toast.makeText(context, "Displaying '$locationName' on in-app map (Not implemented).", Toast.LENGTH_LONG).show()
    Log.d("ChatScreen", "Attempted to display '$locationName' on in-app map.")
}
