package com.example.alarmchatapp.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.alarmchatapp.R
import com.example.alarmchatapp.utils.AlarmHelper
import com.example.alarmchatapp.utils.LocationUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val messageList = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf(TextFieldValue("")) }

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
        // --- Top Bar and Logo (No Changes) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(28.dp).clickable { Toast.makeText(context, "Settings", Toast.LENGTH_SHORT).show() })
            Spacer(modifier = Modifier.width(6.dp))
            Text("grox", style = MaterialTheme.typography.bodyMedium)
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(painter = painterResource(id = R.drawable.wow_logo), contentDescription = "WOW Logo", modifier = Modifier.size(140.dp))
        }

        // --- NEW: Vertically Centered Interactive Section ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // Takes up all available vertical space
            verticalArrangement = Arrangement.Center, // Centers its children vertically
            horizontalAlignment = Alignment.CenterHorizontally // Centers its children horizontally
        ) {
            // Styled Prompt Text
            Text(
                text = "What Do You want to be notified at",
                color = Color.White, // Set text color to white
                style = MaterialTheme.typography.headlineSmall, // Make text bigger
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Row for the first three buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    val calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 7); set(Calendar.MINUTE, 0); if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1) }
                    setSimpleAlarm(context, "Wake up", calendar, messageList)
                }) { Text("Wake me up") }

                Button(onClick = {
                    val calendar = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
                    setSimpleAlarm(context, "Quick Reminder", calendar, messageList)
                }) { Text("Remind me") }

                Button(onClick = {
                    val loc = LocationUtils.getLastKnownLocation(context)
                    messageList.add(0, "🔌 Connected tools — ${loc ?: "Location unavailable"}")
                }) { Text("Connect tools") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // "Schedule Alarm" button
            Button(onClick = {
                val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }
                checkAndScheduleExactAlarm(context, "Custom scheduled alarm", calendar, messageList)
            }) { Text("Schedule Alarm") }
        }

        // --- Bottom Section (Message List and Input Bar) ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp) // Constrain height to not take too much space
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
                placeholder = { Text("e.g., Wake me at 6:30 AM on 15-12-2025") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = {
                val rawText = input.text.trim()
                if (rawText.isEmpty()) return@IconButton

                val extractedTime = extractTimeFromText(rawText)
                if (extractedTime == null) {
                    messageList.add(0, "⚠️ No time found. Please specify a time.")
                    return@IconButton
                }
                val parsedTime = parseToHourMinute(extractedTime)
                if (parsedTime == null) {
                    messageList.add(0, "⚠️ Could not understand the time.")
                    return@IconButton
                }
                val (hour, minute) = parsedTime
                val calendar = Calendar.getInstance()
                val extractedDate = Regex("""\b(\d{1,2})-(\d{1,2})-(\d{4})\b""").find(rawText)?.groupValues

                if (extractedDate != null) {
                    val day = extractedDate[1].toInt()
                    val month = extractedDate[2].toInt() - 1
                    val year = extractedDate[3].toInt()
                    calendar.set(year, month, day, hour, minute, 0)
                    checkAndScheduleExactAlarm(context, rawText, calendar, messageList)
                } else {
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    if (calendar.before(Calendar.getInstance())) {
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                    }
                    setSimpleAlarm(context, rawText, calendar, messageList)
                }
                input = TextFieldValue("")
            }) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

// --- Helper functions remain the same ---

private fun setSimpleAlarm(context: Context, label: String, calendar: Calendar, messageList: MutableList<String>) {
    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_MESSAGE, label)
        putExtra(AlarmClock.EXTRA_HOUR, calendar.get(Calendar.HOUR_OF_DAY))
        putExtra(AlarmClock.EXTRA_MINUTES, calendar.get(Calendar.MINUTE))
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
        messageList.add(0, "✅ Opening clock app to set alarm...")
    } else {
        Toast.makeText(context, "No clock app found.", Toast.LENGTH_SHORT).show()
        messageList.add(0, "⚠️ No clock app found.")
    }
}

private fun checkAndScheduleExactAlarm(context: Context, label: String, calendar: Calendar, messageList: MutableList<String>) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        messageList.add(0, "⚠️ Permission needed. Please enable 'Alarms & reminders' for this app.")
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
            it.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(it)
        }
    } else {
        AlarmHelper.scheduleInAppAlarm(context, label, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.timeInMillis)
        val formattedDate = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(calendar.time)
        messageList.add(0, "✅ In-app alarm scheduled for: $formattedDate")
    }
}

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
                return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            }
        } catch (_: ParseException) { /* try next pattern */ }
    }
    return null
}
