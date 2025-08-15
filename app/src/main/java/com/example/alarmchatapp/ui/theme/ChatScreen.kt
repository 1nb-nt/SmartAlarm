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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.R
import com.example.alarmchatapp.ScheduledTask
import com.example.alarmchatapp.TaskExecutionWorker
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

    // --- Permission and Periodic Work Setup ---
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Top Bar and Logo ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                Toast.makeText(context, "Settings clicked", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("grox", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val wowLogo: Painter = painterResource(id = R.drawable.wow_logo)
        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            Image(painter = wowLogo, contentDescription = "WOW Logo", modifier = Modifier.size(140.dp))
        }

        // --- Vertically Centered Interactive Section ---
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
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = {
                    val raw = "Wake up"
                    val (h, m) = parseHourMinute("07:00")
                    if (h != null && m != null) {
                        AlarmHelper.setAlarmInClockApp(context, raw, h, m, isRecurring = false)
                        messageList.add(0, "✅ Clock alarm scheduled for 07:00")
                    }
                }) { Text("Wake me up") }

                Button(onClick = {
                    val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val t = sdf.format(cal.time)
                    val (h, m) = parseHourMinute(t)
                    if (h != null && m != null) {
                        AlarmHelper.setAlarmInClockApp(context, "Quick reminder", h, m, isRecurring = false)
                        messageList.add(0, "✅ Clock reminder scheduled at $t")
                    }
                }) { Text("Remind me") }

                Button(onClick = {
                    val loc = LocationUtils.getLastKnownLocation(context)
                    messageList.add(0, "🔌 Connected tools — ${loc ?: "Location unavailable"}")
                }) { Text("Connect tools") }
            }
        }

        // --- Bottom Section (Message List and Input Bar) ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp)
                .padding(horizontal = 12.dp),
            reverseLayout = true
        ) {
            items(messageList) { msg ->
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(text = msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                input = TextFieldValue("") // Clear input

                coroutineScope.launch {
                    val command = rawText.lowercase(Locale.getDefault())
                    when {
                        // Logic for saving a scheduled task to the database
                        command.startsWith("task") -> {
                            val description = command.substringAfter("task").substringBefore(" on ").trim()
                            val dateString = command.substringAfter(" on ").trim()
                            try {
                                val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                                val date = sdf.parse(dateString)
                                if (date != null && description.isNotEmpty()) {
                                    val task = ScheduledTask(description = description, executionTimeMillis = date.time)
                                    AppDatabase.getDatabase(context).scheduledTaskDao().insert(task)
                                    showTaskScheduledNotification(context, "Task Saved", "'$description' is saved for $dateString")
                                } else {
                                    messageList.add(0, "⚠️ Could not parse command. Use 'task [description] on [dd-MM-yyyy]'")
                                }
                            } catch (e: Exception) {
                                messageList.add(0, "⚠️ Invalid date format. Use 'dd-MM-yyyy'.")
                            }
                        }

                        // Logic for in-app navigation
                        command.startsWith("navigate to") || command.startsWith("open map") -> {
                            val locationQuery = command.substringAfter("to").trim()
                            if (locationQuery.isNotEmpty()) {
                                displayLocationInAppMap(context, locationQuery)
                                messageList.add(0, "🗺 Displaying location in-app: $locationQuery")
                            } else {
                                messageList.add(0, "⚠️ No location specified")
                            }
                        }

                        // Fallback logic for alarms using the API
                        else -> {
                            try {
                                messageList.add(0, "🤖 Calling API...")
                                val request = AlarmApiRequest(query = rawText)
                                val response = RetrofitClient.instance.getAlarmDetails(request)

                                // --- START OF CORRECTED CODE BLOCK ---
                                val date = parseIsoDateTime(response.datetime) // Use the new robust function

                                if (date != null) {
                                    // The date was parsed successfully!
                                    val cal = Calendar.getInstance().apply { time = date }
                                    val task = ScheduledTask(
                                        description = response.title,
                                        executionTimeMillis = date.time,
                                        isRecurring = (response.recurrence != "once")
                                    )
                                    AppDatabase.getDatabase(context).scheduledTaskDao().insert(task)

                                    val shouldBeRecurring = (response.recurrence != "once")
                                    AlarmHelper.setAlarmInClockApp(
                                        context,
                                        response.title,
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE),
                                        isRecurring = shouldBeRecurring
                                    )

                                    val recurringText = if (shouldBeRecurring) " (daily)" else ""
                                    val confirmationMessage = "'${response.title}' scheduled in Clock App$recurringText."
                                    messageList.add(0, "✅ $confirmationMessage")
                                    showTaskScheduledNotification(context, "Alarm Scheduled", confirmationMessage)
                                } else {
                                    // The date could not be parsed, show a friendly error instead of crashing
                                    Log.e("ChatScreen", "Could not parse date from API: ${response.datetime}")
                                    messageList.add(0, "⚠️ Error: Could not understand the date from the API.")
                                }
                                // --- END OF CORRECTED CODE BLOCK ---

                            } catch (e: Exception) {
                                Log.e("ChatScreen", "API call or scheduling failed", e)
                                messageList.add(0, "⚠️ Error: Request failed. Could not reach server.")
                            }
                        }
                    }
                }
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

// --- Helper Functions ---

// **NEW**: A more robust function to parse various ISO date/time formats
private fun parseIsoDateTime(dateTimeStr: String): Date? {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",  // Format with timezone offset (e.g., +05:30)
        "yyyy-MM-dd'T'HH:mm:ss'Z'",   // Format with 'Z' for UTC
        "yyyy-MM-dd'T'HH:mm:ss"      // Format without timezone
    )
    for (format in formats) {
        try {
            // Using Locale.US is safer for machine-generated timestamps
            return SimpleDateFormat(format, Locale.getDefault()).parse(dateTimeStr)
        } catch (e: Exception) {
            // Ignore and try the next format
        }
    }
    Log.w("ChatScreen", "All parsing attempts failed for date: $dateTimeStr")
    return null // Return null if all formats fail
}

private fun showTaskScheduledNotification(context: Context, title: String, message: String) {
    val channelId = "TASK_SCHEDULED_CHANNEL"
    val notificationId = System.currentTimeMillis().toInt()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Task Notifications"
        val descriptionText = "Notifications for successfully scheduled tasks"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
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
    val periodicWorkRequest = PeriodicWorkRequestBuilder<TaskExecutionWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "PeriodicTaskChecker",
        ExistingPeriodicWorkPolicy.KEEP,
        periodicWorkRequest
    )
    Log.d("ChatScreen", "Periodic task checker has been scheduled.")
}

private fun parseHourMinute(text: String): Pair<Int?, Int?> {
    return try {
        val parts = text.split(":")
        Pair(parts[0].toInt(), parts[1].toInt())
    } catch (_: Exception) {
        Pair(null, null)
    }
}

private fun displayLocationInAppMap(context: Context, locationName: String) {
    // This is a placeholder for your actual map implementation
    Toast.makeText(context, "Displaying '$locationName' on in-app map (Not implemented).", Toast.LENGTH_LONG).show()
    Log.d("ChatScreen", "Attempted to display '$locationName' on in-app map.")
}
