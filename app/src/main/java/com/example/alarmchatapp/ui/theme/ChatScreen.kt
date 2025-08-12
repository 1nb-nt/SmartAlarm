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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.alarmchatapp.My24HourWorker
import com.example.alarmchatapp.R
import com.example.alarmchatapp.utils.AlarmHelper
import com.example.alarmchatapp.utils.LocationUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val messageList = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf(TextFieldValue("")) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

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
            // Styled Prompt Text
            Text(
                text = "What You Want to be notified at",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Quick options buttons
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = {
                    val raw = "Wake up"
                    val parsed = "07:00"
                    val (h, m) = parseHourMinute(parsed)
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

            Spacer(modifier = Modifier.height(8.dp))

            // --- NEW: WorkManager Button ---
            Button(onClick = {
                schedule24HourWork(context)
                messageList.add(0, "✅ Daily background work scheduled for the next 24 hours.")
            }) {
                Text("Schedule 24h Work")
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
                placeholder = { Text("e.g. 'Navigate to Paris'") },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = {
                val rawText = input.text.trim()
                if (rawText.isEmpty()) return@IconButton
                val extracted = extractTimeFromText(rawText)
                if (extracted != null) {
                    val parsed = parseToHourMinute(extracted)
                    if (parsed != null) {
                        val (hour, minute) = parsed
                        setAlarmCrossDevice(context, rawText, hour, minute)
                        messageList.add(0, "✅ Alarm request sent for $extracted — \"$rawText\"")
                    } else {
                        messageList.add(0, "⚠️ Could not parse time \"$extracted\"")
                    }
                }
                if (rawText.startsWith("navigate to", ignoreCase = true) || rawText.startsWith("open map", ignoreCase = true)) {
                    val locationQuery = rawText.substringAfter("to").trim()
                    if (locationQuery.isNotEmpty()) {
                        openLocationInGoogleMaps(context, locationQuery)
                        messageList.add(0, "🗺 Opening location: $locationQuery")
                    } else {
                        messageList.add(0, "⚠️ No location specified")
                    }
                }

                input = TextFieldValue("")
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

/**
 * NEW FUNCTION
 * Schedules a one-time work request to be executed within the next 24 hours
 * using WorkManager.
 */
private fun schedule24HourWork(context: Context) {
    // Create some input data to pass to the worker, if needed
    val workData = workDataOf("TASK_DATA" to "Sync daily report")

    // Build the work request
    val workRequest = OneTimeWorkRequestBuilder<My24HourWorker>()
        .setInitialDelay(24, TimeUnit.HOURS) // Set the work to run after 24 hours
        .setInputData(workData)
        .build()

    // Enqueue the work request
    WorkManager.getInstance(context).enqueue(workRequest)
}

// --- Helper Functions from your provided code ---

private fun extractTimeFromText(text: String): String? {
    val regex = Regex("\\b\\d{1,2}:\\d{2}(?:\\s?[AaPp][Mm])?\\b")
    return regex.find(text)?.value
}

private fun parseToHourMinute(text: String): Pair<Int, Int>? {
    val patterns = listOf("H:mm", "HH:mm", "h:mm a", "hh:mm a")
    for (p in patterns) {
        try {
            val sdf = SimpleDateFormat(p, Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(text)
            if (date != null) {
                val cal = Calendar.getInstance().apply { time = date }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                return Pair(hour, minute)
            }
        } catch (_: ParseException) { /* try next */ }
    }
    return null
}

private fun parseHourMinute(text: String): Pair<Int?, Int?> {
    return try {
        val parts = text.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        Pair(h, m)
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
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    Toast.makeText(context, "Opening clock app — please set alarm ($label)", Toast.LENGTH_LONG).show()
                    return
                }
            } catch (_: Exception) { /* ignore and try next */ }
        }
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
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
            return
        }
        val googleMapsIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (googleMapsIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(googleMapsIntent)
            return
        }
        Toast.makeText(context, "No map app found", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error opening map: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

