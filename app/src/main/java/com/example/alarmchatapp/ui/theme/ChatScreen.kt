package com.example.alarmchatapp.ui

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.alarmchatapp.AppDatabase
import com.example.alarmchatapp.R
import com.example.alarmchatapp.ScheduledTask
import com.example.alarmchatapp.TaskExecutionWorker
import com.example.alarmchatapp.utils.AlarmHelper
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

    // --- Permission and Periodic Work Setup ---
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        schedulePeriodicTaskChecker(context) // Schedule the periodic checker
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
                        setAlarmCrossDevice(context, raw, h, m)
                        messageList.add(0, "✅ Requested alarm 07:00")
                    }
                }) { Text("Wake me up") }

                Button(onClick = {
                    val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val t = sdf.format(cal.time)
                    val (h, m) = parseHourMinute(t)
                    if (h != null && m != null) {
                        setAlarmCrossDevice(context, "Quick reminder", h, m)
                        messageList.add(0, "✅ Requested reminder at $t")
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
                placeholder = { Text("e.g. 'Task meeting on 25-12-2025'") },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                val rawText = input.text.trim().lowercase(Locale.getDefault())
                if (rawText.isEmpty()) return@IconButton

                when {
                    // Logic for saving a scheduled task to the database
                    rawText.startsWith("task") -> {
                        val description = rawText.substringAfter("task").substringBefore(" on ").trim()
                        val dateString = rawText.substringAfter(" on ").trim()
                        try {
                            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                            val date = sdf.parse(dateString)
                            if (date != null && description.isNotEmpty()) {
                                val task = ScheduledTask(description = description, executionTimeMillis = date.time)
                                coroutineScope.launch {
                                    AppDatabase.getDatabase(context).scheduledTaskDao().insert(task)
                                }
                                messageList.add(0, "✅ Task '$description' scheduled for $dateString")
                            } else {
                                messageList.add(0, "⚠️ Could not parse command. Use 'task [description] on [DD-MM-YYYY]'")
                            }
                        } catch (e: Exception) {
                            messageList.add(0, "⚠️ Invalid command format.")
                        }
                    }
                    // Logic for navigation
                    rawText.startsWith("navigate to") || rawText.startsWith("open map") -> {
                        val locationQuery = rawText.substringAfter("to").trim()
                        if (locationQuery.isNotEmpty()) {
                            openLocationInGoogleMaps(context, locationQuery)
                            messageList.add(0, "🗺 Opening location: $locationQuery")
                        } else {
                            messageList.add(0, "⚠️ No location specified")
                        }
                    }
                    // Fallback logic for alarms if time is mentioned
                    else -> {
                        val extracted = extractTimeFromText(rawText)
                        if (extracted != null) {
                            val parsed = parseToHourMinute(extracted)
                            if (parsed != null) {
                                val (hour, minute) = parsed
                                setAlarmCrossDevice(context, rawText, hour, minute)
                                messageList.add(0, "✅ Alarm request sent for $extracted")
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

private fun schedulePeriodicTaskChecker(context: Context) {
    val periodicWorkRequest = PeriodicWorkRequestBuilder<TaskExecutionWorker>(6, TimeUnit.HOURS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "PeriodicTaskChecker",
        ExistingPeriodicWorkPolicy.KEEP,
        periodicWorkRequest
    )
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

private fun setAlarmCrossDevice(context: Context, label: String, hour: Int, minute: Int) {
    try {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }
        val candidates = listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock", "com.miui.clock")
        for (pkg in candidates) {
            try {
                context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                    Toast.makeText(context, "Opening clock app — please set alarm ($label)", Toast.LENGTH_LONG).show()
                    return
                }
            } catch (_: Exception) {}
        }
        // Fallback using the old AlarmHelper to trigger the full-screen AlarmActivity
        AlarmHelper.scheduleInAppAlarm(context, label, hour, minute)
        Toast.makeText(context, "No compatible Clock app. Alarm scheduled inside app.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to request alarm: ${e.message}", Toast.LENGTH_LONG).show()
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
