package com.example.alarmchatapp.ui

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        // Top bar
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

        // Logo near top center
        val wowLogo: Painter = painterResource(id = R.drawable.wow_logo)
        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), contentAlignment = Alignment.TopCenter) {
            Image(painter = wowLogo, contentDescription = "WOW Logo", modifier = Modifier.size(140.dp))
        }

        // Messages (newest first)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            reverseLayout = true
        ) {
            items(messageList) { msg ->
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(text = msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // Quick options row
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = {
                // default 07:00
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

        // Input
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type: e.g. 'Wake me at 6:30 AM'") },
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
                    // try to set alarm via clock app; otherwise schedule in-app fallback
                    val parsed = parseToHourMinute(extracted)
                    if (parsed != null) {
                        val (hour, minute) = parsed
                        setAlarmCrossDevice(context, rawText, hour, minute)
                        messageList.add(0, "✅ Alarm request sent for $extracted — \"$rawText\"")
                    } else {
                        // parsing failed despite regex match -> message
                        messageList.add(0, "⚠️ Could not parse time \"$extracted\"")
                    }
                }
                if(
                    rawText.startsWith("navigate to", ignoreCase = true) ||
                    rawText.startsWith("open map", ignoreCase = true) ) {
                    val locationQuery = rawText.substringAfter("to").trim()
                    Log.d("ChatScreen", "Location query: $locationQuery")
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

/** Extracts first hh:mm (optionally am/pm) */
private fun extractTimeFromText(text: String): String? {
    val regex = Regex("\\b\\d{1,2}:\\d{2}(?:\\s?[AaPp][Mm])?\\b")
    return regex.find(text)?.value
}

/** Parse "7:30" or "7:30 AM" or "19:05" -> returns Pair(hour,minute) or null */
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

/** helper simple parser for known HH:mm string */
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

/**
 * Cross-device alarm setter:
 * 1) Try ACTION_SET_ALARM (Google API)
 * 2) If not handled, try launching common clock packages
 * 3) If not available, schedule in-app AlarmManager fallback
 */
private fun setAlarmCrossDevice(context: Context, label: String, hour: Int, minute: Int) {
    try {
        // 1) Google Clock API
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

        // 2) Try known clock packages (launch the clock app so user can add alarm)
        val candidates = listOf(
            "com.google.android.deskclock",       // Google Clock
            "com.sec.android.app.clockpackage",   // Samsung Clock
            "com.android.deskclock",              // AOSP / common
            "com.miui.clock"                      // Some Xiaomi
        )
        for (pkg in candidates) {
            try {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    // Open clock app (user must add alarm manually) — we try to prefill by extras if the activity supports them
                    // Some OEMs ignore extras; but launching app helps user quickly set alarm
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    Toast.makeText(context, "Opening clock app — please set alarm ($label)", Toast.LENGTH_LONG).show()
                    return
                }
            } catch (_: Exception) { /* ignore and try next */ }
        }

        // 3) Fallback: schedule in-app alarm (AlarmManager -> AlarmReceiver -> AlarmActivity)
        AlarmHelper.scheduleInAppAlarm(context, label, hour, minute)
        Toast.makeText(context, "No compatible Clock app. Alarm scheduled inside app.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to request alarm: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
private fun openLocationInGoogleMaps(context: Context, locationName: String) {
    try {
        Log.d("ChatScreen", "Opening location: $locationName")
        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(locationName)}")
        Log.d("ChatScreen", "gmmIntentUri: $gmmIntentUri")

        // Try without package restriction first
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
            return
        }

        // If you specifically want Google Maps:
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

