package com.example.alarmchatapp.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
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
import com.example.alarmchatapp.utils.AlarmHelper // Import the helper
import com.example.alarmchatapp.utils.LocationUtils
import kotlinx.coroutines.launch
import java.text.ParseException
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

        // --- Interactive Section ---
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
                    val (h, m) = parseHourMinute("07:00")
                    if (h != null && m != null) AlarmHelper.setAlarmInClockApp(context, "Wake up", h, m, isRecurring = true)
                }) { Text("Wake me up") }

                Button(onClick = {
                    val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
                    AlarmHelper.scheduleInAppAlarm(context, "Quick reminder", cal.timeInMillis)
                }) { Text("Remind me") }

                Button(onClick = {
                    val loc = LocationUtils.getLastKnownLocation(context)
                    messageList.add(0, "🔌 Connected tools — ${loc ?: "Location unavailable"}")
                }) { Text("Connect tools") }
            }
        }

        // --- Message List and Input Bar ---
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
                placeholder = { Text("e.g., '...from 01-01-25 to 05-01-25 at 10:00'") },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                val rawText = input.text.trim().lowercase(Locale.getDefault())
                if (rawText.isEmpty()) return@IconButton
                when {
                    rawText.startsWith("task") -> {
                        try {
                            if (rawText.contains(" from ") && rawText.contains(" to ") && rawText.contains(" at ")) {
                                // --- RANGED TASK ---
                                val description = rawText.substringAfter("task").substringBefore(" from ").trim()
                                val fromDateString = rawText.substringAfter(" from ").substringBefore(" to ").trim()
                                val toDateString = rawText.substringAfter(" to ").substringBefore(" at ").trim()
                                val timeString = rawText.substringAfter(" at ").trim()
                                val sdfDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val startDate = sdfDate.parse(fromDateString)!!
                                val endDate = sdfDate.parse(toDateString)!!
                                val time = sdfTime.parse(timeString)!!
                                val startCal = Calendar.getInstance().apply { this.time = startDate }
                                val timeCal = Calendar.getInstance().apply { this.time = time }
                                startCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                                startCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                                val endCal = Calendar.getInstance().apply { this.time = endDate; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }
                                val task = ScheduledTask(description = description, executionTimeMillis = startCal.timeInMillis, isRecurring = true, endDateMillis = endCal.timeInMillis)
                                coroutineScope.launch { AppDatabase.getDatabase(context).scheduledTaskDao().insert(task) }

                                // **FIX**: Use the precise in-app alarm for the first day of the range.
                                AlarmHelper.scheduleInAppAlarm(context, description, startCal.timeInMillis)

                                showTaskScheduledNotification(context, "Ranged Task Scheduled", "'$description' from $fromDateString to $toDateString at $timeString (In-App)")

                            } else if (rawText.contains(" on ") && rawText.contains(" at ")) {
                                // --- ONE-TIME OR INFINITE DAILY ---
                                val isRecurring = rawText.contains(" daily")
                                val cleanText = rawText.replace(" daily", "").trim()
                                val description = cleanText.substringAfter("task").substringBefore(" on ").trim()
                                val fullDateTimeString = cleanText.substringAfter(" on ").trim()
                                val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
                                val date = sdf.parse(fullDateTimeString)!!
                                val task = ScheduledTask(description = description, executionTimeMillis = date.time, isRecurring = isRecurring, endDateMillis = null)
                                coroutineScope.launch { AppDatabase.getDatabase(context).scheduledTaskDao().insert(task) }

                                val typeText: String
                                if (isRecurring) {
                                    // Use clock app for simple, infinite daily alarms
                                    val cal = Calendar.getInstance().apply { this.time = date }
                                    AlarmHelper.setAlarmInClockApp(context, description, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), isRecurring = true)
                                    typeText = " (Daily, in Clock App)"
                                } else {
                                    // **FIX**: Use precise in-app alarm for one-time future dates
                                    AlarmHelper.scheduleInAppAlarm(context, description, date.time)
                                    typeText = " (In-App)"
                                }
                                showTaskScheduledNotification(context, "Task Scheduled", "'$description' for $fullDateTimeString$typeText")
                            } else {
                                throw IllegalArgumentException("Unrecognized task format.")
                            }
                        } catch (e: Exception) {
                            Log.e("ChatScreen", "Task parsing error", e)
                            messageList.add(0, "⚠️ Invalid format. Use '... on [date] at [time]' or '... from [date] to [date] at [time]'")
                        }
                    }
                    rawText.startsWith("navigate to") || rawText.startsWith("open map") -> {
                        val locationQuery = rawText.substringAfter("to").trim()
                        if (locationQuery.isNotEmpty()) {
                            openLocationInGoogleMaps(context, locationQuery)
                            messageList.add(0, "🗺 Opening location: $locationQuery")
                        } else {
                            messageList.add(0, "⚠️ No location specified")
                        }
                    }
                    else -> {
                        val extracted = extractTimeFromText(rawText)
                        if (extracted != null) {
                            val parsed = parseToHourMinute(extracted)
                            if (parsed != null) {
                                val (hour, minute) = parsed
                                AlarmHelper.setAlarmInClockApp(context, rawText, hour, minute)
                            } else {
                                messageList.add(0, "⚠️ Could not parse time \"$extracted\"")
                            }
                        } else {
                            messageList.add(0, "❓ Command not recognized.")
                        }
                    }
                }
                input = TextFieldValue("")
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

// --- Helper Functions ---

// NOTE: The local setAlarmCrossDevice function is now removed from this file.
// Its logic has been moved to AlarmHelper.kt and split into two separate functions.

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
    with(NotificationManagerCompat.from(context)) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notify(notificationId, builder.build())
        } else {
            Toast.makeText(context, "$title: $message", Toast.LENGTH_LONG).show()
        }
    }
}

private fun schedulePeriodicTaskChecker(context: Context) {
    val periodicWorkRequest = PeriodicWorkRequestBuilder<TaskExecutionWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("PeriodicTaskChecker", ExistingPeriodicWorkPolicy.KEEP, periodicWorkRequest)
    Log.d("ChatScreen", "Periodic task checker has been scheduled.")
}

private fun extractTimeFromText(text: String): String? {
    val regex = Regex("\\b\\d{1,2}:\\d{2}(?:\\s?[ap]m)?\\b", RegexOption.IGNORE_CASE)
    return regex.find(text)?.value
}

private fun parseToHourMinute(text: String): Pair<Int, Int>? {
    val patterns = listOf("H:mm", "HH:mm", "h:mm a", "hh:mm a")
    for (p in patterns) {
        try {
            val sdf = SimpleDateFormat(p, Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(text.uppercase(Locale.getDefault()))
            if (date != null) {
                val cal = Calendar.getInstance().apply { time = date }
                return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            }
        } catch (_: ParseException) { /* try next */ }
    }
    return null
}

private fun parseHourMinute(text: String): Pair<Int?, Int?> {
    return try {
        val parts = text.split(":")
        Pair(parts[0].toInt(), parts[1].toInt())
    } catch (_: Exception) {
        Pair(null, null)
    }
}

private fun openLocationInGoogleMaps(context: Context, locationName: String) {
    try {
        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(locationName)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            if (fallbackIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(fallbackIntent)
            } else {
                Toast.makeText(context, "No map app found", Toast.LENGTH_LONG).show()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error opening map: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
